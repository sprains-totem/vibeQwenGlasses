package com.vibeqwen.glasses.protocol

import java.util.UUID

/**
 * 千问 G1 眼镜协议关键常量。
 *
 * 依据 docs/PROTOCOL.md（逆向实测）：
 * - 控制通道 L2CAP CID 0x0041（眼镜→手机 JSON）/ 0x004A（手机→眼镜 JSON）
 * - 音频通道 CID 是【动态】的（实测 0x0047 / 0x0048 因连接而异），
 *   因此实现必须按魔数头 `87 EF 12 03 07 01 86 08` 全局匹配，而不是固定 CID。
 * - 音频帧固定 398 字节：[0..7] 魔数 / [8] 序号 / [9..12] 填充 / [13..396] PCM / [397] 填充
 */
object QwenConstants {

    // ── 控制通道 CID（信息性；Android RFCOMM 层不可见，保留以供诊断） ──
    /** 眼镜 → 手机：事件 / 状态 / 心跳 JSON */
    const val CID_CONTROL_IN = 0x0041
    /** 手机 → 眼镜：指令 / 配置 / 应答 JSON */
    const val CID_CONTROL_OUT = 0x004A

    // ── 音频帧 ──
    /** BLE L2CAP 帧头魔数 (395B 帧长，真实抓包实测确认) */
    val AUDIO_MAGIC_BLE = byteArrayOf(
        0x89.toByte(), 0x01.toByte(), 0x07.toByte(), 0x01.toByte(),
        0x86.toByte(), 0x08.toByte()
    )
    /** 经典蓝牙/兼容帧头魔数 (398B 帧长) */
    val AUDIO_MAGIC_CLASSIC = byteArrayOf(
        0x87.toByte(), 0xEF.toByte(), 0x12.toByte(), 0x03.toByte(),
        0x07.toByte(), 0x01.toByte(), 0x86.toByte(), 0x08.toByte()
    )
    /** 默认静态魔数（保持向后兼容） */
    val AUDIO_MAGIC = AUDIO_MAGIC_CLASSIC

    /** BLE 单帧总长 (11B 头部 + 384B PCM) */
    const val AUDIO_FRAME_SIZE_BLE = 395
    const val AUDIO_HEADER_SIZE_BLE = 11

    /** 经典蓝牙单帧总长 (13B 头部 + 384B PCM + 1B 尾部) */
    const val AUDIO_FRAME_SIZE_CLASSIC = 398
    const val AUDIO_HEADER_SIZE_CLASSIC = 13

    /** 单帧总长（默认向后兼容） */
    const val AUDIO_FRAME_SIZE = AUDIO_FRAME_SIZE_CLASSIC
    /** 帧头长度（魔数8 + 序号1 + 填充4） */
    const val AUDIO_HEADER_SIZE = AUDIO_HEADER_SIZE_CLASSIC
    /** 帧尾填充字节数 */
    const val AUDIO_TAIL_SIZE = 1
    /** 有效 PCM 字节数 / 帧 */
    const val AUDIO_PCM_SIZE = 384

    // ── PCM 参数 ──
    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    // ── RFCOMM / SPP 服务 UUID 候选 ──
    /** 官方抓包实测确认：高清音频推流通道定死在经典蓝牙 RFCOMM Channel 16 (DLCI 33) */
    const val RFCOMM_AUDIO_CHANNEL = 16

    // 官方千问 APP 设备绑定信息提供真实 128-bit 绑定 UUID（2026-08-30 确认）：
    //   D5A74C04-894A-4E70-C2AE-0BDC687904FE
    // 后备：BES2600 私有扩展 0x03FD（数据）/0x03F0（控制）/ 标准 SPP
    // ★ 关键：官方 APP 实际用 **L2CAP PSM=130** 连接（逆向确认，BleL2capClient），
    //    RFCOMM 仅是回退路径。
    /** L2CAP PSM（官方 APP：connectExclusive: ... psm=130） */
    const val L2CAP_PSM = 130
    /** 官方绑定 UUID（首试） */
    val UUID_OFFICIAL_BIND = UUID.fromString("D5A74C04-894A-4E70-C2AE-0BDC687904FE")
    /** BES 私有高速数据通道（主要录音/数据） */
    val UUID_BES_DATA_03FD = UUID.fromString("000003fd-0000-1000-8000-00805f9b34fb")
    /** BES 私有控制通道 */
    val UUID_BES_CTRL_03F0 = UUID.fromString("000003f0-0000-1000-8000-00805f9b34fb")
    /** SPP 串口服务（备用） */
    val UUID_SPP_1101 = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    /** HSP 免提 */
    val UUID_HSP_1108 = UUID.fromString("00001108-0000-1000-8000-00805f9b34fb")
    /** HFAG 免提音频网关 */
    val UUID_HFAG_111E = UUID.fromString("0000111e-0000-1000-8000-00805f9b34fb")

