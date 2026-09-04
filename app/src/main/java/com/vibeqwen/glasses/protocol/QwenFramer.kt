package com.vibeqwen.glasses.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 官方千问 APP GCSP (Genie Communication Service Protocol) 帧封装器。
 *
 * 依据 classes4.dex 逆向与实机抓包分析（2026-09-02 确认）：
 *
 * 1. GCSP 版本协商帧（连接后第 0 帧）：
 *    [0..1] LE 长度-2 = 0x0008
 *    [2..3] CID = 0x0000 (管理通道)
 *    [4]    PDU 长度 = 0x05
 *    [5..6] 魔数前导码 = 'G', 'C' (0x47, 0x43)
 *    [7..8] 操作码 = GCSP_OP_VERSION_NEG_REQ (0x0001)
 *    [9]    支持最大版本 = 0x02
 *    总计 10 字节: 08 00 00 00 05 47 43 00 01 02
 *
 * 2. GCSP v2 数据帧结构（AISCommand）：
 *    [0..1] LE 总帧长 = Header(10B) + Payload(NB) + CRC16(2B) - 2
 *    [2..3] CID = 0x0001 (数据/控制通道)
 *    [4..5] 载荷长度相关 = payloadLength + 5
 *    [6]    Flags = 0x00
 *    [7..8] Frame ID / 消息序号
 *    [9]    Msg Type (0x01 = 数据 / 0x03 = 空)
 *    [10..] Payload
 *    [end-2..end-1] CRC16/CCITT (XMODEM, 多项式 0x1021, 初始值 0xFFFF)
 */
object QwenFramer {

    private var seq: Int = 0

