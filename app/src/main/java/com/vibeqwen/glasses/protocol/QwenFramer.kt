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

    /**
     * 生成 GCSP 版本协商请求帧 (8 字节)
     * 注意：Android BluetoothSocket (LE-CoC) 在底层 write() 时会自动前置 2 字节 SDU 长度 (0x0008)
     * 因此 Java 应用层写入的有效载荷为 8 字节：
     * 00 00 05 47 43 00 00 01
     */
    fun versionNegFrame(maxVersion: Int = 1): ByteArray {
        return byteArrayOf(
            0x00, 0x00, 0x05, 0x47, 0x43, 0x00, 0x00, (maxVersion and 0xFF).toByte()
        )
    }

    /**
     * 生成 GMA 本地鉴权 Step 1 挑战帧 (24 字节，命令 0x14)
     * 注意：Android BluetoothSocket (LE-CoC) 会自动前置 2 字节 SDU 长度 (0x0018 = 24B)
     * Java 应用层写入载荷 (24B):
     * 01 00 15 00 00 00 00 14 [16B RandomA]
     */
    fun authStep1LocalChallenge(randomA: ByteArray = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }): ByteArray {
        return ByteArray(24).apply {
            this[0] = 0x01; this[1] = 0x00; this[2] = 0x15; this[3] = 0x00
            this[4] = 0x00; this[5] = 0x00; this[6] = 0x00; this[7] = 0x14
            System.arraycopy(randomA, 0, this, 8, 16)
        }
    }

    /**
     * 生成 GMA 快速鉴权挑战帧 (24 字节，命令 0x10)
     * 注意：Android BluetoothSocket (LE-CoC) 会自动前置 2 字节 SDU 长度 (0x0018 = 24B)
     * Java 应用层写入载荷 (24B):
     * 01 00 15 00 00 [seq: 2B] 10 [16B RandomA2]
     */
    fun fastAuthChallenge(seq: Int = 1, randomA: ByteArray = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }): ByteArray {
        return ByteArray(24).apply {
            this[0] = 0x01; this[1] = 0x00; this[2] = 0x15; this[3] = 0x00; this[4] = 0x00
            this[5] = (seq and 0xFF).toByte()
            this[6] = ((seq shr 8) and 0xFF).toByte()
            this[7] = 0x10
            System.arraycopy(randomA, 0, this, 8, 16)
        }
    }

    /**
     * 生成 GMA 快速鉴权确认帧 (9 字节，命令 0x12)
     * 注意：Android BluetoothSocket (LE-CoC) 会自动前置 2 字节 SDU 长度 (0x0009 = 9B)
     * Java 应用层写入载荷 (9B):
     * 01 00 06 00 00 [seq: 2B] 12 00
     */
    fun fastAuthConfirm(seq: Int = 2): ByteArray {
        return byteArrayOf(
            0x01, 0x00, 0x06, 0x00, 0x00,
            (seq and 0xFF).toByte(), ((seq shr 8) and 0xFF).toByte(),
            0x12, 0x00
        )
    }

    /**
     * 会话空节点初始化帧（10 字节，包号 3，载荷 "{}"）
     * 自动前置 SDU 长度 0x000A
     * Java 应用层写入载荷 (10B):
     * 01 00 07 00 00 [seq: 2B] 03 7b 7d
     */
    fun emptyNodeInitFrame(seq: Int = 3): ByteArray {
        return byteArrayOf(
            0x01, 0x00, 0x07, 0x00, 0x00,
            (seq and 0xFF).toByte(), ((seq shr 8) and 0xFF).toByte(),
            0x03, 0x7b, 0x7d
        )
    }

    /**
     * 会话 CBOR 系统信息帧（144 字节，自动前置 SDU 长度 0x0090）
     */
    fun cborSystemInfoFrame(peerMac: String = "de:1b:47:f9:a2:64", seq: Int = 4): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(
            0x01, 0x00, 0x8d.toByte(), 0x08, 0x00,
            (seq and 0xFF).toByte(), ((seq shr 8) and 0xFF).toByte(),
            0x01
        ))
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
     * 封装 GCSP 数据帧
     * 注意：Android BluetoothSocket (LE-CoC) 在底层写出时自动添加 2 字节 SDU 长度
     * 因此应用层帧头由 8 字节构成：
     * cid(2) + len(2) + flag(1) + seq(2) + msgType(1)
     */
    /**
     * 封装 GCSP / AIS 数据帧
     * 官方真机抓包严格确认格式（8 字节规范头）：
     * [0]: 0x01 (Channel 1)
     * [1]: (PDU长度 >> 8) & 0x0F
     * [2]: PDU长度 & 0xFF
     * [3]: flag (默认 0x24: PayloadType=JSON, Type=2, requires ACK)
     * [4]: 0x00 (Segment 0)
     * [5]: seq (1 字节递增序号)
     * [6]: nameSpace (默认 0x0F)
     * [7]: cmdId (默认 0x01)
     */
    fun wrap(
        payload: ByteArray,
        flag: Int = 0x24,
        nameSpace: Int = 0x0F,
        cmdId: Int = 0x01,
        appendCrc: Boolean = false,
        msgType: Int = 1,
        cid: Int = 1
    ): ByteArray {
        val pduLen = payload.size + 5
        val headerLen = 8
        val crcLen = if (appendCrc) 2 else 0
        val total = headerLen + payload.size + crcLen
        val buf = ByteBuffer.allocate(total)

        buf.put(0x01.toByte())
        buf.put(((pduLen shr 8) and 0x0F).toByte())
        buf.put((pduLen and 0xFF).toByte())
        buf.put(flag.toByte())
        buf.put(0x00.toByte())
        buf.put((seq and 0xFF).toByte())
        buf.put((nameSpace and 0xFF).toByte())
        buf.put((cmdId and 0xFF).toByte())
        seq = (seq + 1) and 0xFF

        // 载荷
        buf.put(payload)

        // CRC-16 校验和 (若启用)
        if (appendCrc) {
            val crc = crc16(buf.array(), 0, headerLen + payload.size)
            buf.put(((crc shr 8) and 0xFF).toByte())
            buf.put((crc and 0xFF).toByte())
        }

        return buf.array()
    }

    /** 封装 JSON 字符串为标准 AIS 帧 (官方抓包证实 flag=0x24) */
    fun wrapJson(
        json: String,
        nameSpace: Int = 0x0F,
        cmdId: Int = 0x01
    ): ByteArray = wrap(
        json.toByteArray(Charsets.UTF_8),
        flag = 0x24,
        nameSpace = nameSpace,
        cmdId = cmdId,
        appendCrc = false
    )

    /**
     * 生成硬件录音推流使能帧 (官方抓包 Packet 19367 证实：12 字节)
     * 01 00 09 00 00 [seq:1B] 03 2d 1a 00 00 00
     */
    fun hardwareRecordTrigger(): ByteArray {
        return ByteArray(12).apply {
            this[0] = 0x01
            this[1] = 0x00
            this[2] = 0x09
            this[3] = 0x00
            this[4] = 0x00
            this[5] = (seq and 0xFF).toByte()
            this[6] = 0x03 // NameSpace = 3 (Hardware)
            this[7] = 0x2d // CommandId = 0x2d
            this[8] = 0x1a // 26
            this[9] = 0x00
            this[10] = 0x00
            this[11] = 0x00
            seq = (seq + 1) and 0xFF
        }
    }

    /**
     * 生成 GMA ACK 响应帧 (官方抓包 Packet 19378 证实：12 字节)
     * 01 00 09 00 00 [msgId:1B] 03 0f 0d 00 00 00
     */
    fun makeGmaAck(msgId: Int): ByteArray {
        return ByteArray(12).apply {
            this[0] = 0x01
            this[1] = 0x00
            this[2] = 0x09
            this[3] = 0x00
            this[4] = 0x00
            this[5] = (msgId and 0xFF).toByte()
            this[6] = 0x03
            this[7] = 0x0f
            this[8] = 0x0d
            this[9] = 0x00
            this[10] = 0x00
            this[11] = 0x00
        }
    }

    /** node 初始化帧（带 GCSP 封装与 CRC） */
    fun nodeInitFrame(): ByteArray = wrap(buildNodeInit())
}