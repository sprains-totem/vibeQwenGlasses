package com.vibeqwen.glasses.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.vibeqwen.glasses.bluetooth.ClassicBtTransport
import com.vibeqwen.glasses.protocol.QwenCommands
import com.vibeqwen.glasses.protocol.QwenConstants
import com.vibeqwen.glasses.protocol.QwenFramer
import com.vibeqwen.glasses.service.ConnectionState
import com.vibeqwen.glasses.service.GlassesBus
import com.vibeqwen.glasses.service.GlassesConnectionService
import com.vibeqwen.glasses.util.LogCollector
import org.json.JSONObject
import java.io.File

/**
 * 强大的 ADB 调试 ContentProvider：
 * 暴露标准 CLI 接口供 adb shell 直接同步调用，零门槛进行毫秒级动态报文调试。
 *
 * 调用方式：
 *   adb shell content call --uri content://com.vibeqwen.glasses.cli --method <command> [--arg <arg>]
 * 或使用快捷脚本：
 *   adb shell /data/local/tmp/qwen <command> [arg]
 */
class DebugProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val result = try {
            handleCommand(method.trim(), arg?.trim())
        } catch (e: Exception) {
            "ERROR: ${e.message}\n${e.stackTraceToString()}"
        }
        return Bundle().apply {
            putString("result", result)
        }
    }

    private fun handleCommand(cmd: String, arg: String?): String {
        val service = GlassesConnectionService.instance
        return when (cmd.lowercase()) {
            "help" -> helpText()
            "status" -> getStatus(service)
            "connect" -> {
                val ctx = context ?: return "Error: context is null"
                val mac = if (!arg.isNullOrBlank()) {
                    arg
                } else {
                    com.vibeqwen.glasses.bluetooth.DeviceScanner.findGlasses(ctx)?.mac ?: ""
                }
                if (mac.isBlank()) {
                    return "错误: 请指定 MAC 地址 (例如: qwen connect AA:BB:CC:DD:EE:FF)，或先在蓝牙设置中配对眼镜"
                }
                GlassesConnectionService.connect(ctx, mac)
                "已向 Service 发送连接指令 -> $mac"
            }
            "disconnect" -> {
                val ctx = context ?: return "Error: context is null"
                GlassesConnectionService.disconnect(ctx)
                "已向 Service 发送断开指令"
            }
            "rfcomm_connect" -> {
                val channel = arg?.toIntOrNull() ?: QwenConstants.RFCOMM_AUDIO_CHANNEL
                val t = service?.transport() ?: return "Error: Transport 未连接，请先执行 connect"
                val ok = t.openAudioChannel(object : ClassicBtTransport.Listener {
                    override fun onControlData(bytes: ByteArray) {}
                    override fun onAudioData(bytes: ByteArray) {
                        LogCollector.log("RFCOMM_CLI", "收到音频推流: ${bytes.size}B")
                    }
                    override fun onConnected() {}
                    override fun onError(message: String) {
                        LogCollector.e("RFCOMM_CLI 错误: $message")
                    }
                    override fun onDisconnected() {}
                })
                if (ok) "★ RFCOMM Channel $channel 连接成功！" else "RFCOMM Channel $channel 连接失败"
            }
            "record_start" -> {
                val ctx = context ?: return "Error: context is null"
                GlassesConnectionService.startRecord(ctx)
                "已触发开始录音 (下发 3 条 AudioRecording 指令并开启 PCM 采集)"
            }
            "record_stop" -> {
                val ctx = context ?: return "Error: context is null"
                GlassesConnectionService.stopRecord(ctx)
                "已触发停止录音并封口保存 WAV"
            }
            "send_hex" -> {
                if (arg.isNullOrBlank()) return "Usage: send_hex <hex_string>"
                val bytes = hexToBytes(arg)
                val t = service?.transport() ?: return "Error: Transport 未初始化"
                t.write(bytes)
                "已下发原始 HEX (${bytes.size}B): $arg"
            }
            "send_json" -> {
                if (arg.isNullOrBlank()) return "Usage: send_json <json_string>"
                val frame = QwenFramer.wrapJson(arg)
                val t = service?.transport() ?: return "Error: Transport 未初始化"
                t.write(frame)
                "已下发 GCSP v2 封装 JSON (${frame.size}B): $arg"
            }
            "attach" -> {
                val t = service?.transport() ?: return "Error: Transport 未初始化"
                t.write(QwenFramer.wrapJson(QwenCommands.attachSuccess()))
                "已下发 attach_success 通知眼镜激活会话"
            }
            "logs" -> {
                val lines = arg?.toIntOrNull() ?: 35
                val ctx = context ?: return "Error: context is null"
                val logFile = File(ctx.getExternalFilesDir(null), "logs/latest.log")
                if (!logFile.exists()) return "日志文件不存在: ${logFile.absolutePath}"
                val all = logFile.readLines()
                all.takeLast(lines).joinToString("\n")
            }
            else -> "未知命令: '$cmd'。输入 'help' 查看支持的命令列表。"
        }
    }

    private fun getStatus(service: GlassesConnectionService?): String {
        val s = GlassesBus.uiState.value
        val t = service?.transport()
        val json = JSONObject().apply {
            put("service_alive", service != null)
            put("connection_state", s.connection.name)
            put("device_mac", s.deviceMac ?: "null")
            put("device_name", s.deviceName ?: "null")
            put("recording", s.recording)
            put("recording_seconds", s.recordingSeconds)
            put("frames_captured", s.frames)
            put("last_db", s.db)
            put("last_error", s.lastError ?: "null")
            put("control_socket_connected", t?.isConnected == true)
        }
        return json.toString(2)
    }

    private fun helpText(): String = """
=== vibeQwenGlasses ADB CLI 命令帮助 ===
调用方式:
  qwen <command> [argument]
或:
  content call --uri content://com.vibeqwen.glasses.cli --method <command> [--arg <argument>]

支持命令:
  help                     显示本帮助信息
  status                   查看当前 App / 服务 / 蓝牙通道 / 录音完整状态 (JSON)
  connect [mac]            发起连接指定眼镜 (可选指定 MAC 地址)
  disconnect               断开所有连接
  attach                   向眼镜发送 attach_success 激活挂载
  rfcomm_connect [channel] 直连经典蓝牙音频通道 (默认 Channel 16)
  record_start             触发录音 (下发官方 3 条录音握手帧并保存 WAV)
  record_stop              停止录音并封口持久化
  send_hex <hex>           向控制通道发送裸 HEX 字节
  send_json <json>         使用 GCSP v2 (带 CRC16) 封装并发送业务 JSON
  logs [lines]             读取最新 N 行运行日志 (默认 35 行)
========================================
""".trimIndent()

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val byteStr = clean.substring(i * 2, i * 2 + 2)
            result[i] = byteStr.toInt(16).toByte()
        }
        return result
    }

    // ContentProvider 基础空实现
    override fun query(uri: Uri, p: Array<String>?, s: String?, sa: Array<String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, sa: Array<String>?): Int = 0
}