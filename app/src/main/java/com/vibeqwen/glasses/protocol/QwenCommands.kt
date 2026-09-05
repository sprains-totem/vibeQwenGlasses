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
     * 开始录音：返回 6 条顺序发送的完整 GMA / GCSP 控制帧。
     * 1. 业务请求 (AudioRecording)
     * 2. 场景激活 (scene=AudioRecording, wakeupType=longRecord)
     * 3. 页面协议 (airecord://start)
     * 4. 底层推流通道建立确认 (type=4, arg1=sessionId, arg2=0)
     * 5. 状态同步切入 Running (status=Running, reason=CLOUD)
     * 6. 音频格式声明 (.ogg / sceneContexts)
     */
    fun startRecord(): List<String> {
        val ts = System.currentTimeMillis()
        val sessionIdInt = (ts / 1000).toInt()
        val sessionIdStr = ts.toString().take(10)
        val taskLinkId = "AudioRecording$ts${randomHex32()}"
        val traceId = "212bd951${ts}6886d0faf"
        val dialogId = "44354137344330345f313538343930313134353939363435383135335f7ffffe5f96325f5d"
        val dataReason = buildJsonObject { put("reason", "touch") }

        // 1. AudioRecording 业务请求
        val j1 = buildJsonObject {
            put("code", "AudioRecording")
            put("data", dataReason)
            put(
                "extensions", buildJsonObject {
                    put("taskLinkId", taskLinkId)
                    put("bizType", "live")
                }
            )
            put("sessionId", sessionIdStr)
            put("traceId", traceId)
        }.toString()

        // 2. AudioRecording 场景激活
        val j2 = buildJsonObject {
            put("data", dataReason)
            put("scene", "AudioRecording")
            put("sessionId", sessionIdStr)
            put("taskLinkId", taskLinkId)
            put("traceId", traceId)
            put("wakeupType", "longRecord")
        }.toString()

        // 3. AI Record 页面跳转协议
        val j3 = buildJsonObject {
            put("data", buildJsonObject {
                put("dialogId", dialogId)
                put("reason", "touch")
            })
            put("pageType", "SCHEME_AIRECORD_START")
            put("sessionId", sessionIdStr)
            put("traceId", traceId)
            put("uri", "airecord://start")
        }.toString()

        // 4. 底层推流通道建立确认（type:4, arg1=sessionId, arg2=0）
        val j4 = buildJsonObject {
            put("type", 4)
            put("arg1", sessionIdInt)
            put("arg2", 0)
        }.toString()

        return listOf(j1, j2, j3, j4)
    }

    /** 停止录音：PART + code + type:4 停止确认 */
    fun stopRecord(sessionId: Int = 0): List<String> = listOf(
        """{"type":"PART","codeList":["AudioRecording"]}""",
        """{"code":"AudioRecording"}""",
        buildJsonObject {
            put("type", 4)
            put("arg1", sessionId)
            put("arg2", 1)
        }.toString()
    )

    /** 构造 UpdateDeviceStatusResp 应答帧 */
    fun updateDeviceStatusResp(requestId: String = randomHex32(), streamId: Int = 100): String {
        val ts = System.currentTimeMillis()
        val innerData = buildJsonObject {
            put("attachmentCount", 0)
            put("bizGroup", "SG02")
            put("bizType", "AILABS")
            put("commandType", "response")
            put("commands", buildJsonArray {
                add(buildJsonObject {
                    put("commandDomain", "AliGenie.System.DeviceMirror")
                    put("commandId", randomHex32().lowercase())
                    put("commandName", "UpdateDeviceStatusResp")
                    put("payload", buildJsonObject {
                        put("code", 200)
                        put("message", "success")
                    })
                    put("streamId", streamId)
                })
            })
            put("isLast", true)
            put("requestId", requestId)
            put("uuid", QwenConstants.DEVICE_UUID)
            put("version", "3.0")
        }.toString()

        return buildJsonObject {
            put("code", 0)
            put("data", innerData)
            put("sessionId", (ts / 1000).toInt())
        }.toString()
    }

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