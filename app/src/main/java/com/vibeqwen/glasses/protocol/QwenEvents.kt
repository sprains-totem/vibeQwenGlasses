package com.vibeqwen.glasses.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 眼镜事件解析（纯 Kotlin，可单测）。
 *
 * 依据 docs/PROTOCOL.md §4.4 / §6 与 combo_test_report.md：
 * - record_start / record_end：power-state 事件（注意原始事件名带尾随空格，统一 trim）
 * - AudioRecording 状态机：status ∈ Running / TryExit / Exiting / Exited，reasonStop 区分 KEY/APP
 * - onHandler 遥测：recordDataSent / recordDataSentDura（可交叉验证帧数/时长）
 * - 心跳：AliGenie.System SynchronizeState / 含 heartbeat 文本
 * - attach_success：{"code":1,"msg":"attach_success"}
 */
data class GlassesEvent(
    val kind: EventKind,
    val raw: String,
    val eventType: String? = null,
    val eventName: String? = null,
    val recordStatus: String? = null,
    val reasonStop: String? = null,
    val recordDataSent: Long? = null,
    val recordDurationMs: Long? = null,
    val battery: Int? = null,
    val sessionId: Long? = null,
    val inputType: Int? = null,
    val inputTypeName: String? = null,
)

enum class EventKind {
    UNKNOWN,
    MESSAGE_RESULT,      // setMessageResult
    ACTIVE_DATA,         // active_data/odm 上报
    PAIR_INFO,           // pairAdv/pid/peerAddr 上报
    TYPE_10001_Q,        // {"type":10001,...} 眼镜侧下发
    ATTACH_SUCCESS,      // attach_success
    RECORD_START,        // power-state record_start
    RECORD_END,          // power-state record_end
    RECORD_STATUS,       // AudioRecording status: Running/TryExit/Exiting/Exited
    RECORD_TELEMETRY,    // AudioRecording onHandler 遥测（recordDataSent）
    BATTERY_STATUS,      // 电池电量状态上报 (fusion_level/quantity)
    INPUT_EVENT,         // 物理按键与触控事件 (type: 106, 75, 78等)
    HEARTBEAT,           // 心跳/同步状态
    DEVICE_PROPS,        // ro.product.* 属性
    TASK_LAYER,          // taskLayer 当前任务变更（含 code:AudioRecording）
    OTHER,
}

