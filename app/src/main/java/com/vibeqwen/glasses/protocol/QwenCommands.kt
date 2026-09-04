package com.vibeqwen.glasses.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom

/**
 * 千问 G1 眼镜指令构造（纯 Kotlin，可单测）。
 *
 * 依据 docs/PROTOCOL.md §3 / §4：
 * - 握手：device 查询 → calendarSync → messageId → 等上报 → type:10001 → sessionId → support → type:1103(SN) → attach
 * - 开始录音：3 条 JSON（code:AudioRecording / wakeupType:longRecord / uri:airecord://start）
 * - 停止录音：PART + code 两条 JSON
 */
object QwenCommands {

    private val random = SecureRandom()

    /** 生成 32 位大写 HEX（taskLinkId 尾部随机段） */
    fun randomHex32(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    // ── 握手指令 ──

    /** {"device":[]} 设备查询（发送两次，按抓包原样） */
    fun queryDevice(): String = """{"device":[]}"""

    /** {} 空查询 */
    fun queryDeviceEmpty(): String = """{}"""

    /** calendarSync 配置同步 */
    fun calendarSync(): String = buildJsonObject {
        put(
            "device", buildJsonArray {
                add(
                    buildJsonObject {
                        put("identifier", "calendarSync")
                        put(
                            "value",
                            """{"calendarSyncEnable":false,"notificationSyncEnable":false,"scheduleEnable":false}"""
                        )
                    }
                )
            }
        )
    }.toString()

    /** messageId + phoneType:1 + supportHeicDecode:1（与眼镜 setMessageResult 配对） */
    fun messageId(ts: Long = System.currentTimeMillis()): String = buildJsonObject {
        put("messageId", ts.toString())
        put("phoneType", 1)
        put("supportHeicDecode", 1)
    }.toString()

    /** type:10001 同构应答 */
    fun type10001(): String = """{"type":10001,"arg1":1,"arg2":1}"""

    /** sessionId（APP 分配递增整数） */
    fun sessionId(session: Long): String = """{"sessionId":$session}"""

    /** support:true */
    fun support(): String = """{"support":true}"""

    /** type:1103 设备 UUID / 凭据认证（官方抓包证实下发 deviceUuid） */
    fun snAuth(uuid: String = QwenConstants.DEVICE_UUID): String = buildJsonObject {
        put("type", 1103)
        put("arg1", 1)
        put("arg2", 0)
        put("data", uuid)
    }.toString()

    /** 别名：设备 UUID 认证 */
    fun deviceAuth(uuid: String = QwenConstants.DEVICE_UUID): String = snAuth(uuid)

    /** 手机通知眼镜挂载成功：{"code":1,"msg":"attach_success"}（官方抓包证实由手机发给眼镜） */
    fun attachSuccess(): String = """{"code":1,"msg":"attach_success"}"""

    /** 高德导航支持声明 */
    fun amapNavigation(): String = """{"code":"Amap-Navigation"}"""

    // ── 录音控制 ──

    /**
     * 开始录音：返回 3 条顺序发送的 JSON。
     * sessionId = 毫秒时间戳前 10 位；taskLinkId = "AudioRecording" + 时间戳 + 32 位大写 HEX；
     * wakeupType = longRecord；reason = touch。
     */
    fun startRecord(): List<String> {
        val ts = System.currentTimeMillis()
        val sessionId = ts.toString().take(10)
        val taskLinkId = "AudioRecording$ts${randomHex32()}"
        val dataReason = buildJsonObject { put("reason", "touch") }

        val j1 = buildJsonObject {
            put("code", "AudioRecording")
            put("data", dataReason)
            put(
                "extensions", buildJsonObject {
                    put("taskLinkId", taskLinkId)
                    put("bizType", "live")
                }
            )
            put("sessionId", sessionId)
        }.toString()

        val j2 = buildJsonObject {
            put("data", dataReason)
            put("scene", "AudioRecording")
            put("sessionId", sessionId)
            put("taskLinkId", taskLinkId)
            put("wakeupType", "longRecord")
        }.toString()

        val j3 = buildJsonObject {
            put("data", dataReason)
            put("pageType", "SCHEME_AIRECORD_START")
            put("sessionId", sessionId)
            put("uri", "airecord://start")
        }.toString()

        return listOf(j1, j2, j3)
    }

    /** 停止录音：PART + code 两条 JSON */
    fun stopRecord(): List<String> = listOf(
        """{"type":"PART","codeList":["AudioRecording"]}""",
        """{"code":"AudioRecording"}""",
    )

    /** props 查询（握手后可选项） */
    fun queryProps(): String =
        """{"props":["ro.product.model","ro.product.brand"]}"""

    /** 供测试使用：固定时间戳构造开始指令 */
    fun startRecordAt(ts: Long, hex32: String): List<String> {
        val sessionId = ts.toString().take(10)
        val taskLinkId = "AudioRecording$ts$hex32"
        val dataReason = buildJsonObject { put("reason", "touch") }
        val j1 = buildJsonObject {
            put("code", "AudioRecording")
            put("data", dataReason)
            put(
                "extensions", buildJsonObject {
                    put("taskLinkId", taskLinkId)
                    put("bizType", "live")
                }
            )
            put("sessionId", sessionId)
        }.toString()
        val j2 = buildJsonObject {
            put("data", dataReason)
            put("scene", "AudioRecording")
            put("sessionId", sessionId)
            put("taskLinkId", taskLinkId)
            put("wakeupType", "longRecord")
        }.toString()
        val j3 = buildJsonObject {
            put("data", dataReason)
            put("pageType", "SCHEME_AIRECORD_START")
            put("sessionId", sessionId)
            put("uri", "airecord://start")
        }.toString()
        return listOf(j1, j2, j3)
    }

    /** 供测试使用：解析 JSON 校验（避免测试里重复写解析逻辑） */
    val json = Json { ignoreUnknownKeys = true }
}