package com.vibeqwen.glasses.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 398B 音频帧解析器单测：
 * - 魔数头全局匹配（动态 CID 的替代方案）
 * - 帧跨 read 分片 / 多帧粘包
 * - 序号跳变容忍统计
 * - [13..396] PCM 提取正确
 */
class QwenFrameParserTest {

    private fun makeFrame(seq: Int, pcmSeed: Int = seq): ByteArray {
        val f = ByteArray(QwenConstants.AUDIO_FRAME_SIZE)
        QwenConstants.AUDIO_MAGIC.copyInto(f, 0)
        f[8] = seq.toByte()
        // 填充可辨识 PCM 模式：pcmSeed.. 递增（16bit LE）
        for (k in 0 until 192) {
            val v = (pcmSeed + k) and 0xFFFF
            f[QwenConstants.AUDIO_HEADER_SIZE + k * 2] = (v and 0xFF).toByte()
            f[QwenConstants.AUDIO_HEADER_SIZE + k * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return f
    }

    @Test
    fun `单帧完整解析`() {
        val parser = QwenFrameParser()
        val frames = parser.feed(makeFrame(0x5B))
        assertEquals(1, frames.size)
        assertEquals(0x5B, frames[0].seq)
        assertEquals(QwenConstants.AUDIO_PCM_SIZE, frames[0].pcm.size)
        assertEquals(1L, parser.totalFrames)
        // PCM 内容：首样本 = pcmSeed
        val v = ((frames[0].pcm[0].toInt() and 0xFF) or (frames[0].pcm[1].toInt() shl 8))
        assertEquals(0x5B and 0xFFFF, v)
    }

    @Test
    fun `一包多帧粘包`() {
        val parser = QwenFrameParser()
        val chunk = makeFrame(0x01) + makeFrame(0x02) + makeFrame(0x03)
        val frames = parser.feed(chunk)
        assertEquals(3, frames.size)
        assertEquals(listOf(1, 2, 3), frames.map { it.seq })
        assertEquals(0L, parser.seqJumps)
    }

    @Test
    fun `帧跨分片重组`() {
        val parser = QwenFrameParser()
        val frame = makeFrame(0x5C)
        // 切成 3 段喂入
        val parts = listOf(
            frame.copyOfRange(0, 7),
            frame.copyOfRange(7, 300),
            frame.copyOfRange(300, frame.size),
        )
        val all = parts.flatMap { parser.feed(it) }
        assertEquals(1, all.size)
        assertEquals(0x5C, all[0].seq)
    }

    @Test
    fun `垃圾前缀被跳过`() {
        val parser = QwenFrameParser()
        val junk = "AT+BRSF=1023\r\n".toByteArray()
        val frame = makeFrame(0x0A)
        val frames = parser.feed(junk + frame)
        assertEquals(1, frames.size)
        assertEquals(0x0A, frames[0].seq)
        assertTrue(parser.droppedBytes > 0)
    }

    @Test
    fun `序号跳变容忍并统计`() {
        val parser = QwenFrameParser()
        parser.feed(makeFrame(0x5B))
        val frames = parser.feed(makeFrame(0x5D)) // 跳过 0x5C
        assertEquals(1, frames.size) // 不丢帧
        assertEquals(1L, parser.seqJumps)
        assertEquals(2L, parser.totalFrames)
    }

    @Test
    fun `序号回绕容忍`() {
        val parser = QwenFrameParser()
        parser.feed(makeFrame(0xFF))
        val frames = parser.feed(makeFrame(0x00))
        assertEquals(1, frames.size)
        assertEquals(0L, parser.seqJumps)
    }

    @Test
    fun `reset 重置统计`() {
        val parser = QwenFrameParser()
        parser.feed(makeFrame(0x01))
        parser.reset()
        assertEquals(0L, parser.totalFrames)
        assertEquals(0L, parser.seqJumps)
        assertEquals(0L, parser.droppedBytes)
    }

    @Test
    fun `无魔数数据仅累积不产出帧`() {
        val parser = QwenFrameParser()
        val frames = parser.feed(ByteArray(1000) { it.toByte() })
        assertEquals(0, frames.size)
    }

    @Test
    fun `BLE L2CAP 395字节音频帧解析`() {
        val parser = QwenFrameParser()
        val f = ByteArray(QwenConstants.AUDIO_FRAME_SIZE_BLE)
        QwenConstants.AUDIO_MAGIC_BLE.copyInto(f, 0)
        f[6] = 0xE8.toByte()
        // 7..10 为 0
        // 11..394 为 PCM
        f[11] = 0x12
        f[12] = 0x34
        val frames = parser.feed(f)
        assertEquals(1, frames.size)
        assertEquals(0xE8, frames[0].seq)
        assertEquals(384, frames[0].pcm.size)
        assertEquals(0x3412, ((frames[0].pcm[1].toInt() and 0xFF) shl 8) or (frames[0].pcm[0].toInt() and 0xFF))
    }
}