object QwenEvents {

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析单条眼镜 JSON 事件文本（任何异常都不抛出，返回 UNKNOWN） */
    fun parse(raw: String): GlassesEvent {
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            classify(raw, obj)
        } catch (e: Exception) {
            GlassesEvent(EventKind.UNKNOWN, raw)
        }
    }

    private fun classify(raw: String, obj: JsonObject): GlassesEvent {
        // ── attach_success（{"code":1,"msg":"attach_success"}） ──
        val msg = obj["msg"]?.jsonPrimitive?.contentOrNull
        if (msg == "attach_success") {
            return GlassesEvent(
                EventKind.ATTACH_SUCCESS, raw, eventName = "attach_success",
                sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )
        }

        // ── type:10001（眼镜下发） ──
        if (obj["type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() == 10001) {
            return GlassesEvent(EventKind.TYPE_10001_Q, raw)
        }

        // ── active_data / odm ──
        if (obj["active_data"] != null) {
            return GlassesEvent(EventKind.ACTIVE_DATA, raw)
        }

        // ── pairAdv / pid / peerAddr ──
        if (obj["pairAdv"] != null || obj["pid"] != null) {
            return GlassesEvent(EventKind.PAIR_INFO, raw)
        }

        // ── setMessageResult ──
        if (obj["setMessageResult"] != null) {
            return GlassesEvent(EventKind.MESSAGE_RESULT, raw)
        }

        // ── props 查询结果 ──
        if (obj["ro.product.model"] != null || obj["ro.product.brand"] != null) {
            return GlassesEvent(EventKind.DEVICE_PROPS, raw)
        }

        // ── 事件类字段 ──
        val eventType = obj["eventType"]?.jsonPrimitive?.contentOrNull
        val eventName = obj["eventName"]?.jsonPrimitive?.contentOrNull?.trim()
        val contextInfo = obj["contextInfo"]?.jsonObject

        // ── 录音开始/结束（power-state / record_start / record_end） ──
        if ((eventType == "power-state" || eventType == null) &&
            (eventName == "record_start" || eventName == "record_end")
        ) {
            val battery = contextInfo?.get("battery")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            return GlassesEvent(
                kind = if (eventName == "record_start") EventKind.RECORD_START else EventKind.RECORD_END,
                raw = raw, eventType = eventType, eventName = eventName, battery = battery,
                recordDurationMs = contextInfo?.get("duration")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )
        }

        // ── AudioRecording 状态变更（code:AudioRecording + status） ──
        if (obj["code"]?.jsonPrimitive?.contentOrNull == "AudioRecording" &&
            obj["status"]?.jsonPrimitive?.contentOrNull != null
        ) {
            return GlassesEvent(
                EventKind.RECORD_STATUS, raw, eventType = eventType, eventName = eventName,
                recordStatus = obj["status"]?.jsonPrimitive?.contentOrNull,
                reasonStop = obj["reasonStop"]?.jsonPrimitive?.contentOrNull,
                sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )
        }

        // ── taskLayer 当前任务变更 ──
        if (obj.containsKey("current") &&
            obj["current"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull == "AudioRecording"
        ) {
            return GlassesEvent(EventKind.TASK_LAYER, raw, eventType = eventType)
        }
        val taskLayer = obj["eventContext"]?.jsonObject?.get("taskLayer")?.jsonObject
        if (taskLayer != null) {
            val curCode = taskLayer.get("current")?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
            if (eventName == null && curCode == "AudioRecording") {
                return GlassesEvent(EventKind.TASK_LAYER, raw, eventType = eventType)
            }
        }

        // ── 电池电量状态上报 ──
        if (eventType == "battery" && eventName == "battery_status") {
            val fusionLevel = contextInfo?.get("fusion_level")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val level = contextInfo?.get("level")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            return GlassesEvent(
                EventKind.BATTERY_STATUS, raw, eventType = eventType, eventName = eventName,
                battery = fusionLevel ?: level
            )
        }
        if (obj["identifier"]?.jsonPrimitive?.contentOrNull == "power") {
            val quantity = obj["value"]?.jsonObject?.get("quantity")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (quantity != null) {
                return GlassesEvent(
                    EventKind.BATTERY_STATUS, raw, battery = quantity
                )
            }
        }

        // ── 输入按键/触控事件 (电源键单击106, 单指长按75, 多指长按78) ──
        if (eventType == "input" && (eventName == "event" || eventName == "handle")) {
            val type = contextInfo?.get("type")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val typeName = contextInfo?.get("typeName")?.jsonPrimitive?.contentOrNull
            return GlassesEvent(
                EventKind.INPUT_EVENT, raw, eventType = eventType, eventName = eventName,
                inputType = type, inputTypeName = typeName
            )
        }

        // ── 录音遥测（AudioRecording / onHandler / recordDataSent） ──
        if (eventType == "AudioRecording" && eventName == "onHandler") {
            val sent = contextInfo?.get("recordDataSent")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val dura = contextInfo?.get("recordDataSentDura")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val battery = contextInfo?.get("battery")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            return GlassesEvent(
                EventKind.RECORD_TELEMETRY, raw, eventType = eventType, eventName = eventName,
                recordDataSent = sent, recordDurationMs = dura, battery = battery,
            )
        }

        // ── 心跳 / 同步状态 ──
        val eventNs = obj["eventNs"]?.jsonPrimitive?.contentOrNull
        if (eventName != null && (eventName.contains("heartbeat", true) ||
                (eventNs == "AliGenie.System" && eventName == "SynchronizeState") ||
                eventName.contains("Synchronize", true))
        ) {
            return GlassesEvent(EventKind.HEARTBEAT, raw, eventType = eventType, eventName = eventName)
        }
        if (raw.contains("heartbeat", ignoreCase = true)) {
            return GlassesEvent(EventKind.HEARTBEAT, raw)
        }

        // ── 其他事件 ──
        return GlassesEvent(
            EventKind.OTHER, raw, eventType = eventType, eventName = eventName,
            sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        )
    }
}