package com.vibeqwen.glasses.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.vibeqwen.glasses.protocol.QwenConstants
import java.io.IOException
import java.util.UUID

/**
 * 经典蓝牙 RFCOMM 传输层。
 *
 * 说明：Android 客户端无法直接指定 L2CAP CID（0x0041/0x004A/0x0048 是抓包视角的 CID），
 * 只能按 RFCOMM 服务 UUID 打开通道。本类提供：
 * - 控制通道：createRfcommSocketToServiceRecord(uuid)，按候选列表依次尝试
 * - 音频通道（可选第二通道，openAudioChannel）：HFP/HSP UUID 候选
 * - 读取循环：持续读取并回调原始字节（是否区分 JSON/音频帧交给上层解复用）
 *
 * 注意：connect() 内含阻塞 IO，必须在后台线程调用（服务内协程/线程完成）。
 */
class ClassicBtTransport(
    private val device: BluetoothDevice,
    private val controlCandidates: List<UUID> = QwenConstants.DEFAULT_CONTROL_UUIDS,
    private val audioCandidates: List<UUID> = QwenConstants.DEFAULT_AUDIO_UUIDS,
) {
    interface Listener {
        /** 控制通道原始字节（JSON 为主，间或夹杂非 JSON 残余） */
        fun onControlData(bytes: ByteArray)

        /** 音频通道原始字节（HFP AT 协商 + 398B 音频帧混合流） */
        fun onAudioData(bytes: ByteArray)

        /** 控制通道建立成功 */
        fun onConnected()

        /** 任意通道错误 */
        fun onError(message: String)

        /** 连接断开（网络层触发） */
        fun onDisconnected()
    }

    private val tag = "ClassicBtTransport"

    @Volatile
    private var controlSocket: BluetoothSocket? = null

    @Volatile
    private var audioSocket: BluetoothSocket? = null

    @Volatile
    private var cancelled = false

    @Volatile
    private var listener: Listener? = null

    private var controlThread: Thread? = null
    private var audioThread: Thread? = null

    /** 控制通道是否已连接 */
    val isConnected: Boolean
        get() = controlSocket?.isConnected == true

    /** 建立控制通道（阻塞；后台线程调用） */
    fun connect(listener: Listener): Boolean {
        this.listener = listener
        cancelled = false
        // 官方 APP 用 L2CAP PSM=130（逆向确认），优先尝试；失败再走 RFCOMM 候选
        val sock = openL2capOrControl()
        if (sock == null) return false
        controlSocket = sock
        startReadLoop(sock, isAudio = false)
        listener.onConnected()
        return true
    }

    /** L2CAP(PSM=130) 优先，失败回退 RFCOMM 候选 */
    private fun openL2capOrControl(): BluetoothSocket? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                // 优先使用官方 APP 使用的 Insecure L2CAP 通道 (isSecured = false)
                val s = device.createInsecureL2capChannel(QwenConstants.L2CAP_PSM)
                s.connect()
                Log.i(tag, "[control] Insecure L2CAP PSM=${QwenConstants.L2CAP_PSM} 连接成功")
                com.vibeqwen.glasses.util.LogCollector.c("Insecure L2CAP PSM=${QwenConstants.L2CAP_PSM} 连接成功")
                return s
            } catch (e: Exception) {
                Log.w(tag, "[control] Insecure L2CAP 失败: ${e.message}，尝试 Secure L2CAP")
                try {
                    val s = device.createL2capChannel(QwenConstants.L2CAP_PSM)
                    s.connect()
                    Log.i(tag, "[control] Secure L2CAP PSM=${QwenConstants.L2CAP_PSM} 连接成功")
                    com.vibeqwen.glasses.util.LogCollector.c("Secure L2CAP PSM=${QwenConstants.L2CAP_PSM} 连接成功")
                    return s
                } catch (e2: Exception) {
                    Log.w(tag, "[control] L2CAP PSM=${QwenConstants.L2CAP_PSM} 失败: ${e2.message}，回退 RFCOMM")
                    com.vibeqwen.glasses.util.LogCollector.e("L2CAP 连接失败: ${e2.message}")
                }
            }
        }
        return connectWithCandidates(controlCandidates, "control")
    }

    /** 建立音频第二通道（官方抓包证实走经典蓝牙 RFCOMM Channel 16） */
    fun openAudioChannel(listener: Listener): Boolean {
        this.listener = listener
        // 1. 优先直连官方抓包验证的经典蓝牙 RFCOMM Channel 16
        val sock = tryConnectRfcommChannel(QwenConstants.RFCOMM_AUDIO_CHANNEL)
            ?: connectWithCandidates(audioCandidates, "audio")
        if (sock == null) return false
        audioSocket = sock
        startReadLoop(sock, isAudio = true)
        return true
    }

    private fun tryConnectRfcommChannel(channel: Int): BluetoothSocket? {
        for (methodName in listOf("createInsecureRfcommSocket", "createRfcommSocket")) {
            if (cancelled) return null
            try {
                val method = device.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
                val s = method.invoke(device, channel) as BluetoothSocket
                s.connect()
                Log.i(tag, "[audio] $methodName($channel) 连接成功！")
                com.vibeqwen.glasses.util.LogCollector.log("CONN", "★ 经典蓝牙私有音频推流通道 (RFCOMM $channel) 建立成功！")
                return s
            } catch (e: Exception) {
                Log.w(tag, "[audio] $methodName($channel) 尝试失败: ${e.message}")
            }
        }
        return null
    }

    private fun connectWithCandidates(candidates: List<UUID>, label: String): BluetoothSocket? {
        var lastError: String? = null
        for (uuid in candidates) {
            if (cancelled) return null
            val sock = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: Exception) {
                lastError = "createRfcomm($uuid) 失败: ${e.message}"
                continue
            }
            try {
                sock.connect()
                Log.i(tag, "[$label] 连接成功 uuid=$uuid")
                return sock
            } catch (e: IOException) {
                lastError = "connect($uuid) 失败: ${e.message}"
                try { sock.close() } catch (_: IOException) { }
            } catch (e: Exception) {
                lastError = "connect($uuid) 异常: ${e.message}"
                try { sock.close() } catch (_: IOException) { }
            }
        }
        // 只有控制通道失败才上报致命错误；音频第二通道是可选通道，失败不影响主会话
        if (label == "control") {
            listener?.onError("[$label] $lastError")
        } else {
            Log.w(tag, "[$label] 可选音频通道建立跳过/失败: $lastError")
        }
        return null
    }

    /** 写控制指令（线程安全即可，调用方负责串行化） */
    fun write(bytes: ByteArray) {
        val sock = controlSocket
        if (sock == null || !sock.isConnected) return
        try {
            sock.outputStream.write(bytes)
            sock.outputStream.flush()
        } catch (e: IOException) {
            listener?.onError("写失败: ${e.message}")
            notifyDisconnected()
        }
    }

    /** 主动断开并清理资源 */
    fun disconnect() {
        cancelled = true
        tryClose(controlSocket)
        tryClose(audioSocket)
        controlSocket = null
        audioSocket = null
    }

    private val RFCOMM_CREDIT_ACK = byteArrayOf(
        0x85.toByte(), 0xFF.toByte(), 0x01.toByte(), 0x02.toByte(), 0x6B.toByte()
    )

    private fun startReadLoop(sock: BluetoothSocket, isAudio: Boolean) {
        val label = if (isAudio) "audio" else "control"
        val thread = Thread({
            val input = try { sock.inputStream } catch (e: IOException) { return@Thread }
            val buf = ByteArray(4096)
            var frameCount = 0
            try {
                while (!cancelled && sock.isConnected) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val chunk = buf.copyOf(n)
                    com.vibeqwen.glasses.util.LogCollector.log("IO", "[$label] 收到 ${n}B: " + chunk.take(24).joinToString("") { "%02X".format(it) })
                    if (isAudio) {
                        listener?.onAudioData(chunk)
                        frameCount++
                        if (frameCount % 2 == 0) {
                            try {
                                sock.outputStream.write(RFCOMM_CREDIT_ACK)
                                sock.outputStream.flush()
                            } catch (_: Exception) {}
                        }
                    } else {
                        listener?.onControlData(chunk)
                    }
                }
            } catch (e: IOException) {
                // 正常断开 / 远端关闭
            } catch (e: Exception) {
                if (!cancelled) listener?.onError("读取异常: ${e.message}")
            } finally {
                if (!cancelled) {
                    notifyDisconnected()
                }
            }
        }, if (isAudio) "vqg-audio-reader" else "vqg-control-reader").apply {
            isDaemon = true
            start()
        }
        if (isAudio) audioThread = thread else controlThread = thread
    }

    private fun notifyDisconnected() {
        if (cancelled) return
        cancelled = true
        listener?.onDisconnected()
    }

    private fun tryClose(sock: BluetoothSocket?) {
        if (sock == null) return
        try { sock.close() } catch (_: IOException) { }
    }
}