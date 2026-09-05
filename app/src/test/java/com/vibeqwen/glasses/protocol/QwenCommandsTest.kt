package com.vibeqwen.glasses.protocol

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指令构造单测：sessionId / taskLinkId 生成规则、
 * 3 条开始指令与 2 条停止指令的结构。
 */
class QwenCommandsTest {

    private val json = QwenCommands.json

    @Test
    fun `开始录音生成3条指令且字段合规`() {
        val ts = 1788061683242L
        val hex32 = "FC9658C0DB8D4AD9BF4092EFA469D4E6"
        val msgs = QwenCommands.startRecordAt(ts, hex32)
        assertEquals(3, msgs.size)

        // ① code:AudioRecording + extensions.taskLinkId + sessionId=前10位
        val j1 = json.parseToJsonElement(msgs[0]).jsonObject
        assertEquals("AudioRecording", j1["code"]?.jsonPrimitive?.content)
        assertEquals("touch", j1["data"]?.jsonObject?.get("reason")?.jsonPrimitive?.content)
        assertEquals("1788061683", j1["sessionId"]?.jsonPrimitive?.content)
        val taskLinkId = j1["extensions"]?.jsonObject?.get("taskLinkId")?.jsonPrimitive?.content
        assertEquals("AudioRecording${ts}${hex32}", taskLinkId)

        // ② wakeupType=longRecord + scene
        val j2 = json.parseToJsonElement(msgs[1]).jsonObject
        assertEquals("longRecord", j2["wakeupType"]?.jsonPrimitive?.content)
        assertEquals("AudioRecording", j2["scene"]?.jsonPrimitive?.content)
        assertEquals(taskLinkId, j2["taskLinkId"]?.jsonPrimitive?.content)

        // ③ uri=airecord://start + pageType
        val j3 = json.parseToJsonElement(msgs[2]).jsonObject
        assertEquals("airecord://start", j3["uri"]?.jsonPrimitive?.content)
        assertEquals("SCHEME_AIRECORD_START", j3["pageType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `随机开始指令 taskLinkId 含32位大写HEX`() {
        val msgs = QwenCommands.startRecord()
        val j1 = json.parseToJsonElement(msgs[0]).jsonObject
        val taskLinkId = j1["extensions"]?.jsonObject?.get("taskLinkId")?.jsonPrimitive?.content
            ?: error("缺少 taskLinkId")
        assertTrue("taskLinkId 格式错误: $taskLinkId", Regex("^AudioRecording\\d{10,13}[0-9A-F]{32}$").matches(taskLinkId))
        // sessionId 为毫秒时间戳前 10 位
        val sid = j1["sessionId"]?.jsonPrimitive?.content ?: error("缺少 sessionId")
        assertEquals(10, sid.length)
        assertEquals(ts10Digits(), sid)
    }

    @Test
    fun `停止录音生成3条指令`() {
        val msgs = QwenCommands.stopRecord(12345)
        assertEquals(3, msgs.size)
        assertTrue(msgs[0].contains("\"type\":\"PART\""))
        assertTrue(msgs[0].contains("AudioRecording"))
        assertEquals("""{"code":"AudioRecording"}""", msgs[1])
        assertTrue(msgs[2].contains("\"type\":4"))
        assertTrue(msgs[2].contains("\"arg1\":12345"))
        assertTrue(msgs[2].contains("\"arg2\":1"))
    }

    @Test
    fun `握手消息结构`() {
        val sid = QwenCommands.sessionId(4196571)
        assertEquals("""{"sessionId":4196571}""", sid)
        val sn = QwenCommands.snAuth("D5A74C04894A4E70C2AE0BDC687904FE")
        assertTrue(sn.contains("\"type\":1103"))
        assertTrue(sn.contains("D5A74C04894A4E70C2AE0BDC687904FE"))
    }

    private fun ts10Digits(): String = System.currentTimeMillis().toString().take(10)
}