    /** CRC-16/CCITT (XMODEM) 算法，反编译自官方 AISCommand.a */
    fun crc16(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0xFFFF
        val end = offset + length
        for (i in offset until end) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            for (j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
            }
            crc = crc and 0xFFFF
        }
        return crc and 0xFFFF
    }

    /** 生成 GCSP 版本协商请求帧 (10 字节，官方抓包严格一致) */
    fun versionNegFrame(maxVersion: Int = 1): ByteArray {
        return byteArrayOf(
            0x08, 0x00, 0x00, 0x00, 0x05, 0x47, 0x43, 0x00, 0x00, 0x01
        )
    }

    /**
     * 生成 GMA 快速鉴权挑战帧 (26 字节，命令 0x10)
     * 手机向眼镜发起重连挑战，携带 16 字节随机数 RandomA
     */
    fun fastAuthChallenge(randomA: ByteArray = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }): ByteArray {
        return ByteArray(26).apply {
            this[0] = 0x18; this[1] = 0x00; this[2] = 0x01; this[3] = 0x00
            this[4] = 0x15; this[5] = 0x00; this[6] = 0x00; this[7] = 0x00; this[8] = 0x00
            this[9] = 0x10
            System.arraycopy(randomA, 0, this, 10, 16)
        }
    }

    /**
     * 生成 GMA 快速鉴权确认帧 (11 字节，命令 0x12)
     * 收到眼镜 0x11 回包后下发确认
     */
    fun fastAuthConfirm(): ByteArray {
        return byteArrayOf(
            0x09, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00, 0x01, 0x00, 0x12, 0x00
        )
    }

    /**
     * 会话空节点初始化帧（12 字节，包号 2，载荷 "{}"）
     */
    fun emptyNodeInitFrame(): ByteArray {
        return byteArrayOf(
            0x0a, 0x00, 0x01, 0x00, 0x07, 0x00, 0x00, 0x02, 0x00, 0x03, 0x7b, 0x7d
        )
    }

    /**
     * 会话 CBOR 系统信息帧（146 字节，官方抓包严格一致）
     */
    fun cborSystemInfoFrame(peerMac: String = "55:55:55:55:55:55"): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x90.toByte(), 0x00, 0x01, 0x00, 0x8d.toByte(), 0x08, 0x00, 0x03, 0x00, 0x01))
        baos.write(0xbf)
        baos.write("haddrType".toByteArray(Charsets.ISO_8859_1))
        baos.write(0x02)
        baos.write("eappId".toByteArray(Charsets.ISO_8859_1))
        baos.write("ocom.alibaba.wowbosgAndroidhpeerAddrq".toByteArray(Charsets.ISO_8859_1))
        baos.write(peerMac.toByteArray(Charsets.ISO_8859_1))
        baos.write("dtime".toByteArray(Charsets.ISO_8859_1))
        val ts = System.currentTimeMillis()
        baos.write(byteArrayOf(
            0x1B,
            ((ts shr 56) and 0xFF).toByte(),
            ((ts shr 48) and 0xFF).toByte(),
            ((ts shr 40) and 0xFF).toByte(),
            ((ts shr 32) and 0xFF).toByte(),
            ((ts shr 24) and 0xFF).toByte(),
            ((ts shr 16) and 0xFF).toByte(),
            ((ts shr 8) and 0xFF).toByte(),
            (ts and 0xFF).toByte()
        ))
        baos.write("jtimeOffset".toByteArray(Charsets.ISO_8859_1))
        baos.write(byteArrayOf(0x1A, 0x01, 0xB7.toByte(), 0x74, 0x00))
        baos.write("jtimeZoneIdmAsia/Shanghaigversion".toByteArray(Charsets.ISO_8859_1))
        baos.write(byteArrayOf(0x01, 0xFF.toByte()))
        return baos.toByteArray()
    }

    /** 重置帧序号 */
    fun resetSeq() {
        seq = 0
    }

    /** 会话 node 初始化载荷（官方 APP 格式） */
    fun buildNodeInit(): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(0xbf)
        baos.write("haddrType".toByteArray(Charsets.ISO_8859_1))
        baos.write(0x00)
        baos.write("eappId".toByteArray(Charsets.ISO_8859_1))
        baos.write("ocom.alibaba.wowbosgAndroidhpeerAddrq".toByteArray(Charsets.ISO_8859_1))
        baos.write("22:c1:37:10:6e:b4".toByteArray(Charsets.ISO_8859_1))
        baos.write("dtime".toByteArray(Charsets.ISO_8859_1))
        baos.write(byteArrayOf(0x1B, 0x00, 0x00, 0x01, 0xA0.toByte(), 0x52, 0xA4.toByte(), 0x81.toByte(), 0x80.toByte()))
        baos.write("jtimeOffset".toByteArray(Charsets.ISO_8859_1))
        baos.write(byteArrayOf(0x1A, 0x01, 0xB7.toByte(), 0x74, 0x00))
        baos.write("jtimeZoneIdmAsia/Shanghaigversion".toByteArray(Charsets.ISO_8859_1))
        baos.write(byteArrayOf(0x01, 0xFF.toByte()))
        return baos.toByteArray()
    }

    /**
     * 封装 GCSP v2 数据帧（带校验和）
     */
    fun wrap(payload: ByteArray, msgType: Int = 1, cid: Int = 1, appendCrc: Boolean = true): ByteArray {
        val headerLen = 10
        val crcLen = if (appendCrc) 2 else 0
        val total = headerLen + payload.size + crcLen
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)

        // 头部字段
        buf.putShort((total - 2).toShort())
        buf.putShort(cid.toShort())
        buf.putShort((payload.size + 5).toShort())
        buf.put(0.toByte())
        buf.putShort(seq.toShort())
        buf.put(msgType.toByte())
        seq++

        // 载荷
        buf.put(payload)

        // CRC-16 校验和 (大端 MSB 前，LSB 后)
        if (appendCrc) {
            val crc = crc16(buf.array(), 0, headerLen + payload.size)
            buf.put(((crc shr 8) and 0xFF).toByte())
            buf.put((crc and 0xFF).toByte())
        }

        return buf.array()
    }

    /** 封装 JSON 字符串为 GCSP 帧 */
    fun wrapJson(json: String, msgType: Int = 1): ByteArray =
        wrap(json.toByteArray(Charsets.UTF_8), msgType = msgType)

    /** node 初始化帧（带 GCSP 封装与 CRC） */
    fun nodeInitFrame(): ByteArray = wrap(buildNodeInit())
}