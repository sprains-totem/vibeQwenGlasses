package com.vibeqwen.glasses.protocol

import com.vibeqwen.glasses.util.LogCollector
import java.util.ArrayList

/**
 * GCSP / GMA 流式帧组装器（处理蓝牙 L2CAP 分包、粘包与重组）。
 *
 * 帧类型解析：
 * 1. GCSP 管理帧 (CID=0, 如版本协商 0x0001 / 0x0002 / Ping 0x00)
 * 2. GMA 控制帧 (CID=1, 包含 JSON 文本 或 二进制指令)
 * 3. 音频数据帧 (带魔数 87 EF 12 03 07 01 86 08)
 */
class GcspFrameReassembler(
    private val onJson: (String) -> Unit,
    private val onGmaCommand: (cid: Int, bytes: ByteArray) -> Unit,
    private val onAudioFrame: (ByteArray) -> Unit,
    private val onGcspControl: (ByteArray) -> Unit,
) {
    private val buffer = ArrayList<Byte>()

    @Synchronized
    fun feed(data: ByteArray) {
        for (b in data) buffer.add(b)
        drain()
    }

    private fun drain() {
        while (buffer.isNotEmpty()) {
            // 1. 检查音频帧魔数头 (BLE 395B 或 Classic 398B)
            val audioLen = getAudioFrameLength(0)
            if (audioLen > 0) {
                if (buffer.size < audioLen) {
                    break // 数据分包未收全，等待下一包到达
                }
                val audioBytes = ByteArray(audioLen) { buffer[it] }
                buffer.subList(0, audioLen).clear()
                onAudioFrame(audioBytes)
                continue
            }

            // 2. 检查 JSON 数据帧（无论底层是否剥离 SDU 长度，亦或跨包分片）：
            val jsonStart = buffer.indexOf('{'.code.toByte())
            if (jsonStart >= 0) {
                val jsonEnd = findJsonEnd(jsonStart)
                if (jsonEnd > jsonStart) {
                    // 完整的 JSON 帧已收齐
                    val jsonBytes = ByteArray(jsonEnd - jsonStart) { buffer[jsonStart + it] }
                    val jsonStr = String(jsonBytes, Charsets.UTF_8).trim()

                    // 依据官方抓包严格数学规律，在 '{' 前的 5 字节固定为：
                    // [jsonStart - 5]: flag
                    // [jsonStart - 4]: segment
                    // [jsonStart - 3]: msgId
                    // [jsonStart - 2]: nameSpace
                    // [jsonStart - 1]: cmdId
                    if (jsonStart >= 5) {
                        val flag = buffer[jsonStart - 5].toInt() and 0xFF
                        val msgId = buffer[jsonStart - 3].toInt() and 0xFF
                        val ns = buffer[jsonStart - 2].toInt() and 0xFF
                        val cmd = buffer[jsonStart - 1].toInt() and 0xFF

                        // (A) 官方抓包 Packet 19388/19393 确认的 8 字节 ACK：
                        if (flag == 0x24) {
                            val ack = byteArrayOf(
                                0x01, 0x00, 0x05, 0x10, 0x00,
                                msgId.toByte(), ns.toByte(), cmd.toByte()
                            )
                            onGcspControl(ack)
                        }

                        // (B) 官方抓包 Packet 47855/47857/47859/47861 确认的 sessionId 响应帧：
                        if (ns == 0x10 || ns == 0x16 || jsonStr.contains(".ogg") || jsonStr.contains("sceneContexts") || jsonStr.contains("SynchronizeStatus")) {
                            val sid = (System.currentTimeMillis() / 1000).toInt()
                            val resp = QwenFramer.wrapResponse(
                                """{"sessionId":$sid}""",
                                msgId = msgId,
                                nameSpace = ns,
                                cmdId = cmd,
                                flag = 0x14
                            )
                            LogCollector.r("←响应眼镜会话请求 (ns=0x%02X, msgId=0x%02X, sid=%d)".format(ns, msgId, sid))
                            onGcspControl(resp)
                        }
                    }

                    buffer.subList(0, jsonEnd).clear()
                    onJson(jsonStr)
                    continue
                } else {
                    // JSON 未完整闭合，等待下一包到达（保留缓冲区数据）
                    break
                }
            }

            // 3. 检查纯二进制 GMA 命令 (如 0x15, 0x11, 0x13, 0x2009 等，以 0x01 开头且前 3 字节声明长度)
            if (buffer.size >= 3 && buffer[0] == 0x01.toByte()) {
                val pduLen = ((buffer[1].toInt() and 0x0F) shl 8) or (buffer[2].toInt() and 0xFF)
                val totalFrameLen = 3 + pduLen
                if (totalFrameLen in 4..4096) {
                    if (buffer.size < totalFrameLen) {
                        break // 数据尚未收全，等待后续分包到达
                    }
                    val frameBytes = ByteArray(totalFrameLen) { buffer[it] }
                    buffer.subList(0, totalFrameLen).clear()
                    onGmaCommand(0x0001, frameBytes)
                    continue
                }
            }

            // 4. 无法识别的前导无用字节，滑窗剔除 1 字节
            buffer.removeAt(0)
        }
    }

    private fun getAudioFrameLength(offset: Int): Int {
        if (buffer.size >= offset + 6) {
            val magicBle = QwenConstants.AUDIO_MAGIC_BLE
            var okBle = true
            for (i in 0 until 6) {
                if (buffer[offset + i] != magicBle[i]) {
                    okBle = false
                    break
                }
            }
            if (okBle) return QwenConstants.AUDIO_FRAME_SIZE_BLE
        }
        if (buffer.size >= offset + 8) {
            val magicClassic = QwenConstants.AUDIO_MAGIC_CLASSIC
            var okClassic = true
            for (i in 0 until 8) {
                if (buffer[offset + i] != magicClassic[i]) {
                    okClassic = false
                    break
                }
            }
            if (okClassic) return QwenConstants.AUDIO_FRAME_SIZE_CLASSIC
        }
        return 0
    }

    private fun findJsonEnd(from: Int = 0): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in from until buffer.size) {
            val c = buffer[i].toInt().toChar()
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i + 1
                    }
                }
            }
        }
        return -1
    }

    private fun dispatchFrame(cid: Int, frame: ByteArray) {
        val hex = frame.take(24).joinToString("") { "%02X".format(it) }
        LogCollector.log("GCSP", "解析完整帧 CID=0x%04X, 长度=%dB: %s".format(cid, frame.size, hex))

        when (cid) {
            0x0000 -> {
                // GCSP 管理 / 控制帧 (如版本协商响应 0x0002)
                onGcspControl(frame)
            }
            0x0001, 0x0041, 0x004A -> {
                // 控制/数据通道：检查载荷是否为 JSON
                val jsonStart = frame.indexOf('{'.code.toByte())
                val jsonEnd = frame.lastIndexOf('}'.code.toByte())
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    // 1. 官方抓包 Packet 19388/19393 严格确认格式（8 字节 ACK）：
                    // 01 00 05 10 00 [msgId:1B] [nameSpace:1B] [cmdId:1B]
                    if (jsonStart >= 8 && frame[3] == 0x24.toByte()) {
                        val ack = byteArrayOf(
                            0x01, 0x00, 0x05, 0x10, 0x00,
                            frame[5], frame[6], frame[7]
                        )
                        onGcspControl(ack)
                    }
                    val jsonBytes = frame.copyOfRange(jsonStart, jsonEnd + 1)
                    val jsonStr = String(jsonBytes, Charsets.UTF_8).trim()

                    // 2. 官方抓包 Packet 47855/47857/47859/47861 严格确认：
                    // 当眼镜发送 SynchronizeStatus (nameSpace=0x10) 或 .ogg/sceneContexts (nameSpace=0x16) 会话请求时，
                    // 手机必须立即回送携带对应 msgId/namespace/cmdId 的 flag=0x14 响应帧，
                    // 否则眼镜判定与手机网络失联、停止推流并播报“手机网络可能存在问题”
                    if (jsonStart >= 8 && (frame[6] == 0x10.toByte() || frame[6] == 0x16.toByte() || jsonStr.contains(".ogg") || jsonStr.contains("sceneContexts") || jsonStr.contains("SynchronizeStatus"))) {
                        val sid = (System.currentTimeMillis() / 1000).toInt()
                        val resp = QwenFramer.wrapResponse(
                            """{"sessionId":$sid}""",
                            msgId = frame[5].toInt() and 0xFF,
                            nameSpace = frame[6].toInt() and 0xFF,
                            cmdId = frame[7].toInt() and 0xFF,
                            flag = 0x14
                        )
                        LogCollector.r("←响应眼镜推流会话请求 (ns=0x%02X, msgId=0x%02X, sid=%d)".format(frame[6], frame[5], sid))
                        onGcspControl(resp)
                    }

                    onJson(jsonStr)
                } else {
                    // 二进制 GMA 命令 (如 0x15, 0x11, 0x13, 0x2009 等)
                    onGmaCommand(cid, frame)
                }
            }
            else -> {
                // 其他通道数据 (如音频等)
                onAudioFrame(frame)
            }
        }
    }

    private fun hasCrc(frame: ByteArray): Boolean {
        if (frame.size < 12) return false
        val computed = QwenFramer.crc16(frame, 0, frame.size - 2)
        val expected = ((frame[frame.size - 2].toInt() and 0xFF) shl 8) or (frame[frame.size - 1].toInt() and 0xFF)
        return computed == expected
    }
}