    /** 厂商私有 UUID 候选（BES2800 / AliGenie 系列） */
    // 注意：Nordic UART (6e400001-*) 是 BLE GATT 服务，不是经典 RFCOMM，
    // 不可用于 createRfcommSocketToServiceRecord —— 已移除。
    val VENDOR_UUID_CANDIDATES = listOf(
        UUID_BES_DATA_03FD,
        UUID_BES_CTRL_03F0,
    )

    /** 控制通道 UUID 尝试顺序（官方绑定 UUID 优先） */
    val DEFAULT_CONTROL_UUIDS = listOf(UUID_OFFICIAL_BIND, UUID_BES_CTRL_03F0, UUID_BES_DATA_03FD, UUID_SPP_1101) + VENDOR_UUID_CANDIDATES
    /** 音频通道 UUID 尝试顺序（HFP 通道在抓包中承载 AT 协商 + 音频帧） */
    val DEFAULT_AUDIO_UUIDS = listOf(UUID_OFFICIAL_BIND, UUID_BES_DATA_03FD, UUID_HFAG_111E, UUID_HSP_1108, UUID_SPP_1101)

    // ── 设备身份常量（真机抓包与 shared_prefs 提取） ──
    const val DEVICE_ODM = "AILABS_SG02_QW"
    const val DEVICE_MODEL = "AILABS_SG02_QW"
    const val DEVICE_BRAND = "Quark_glasses"
    const val DEVICE_TYPE = "bes2800"
    /** Product ID (8665 = 0x21D9) */
    const val PRODUCT_ID = 8665
    /** GMA 设备 UUID (D5A74C04894A4E70C2AE0BDC687904FE) */
    const val DEVICE_UUID = "D5A74C04894A4E70C2AE0BDC687904FE"
    /** GMA 鉴权 BLE 密钥 */
    const val DEVICE_BLE_KEY = "19f2bb2b7bff8e994b7e244f65a989c7"
    /** 设备 SN */
    const val DEVICE_SN = "5200002612240211A002181"
    /** 眼镜蓝牙 MAC（实测：Qwen Glasses G1 191C） */
    const val GLASSES_MAC = "C4:D7:DC:40:19:1C"
    /** 眼镜设备名（实测） */
    const val GLASSES_NAME = "Qwen Glasses G1 191C"

    // ── BLE 广播特征（官方 APP 逆向确认，2026-08-30 vibeADB 真机验证）──
    /** 官方 APP 扫描眼镜的 Service UUID: 0xFEB3（Alibaba 私有） */
    val UUID_BLE_GLASSES_SERVICE_FEB3 =
        java.util.UUID.fromString("0000FEB3-0000-1000-8000-00805F9B34FB")
    /** 官方 APP 扫描眼镜的厂商 ID: 424 (0x1A8) */
    const val BLE_GLASSES_MANUFACTURER_ID = 424
    /** 厂商数据 "WoW"（87,111,87）—— 眼镜广播标识 */
    val BLE_GLASSES_ADV_WOW = byteArrayOf(0x57.toByte(), 0x6F.toByte(), 0x57.toByte())
    /** 厂商数据 "QWE"（81,87,69）—— 眼镜广播标识（另一形态） */
    val BLE_GLASSES_ADV_QWE = byteArrayOf(0x51.toByte(), 0x57.toByte(), 0x45.toByte())

    // ── 超时（毫秒） ──
    /** 握手各步发送间隔 */
    const val HANDSHAKE_STEP_DELAY_MS = 80L
    /** 等待眼镜上报连接参数的超时 */
    const val HANDSHAKE_INFO_TIMEOUT_MS = 6000L
    /** 等待 attach_success 的超时 */
    const val HANDSHAKE_ATTACH_TIMEOUT_MS = 8000L
    /** 连接超时 */
    const val CONNECT_TIMEOUT_MS = 12000L
}