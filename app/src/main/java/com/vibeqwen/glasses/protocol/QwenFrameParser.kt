package com.vibeqwen.glasses.protocol

/**
 * 395B (BLE L2CAP) / 398B (Classic RFCOMM) 音频帧解析器（纯 Kotlin，可在 JVM 单测）。
 *
 * 要点（真机抓包实测确认）：
 * - BLE L2CAP 格式：6B 魔数 (89 01 07 01 86 08) + 1B 序号 + 4B 填充 + 384B PCM = 395B
 * - 经典蓝牙格式：8B 魔数 (87 EF 12 03 07 01 86 08) + 1B 序号 + 4B 填充 + 384B PCM + 1B 尾部 = 398B
 * - 容忍：帧跨 socket read 分片、多帧粘包、序号跳变/回绕（不丢弃，仅统计）。
 * - 提取规则：提取 384B 为 16kHz 16bit 单声道 PCM。
 */
class QwenFrameParser {

    /** 单帧解析结果 */
    data class AudioFrame(
        val seq: Int,
        val pcm: ByteArray,
    )

    private data class MatchResult(
        val idx: Int,
        val frameSize: Int,
        val headerSize: Int,
        val seqOffset: Int,
    )

    private var buf = ByteArray(0)
    private var head = 0
    private var lastSeq = -1

    /** 累计解析帧数 */
    var totalFrames = 0L
        private set

    /** 序号跳变累计次数（容忍，不丢帧） */
    var seqJumps = 0L
        private set

    /** 被丢弃的非音频残余字节累计 */
    var droppedBytes = 0L
        private set

    /** 追加网络字节，返回本次解析出的完整音频帧（可能为空） */
    fun feed(data: ByteArray): List<AudioFrame> {
        // 压缩已消费的头部
        if (head > 0) {
            buf = buf.copyOfRange(head, buf.size)
            head = 0
        }
        buf = if (buf.isEmpty()) data.copyOf() else buf + data

        val out = ArrayList<AudioFrame>()
        var match = findNextMagic(head)
        while (match != null) {
            val idx = match.idx
            // 不足一帧长度：保留等待后续数据
            if (buf.size - idx < match.frameSize) break

            // 序号
            val seq = buf[idx + match.seqOffset].toInt() and 0xFF
            if (lastSeq >= 0) {
                val expected = (lastSeq + 1) and 0xFF
                if (seq != expected) seqJumps++
            }
            lastSeq = seq

            // 提取 PCM：跳过帧头，取 384B
            val pcm = buf.copyOfRange(
                idx + match.headerSize,
                idx + match.headerSize + QwenConstants.AUDIO_PCM_SIZE
            )
            out.add(AudioFrame(seq, pcm))
            totalFrames++

            val consumed = idx + match.frameSize
            droppedBytes += (idx - head) // 仅帧前的残余（垃圾/其他协议文本）计入丢弃
            head = consumed
            match = findNextMagic(head)
        }

        // 长时间未匹配魔数（例如一段无音频的文本）：防缓冲无限增长，丢弃超长残余
        if (match == null && buf.size - head > QwenConstants.AUDIO_FRAME_SIZE_CLASSIC * 16) {
            droppedBytes += (buf.size - head)
            head = buf.size
        }
        return out
    }

    /** 新录音段开始前重置统计（序号重新计数） */
    fun reset() {
        buf = ByteArray(0)
        head = 0
        lastSeq = -1
        totalFrames = 0
        seqJumps = 0
        droppedBytes = 0
    }

    private fun findNextMagic(from: Int): MatchResult? {
        val magicBle = QwenConstants.AUDIO_MAGIC_BLE
        val magicClassic = QwenConstants.AUDIO_MAGIC_CLASSIC
        var i = from
        val limit = buf.size - magicBle.size

        while (i <= limit) {
            // 先尝试匹配 6 字节 BLE 魔数 (89 01 07 01 86 08)
            var okBle = true
            for (j in magicBle.indices) {
                if (buf[i + j] != magicBle[j]) {
                    okBle = false
                    break
                }
            }
            if (okBle) {
                return MatchResult(
                    idx = i,
                    frameSize = QwenConstants.AUDIO_FRAME_SIZE_BLE,
                    headerSize = QwenConstants.AUDIO_HEADER_SIZE_BLE,
                    seqOffset = 6
                )
            }

            // 再尝试匹配 8 字节经典魔数 (87 EF 12 03 07 01 86 08)
            if (i <= buf.size - magicClassic.size) {
                var okClassic = true
                for (j in magicClassic.indices) {
                    if (buf[i + j] != magicClassic[j]) {
                        okClassic = false
                        break
                    }
                }
                if (okClassic) {
                    return MatchResult(
                        idx = i,
                        frameSize = QwenConstants.AUDIO_FRAME_SIZE_CLASSIC,
                        headerSize = QwenConstants.AUDIO_HEADER_SIZE_CLASSIC,
                        seqOffset = 8
                    )
                }
            }

            i++
        }
        return null
    }
}