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
        if (buffer.size > 32768) {
            LogCollector.e("GCSP 缓冲区溢出保护触发 (${buffer.size}B)，重置缓冲区")
            buffer.clear()
            return
        }

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

            // 2. 检查标准 GCSP / GMA 命令帧 (前导 0x01，权威长度前缀位于 [1..2])
            if (buffer.size >= 4 && buffer[0] == 0x01.toByte()) {
                val pduLen = ((buffer[1].toInt() and 0x0F) shl 8) or (buffer[2].toInt() and 0xFF)
                val totalFrameLen = 3 + pduLen
                if (totalFrameLen in 4..4096) {
                    if (buffer.size < totalFrameLen) {
                        break // 数据尚未收全，保留缓冲区等待后续分包
                    }
                    val frameBytes = ByteArray(totalFrameLen) { buffer[it] }
                    buffer.subList(0, totalFrameLen).clear()

                    val flag = frameBytes[3].toInt() and 0xFF
                    val isBinary = (flag and 0x0C) == 0

                    if (isBinary) {
                        onGmaCommand(0x0001, frameBytes)
                    } else {
                        // GCSP 控制与业务帧
                        if (frameBytes.size >= 8) {
                            val segment = frameBytes[4].toInt() and 0xFF
                            val msgId = frameBytes[5].toInt() and 0xFF
                            val ns = frameBytes[6].toInt() and 0xFF
                            val cmd = frameBytes[7].toInt() and 0xFF

                            // (A) 官方规范与抓包 Packet 19388/19393：flag 包含 0x20 时必须立即下发 8 字节 ACK
                            if ((flag and 0x20) != 0 || flag == 0x24) {
                                val ack = byteArrayOf(
                                    0x01, 0x00, 0x05, 0x10, 0x00,
                                    msgId.toByte(), ns.toByte(), cmd.toByte()
                                )
                                onGcspControl(ack)
                            }

                            val payloadBytes = frameBytes.copyOfRange(8, frameBytes.size)
                            val jsonStr = String(payloadBytes, Charsets.UTF_8).trim()

                            // (B) 官方抓包严格对齐：
                            // 1. PaySDK / Alipay RPC: 立即回复 reply，若为 BIND 查询则返回 BINDED
                            // 2. ns=0x16 cmd=0x02 (停止确认) 回 Flag 0x14 {"code":0}
                            // 3. ns=0x10/0x16 cmd=0x01 (.ogg/会话同步) 回 Flag 0x14 {"sessionId":$sid}
                            if (jsonStr.contains("PaySDK") || jsonStr.contains("\"action\":")) {
                                val replyResp = QwenFramer.wrapResponse(
                                    "reply",
                                    msgId = msgId,
                                    nameSpace = if (ns != 0) ns else 0x13,
                                    cmdId = if (cmd != 0) cmd else 0x02,
                                    flag = 0x14
                                )
                                LogCollector.r("←回复 PaySDK RPC ACK (reply, msgId=0x%02X)".format(msgId))
                                onGcspControl(replyResp)

                                if (jsonStr.contains("COMMAND_PAY_BIND_STATUS_QUERY") || jsonStr.contains("getBindStatus")) {
                                    val traceId = jsonStr.substringAfter("\"traceId\":\"", "").substringBefore("\"", "")
                                    val bindPayload = """{"data":"{\"code\":\"1000\",\"data\":\"{\\\"bindStatus\\\":\\\"BINDED\\\",\\\"featureStatusList\\\":[{\\\"featureId\\\":\\\"PAYMENT\\\",\\\"status\\\":\\\"STATUS_BIND\\\"},{\\\"featureId\\\":\\\"CITY_SIGHTSEEING\\\",\\\"status\\\":\\\"STATUS_BIND\\\"},{\\\"featureId\\\":\\\"QUICK_MODE_V2\\\",\\\"status\\\":\\\"STATUS_UNBIND\\\"}],\\\"success\\\":true,\\\"verifyMethodStatus\\\":[{\\\"VERIFY_IRIS\\\":\\\"CLOSE\\\"},{\\\"VERIFY_VOICE\\\":\\\"OPEN\\\"}]}\",\"traceId\":\"$traceId\",\"type\":\"PaySDK\"}"""
                                    val bindResp = QwenFramer.wrap(
                                        bindPayload.toByteArray(Charsets.UTF_8),
                                        flag = 0x04,
                                        nameSpace = 0x13,
                                        cmdId = 0x01
                                    )
                                    LogCollector.r("←回复 PaySDK 绑定状态成功 (bindStatus: BINDED)")
                                    onGcspControl(bindResp)
                                }
                            } else if (ns == 0x16 && cmd == 0x02) {
                                val resp = QwenFramer.wrapResponse(
                                    """{"code":0}""",
                                    msgId = msgId,
                                    nameSpace = ns,
                                    cmdId = cmd,
                                    flag = 0x14
                                )
                                LogCollector.r("←响应眼镜会话停止确认 (ns=0x16, cmd=0x02, msgId=0x%02X)".format(msgId))
                                onGcspControl(resp)
                            } else if (ns == 0x10 || ns == 0x16 || jsonStr.contains(".ogg") || jsonStr.contains("sceneContexts") || jsonStr.contains("SynchronizeStatus") || jsonStr.contains("AliGenie.Text") || jsonStr.contains("Recognize")) {
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

                                if (jsonStr.contains("GetSetting")) {
                                    val setResp = com.vibeqwen.glasses.protocol.QwenCommands.getSettingResp(sid)
                                    val setRespBytes = QwenFramer.wrap(setResp.toByteArray(Charsets.UTF_8), flag = 0x24, nameSpace = 0x10, cmdId = 0x02)
                                    LogCollector.r("←响应眼镜 GetSettingResp (配置快捷键与手势)")
                                    onGcspControl(setRespBytes)
                                }
                            }

                            if (jsonStr.startsWith("{") && jsonStr.endsWith("}")) {
                                onJson(jsonStr)
                            }
                        }
                    }
                    continue
                }
            }

            // 3. 容错回退：裸 JSON 文本（非标准或底层驱动剥离了头部）
            val jsonStart = buffer.indexOf('{'.code.toByte())
            if (jsonStart in 0..16) {
                val jsonEnd = findJsonEnd(jsonStart)
                if (jsonEnd > jsonStart) {
                    val jsonBytes = ByteArray(jsonEnd - jsonStart) { buffer[jsonStart + it] }
                    val jsonStr = String(jsonBytes, Charsets.UTF_8).trim()
                    buffer.subList(0, jsonEnd).clear()
                    onJson(jsonStr)
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

                    // 2. 官方抓包 Packet 50539/51458/37385 严格确认：
                    // 当眼镜发送 SynchronizeStatus (nameSpace=0x10)、.ogg/sceneContexts (nameSpace=0x16)、或 AliGenie.Text.Recognize 时，
                    // 手机必须立即回送携带对应 msgId/namespace/cmdId 的 flag=0x14 响应帧，
                    // 否则眼镜判定与手机网络失联、停止推流并播报“手机网络好像有点问题，请检查”
                    val nsByte = if (frame.size >= 8) frame[6] else 0.toByte()
                    val cmdByte = if (frame.size >= 8) frame[7] else 0.toByte()
                    if (jsonStr.contains("PaySDK") || jsonStr.contains("\"action\":")) {
                        val replyResp = QwenFramer.wrapResponse(
                            "reply",
                            msgId = frame[5].toInt() and 0xFF,
                            nameSpace = if (nsByte.toInt() != 0) nsByte.toInt() and 0xFF else 0x13,
                            cmdId = if (cmdByte.toInt() != 0) cmdByte.toInt() and 0xFF else 0x02,
                            flag = 0x14
                        )
                        LogCollector.r("←回复 PaySDK RPC ACK (reply, msgId=0x%02X)".format(frame[5]))
                        onGcspControl(replyResp)

                        if (jsonStr.contains("COMMAND_PAY_BIND_STATUS_QUERY") || jsonStr.contains("getBindStatus")) {
                            val traceId = jsonStr.substringAfter("\"traceId\":\"", "").substringBefore("\"", "")
                            val bindPayload = """{"data":"{\"code\":\"1000\",\"data\":\"{\\\"bindStatus\\\":\\\"BINDED\\\",\\\"featureStatusList\\\":[{\\\"featureId\\\":\\\"PAYMENT\\\",\\\"status\\\":\\\"STATUS_BIND\\\"},{\\\"featureId\\\":\\\"CITY_SIGHTSEEING\\\",\\\"status\\\":\\\"STATUS_BIND\\\"},{\\\"featureId\\\":\\\"QUICK_MODE_V2\\\",\\\"status\\\":\\\"STATUS_UNBIND\\\"}],\\\"success\\\":true,\\\"verifyMethodStatus\\\":[{\\\"VERIFY_IRIS\\\":\\\"CLOSE\\\"},{\\\"VERIFY_VOICE\\\":\\\"OPEN\\\"}]}\",\"traceId\":\"$traceId\",\"type\":\"PaySDK\"}"""
                            val bindResp = QwenFramer.wrap(
                                bindPayload.toByteArray(Charsets.UTF_8),
                                flag = 0x04,
                                nameSpace = 0x13,
                                cmdId = 0x01
                            )
                            LogCollector.r("←回复 PaySDK 绑定状态成功 (bindStatus: BINDED)")
                            onGcspControl(bindResp)
                        }
                    } else if (jsonStart >= 8 && nsByte == 0x16.toByte() && cmdByte == 0x02.toByte()) {
                        val resp = QwenFramer.wrapResponse(
                            """{"code":0}""",
                            msgId = frame[5].toInt() and 0xFF,
                            nameSpace = 0x16,
                            cmdId = 0x02,
                            flag = 0x14
                        )
                        LogCollector.r("←响应眼镜会话停止确认 (ns=0x16, cmd=0x02, msgId=0x%02X)".format(frame[5]))
                        onGcspControl(resp)
                    } else if (jsonStart >= 8 && (nsByte == 0x10.toByte() || nsByte == 0x16.toByte() || jsonStr.contains(".ogg") || jsonStr.contains("sceneContexts") || jsonStr.contains("SynchronizeStatus") || jsonStr.contains("AliGenie.Text") || jsonStr.contains("Recognize"))) {
                        val sid = (System.currentTimeMillis() / 1000).toInt()
                        val resp = QwenFramer.wrapResponse(
                            """{"sessionId":$sid}""",
                            msgId = frame[5].toInt() and 0xFF,
                            nameSpace = nsByte.toInt() and 0xFF,
                            cmdId = cmdByte.toInt() and 0xFF,
                            flag = 0x14
                        )
                        LogCollector.r("←响应眼镜推流/文本会话请求 (ns=0x%02X, msgId=0x%02X, sid=%d)".format(nsByte, frame[5], sid))
                        onGcspControl(resp)

                        if (jsonStr.contains("GetSetting")) {
                            val setResp = com.vibeqwen.glasses.protocol.QwenCommands.getSettingResp(sid)
                            val setRespBytes = QwenFramer.wrap(setResp.toByteArray(Charsets.UTF_8), flag = 0x24, nameSpace = 0x10, cmdId = 0x02)
                            LogCollector.r("←响应眼镜 GetSettingResp (配置快捷键与手势)")
                            onGcspControl(setRespBytes)
                        }
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