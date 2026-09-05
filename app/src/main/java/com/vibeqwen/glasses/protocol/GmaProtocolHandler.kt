package com.vibeqwen.glasses.protocol

import com.vibeqwen.glasses.util.LogCollector
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GMA (Genie Mobile Accessory) / GCSP 二进制协议解析与自动应答器。
 *
 * 反编译自官方 APP (com.alibaba.wow classes4.dex)：
 * - GCSP 帧结构 (v1 / v2)
 * - GMA 命令帧：namespace, commandId, msgId, payload
 * - 收到眼镜的 GMA 请求命令后自动生成标准应答帧并回包给眼镜
 */
object GmaProtocolHandler {

    /** 鉴权成功回调 */
    var onAuthSuccess: (() -> Unit)? = null

    /**
     * 尝试解析接收到的原始二进制包。
     * @return 如果是 GMA 二进制请求，返回对应的 ACK 应答包；否则返回 null
     */
    fun handleIncomingBytes(bytes: ByteArray): ByteArray? {
        if (bytes.size < 6) return null

        val hex = bytes.take(32).joinToString("") { "%02X".format(it) }
        LogCollector.log("GMA", "收到原始包 (${bytes.size}B): $hex")

        // 动态判断是否带有 2 字节 LE-CoC SDU 长度：
        // 1) SDU 长度已剥离（典型 Android InputStream.read）：bytes[0..1] == 0x0001 (CID)，cmd 在 bytes[7]，payload 起始于 bytes[8]
        // 2) SDU 长度未剥离：bytes[2..3] == 0x0001 (CID)，cmd 在 bytes[9]，payload 起始于 bytes[10]
        val cmd = when {
            bytes.size >= 8 && (bytes[0].toInt() and 0xFF) == 0x01 && (bytes[1].toInt() and 0xFF) == 0x00 -> bytes[7].toInt() and 0xFF
            bytes.size >= 10 && (bytes[2].toInt() and 0xFF) == 0x01 && (bytes[3].toInt() and 0xFF) == 0x00 -> bytes[9].toInt() and 0xFF
            else -> -1
        }
        val payloadOffset = when {
            bytes.size >= 8 && (bytes[0].toInt() and 0xFF) == 0x01 && (bytes[1].toInt() and 0xFF) == 0x00 -> 8
            bytes.size >= 10 && (bytes[2].toInt() and 0xFF) == 0x01 && (bytes[3].toInt() and 0xFF) == 0x00 -> 10
            else -> 0
        }

        // 0. 收到眼镜 0x15 (HMAC 32B + RandomB 16B) -> 依据官方真机抓包 Packet 327，立即回复 0x10 GMA 快速鉴权帧 (seq=1)
        if (cmd == 0x15 && bytes.size >= payloadOffset + 48) {
            LogCollector.h("★ 收到眼镜 0x15 设备 HMAC 响应 (${bytes.size}B)")
            val deviceHmac = bytes.copyOfRange(payloadOffset, payloadOffset + 32)
            val randomB = bytes.copyOfRange(payloadOffset + 32, payloadOffset + 48)
            LogCollector.h("  设备 HMAC: " + deviceHmac.take(8).joinToString("") { "%02X".format(it) } + "...")
            LogCollector.h("  设备 RandomB: " + randomB.joinToString("") { "%02X".format(it) })

            val challenge10 = QwenFramer.fastAuthChallenge(seq = 1)
            LogCollector.h("← 触发 Phase 2: 下发 0x10 快速鉴权挑战包 (24B, seq=1)")
            return challenge10
        }

        // 0.1 GMA 快速鉴权协议响应 (0x11 设备随机数响应 -> 自动回复 0x12 确认帧 seq=2)
        if (cmd == 0x11 && bytes.size >= payloadOffset + 16) {
            LogCollector.h("★ 收到眼镜 0x11 设备 RandomB2 响应 (26B)")
            val randomB2 = bytes.copyOfRange(payloadOffset, payloadOffset + 16)
            LogCollector.h("  设备 RandomB2: " + randomB2.joinToString("") { "%02X".format(it) })
            val confirmFrame = QwenFramer.fastAuthConfirm(seq = 2)
            LogCollector.h("← 下发 0x12 快速鉴权确认包 (9B, seq=2)")
            return confirmFrame
        }

        // 0.2 GMA 鉴权成功通知 (0x13 0x00)
        if (cmd == 0x13 && bytes.size >= payloadOffset + 1) {
            val status = bytes[payloadOffset].toInt() and 0xFF
            if (status == 0) {
                LogCollector.c("★★★★★ 眼镜上报 0x13 AUTH_SUCCESS (鉴权确凿通过！) ★★★★★")
                onAuthSuccess?.invoke()
            } else {
                LogCollector.e("眼镜上报 0x13 鉴权失败，状态码: $status")
            }
            return null
        }

        // 1. GCSP 版本协商应答 (0x0002) 或 请求 (0x0001)
        if (bytes.size >= 8 && bytes.contains(0x47.toByte()) && bytes.contains(0x43.toByte())) {
            val idx = bytes.indexOf(0x47.toByte())
            if (idx >= 0 && idx + 4 < bytes.size && bytes[idx + 1] == 0x43.toByte()) {
                val opcode = ((bytes[idx + 2].toInt() and 0xFF) shl 8) or (bytes[idx + 3].toInt() and 0xFF)
                val ver = bytes[idx + 4].toInt() and 0xFF
                LogCollector.h("GCSP 协议握手帧: opcode=0x%04X, version=%d".format(opcode, ver))
                // 如果眼镜在请求协商，回复版本 1 应答
                if (opcode == 0x0001) {
                    return byteArrayOf(
                        0x08, 0x00, 0x00, 0x00, 0x05, 0x47, 0x43, 0x00, 0x02, 0x01
                    )
                }
                return null
            }
        }

        // 2. GMA 二进制消息解析 (如 01 00 09 20 00 49 03 0B 00 00 00 00)
        // 结构：[0..1] CID (0x0001), [2..3] CmdId (0x2009), [4] Flag (0x00), [5..6] MsgId (0x0349), [7..] Payload
        try {
            val cid = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
            if (cid == 0x0001 || cid == 0x0041 || cid == 0x004A) {
                val cmdId = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
                val flag = bytes[4].toInt() and 0xFF
                val msgIdLow = bytes[5].toInt() and 0xFF
                val msgIdHigh = if (bytes.size > 6) bytes[6].toInt() and 0xFF else 0
                val msgId = msgIdLow or (msgIdHigh shl 8)
                LogCollector.p("GMA 二进制命令: CID=0x%04X, cmd=0x%04X, msgId=0x%04X, flag=0x%02X".format(cid, cmdId, msgId, flag))

                // 回复官方抓包确认的标准 GMA 应答 (12 字节应用层有效载荷，系统底层自动前置 0x0C 0x00 SDU 长度)
                // 格式：01 00 09 00 00 [MsgId: 2B] 0F 0D 00 00 00
                val ack = byteArrayOf(
                    0x01, 0x00, 0x09, 0x00, 0x00,
                    msgIdLow.toByte(), msgIdHigh.toByte(),
                    0x0F, 0x0D, 0x00, 0x00, 0x00
                )
                LogCollector.h("生成 GMA ACK: " + ack.joinToString("") { "%02X".format(it) })
                return ack
            }
        } catch (e: Exception) {
            LogCollector.e("GMA 解析异常: ${e.message}")
        }

        return null
    }

    /**
     * 构造 GMA 标准应答帧 (ACK)
     */
    fun buildGmaAck(cid: Int, ns: Int, cmdId: Int, msgId: Int, status: Int = 0): ByteArray {
        val payload = byteArrayOf(
            status.toByte(), // 状态码 0 = 成功
            0x00, 0x00, 0x00
        )
        // 构造 GMA 响应载荷
        val gmaPayload = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(cmdId.toShort())
            put(ns.toByte())
            put(msgId.toByte())
            put(0x01.toByte()) // Response Type
            put(0x00.toByte())
            putShort(payload.size.toShort())
            put(payload)
        }.array()

        // 封装为 GCSP v2 数据帧 (带 CRC16)
        val frame = QwenFramer.wrap(gmaPayload, msgType = 1, cid = cid, appendCrc = true)
        LogCollector.h("生成 GMA ACK 应答包: " + frame.joinToString("") { "%02X".format(it) })
        return frame
    }
}