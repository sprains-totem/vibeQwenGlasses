package com.vibeqwen.glasses.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.vibeqwen.glasses.audio.RecordingFileManager.RecordingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * 全局单例音频播放管理器：单一信源驱动整个 App 的播放状态、Mini 播放条与 PlayerSheet 详情。
 */
object GlobalAudioPlayer {

    data class State(
        val current: RecordingInfo? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val speed: Float = 1.0f,
        val loop: Boolean = false,
        val peaks: FloatArray = FloatArray(0),
        val sheetVisible: Boolean = false,
        val error: String? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is State) return false
            if (current != other.current) return false
            if (isPlaying != other.isPlaying) return false
            if (positionMs != other.positionMs) return false
            if (durationMs != other.durationMs) return false
            if (speed != other.speed) return false
            if (loop != other.loop) return false
            if (!peaks.contentEquals(other.peaks)) return false
            if (sheetVisible != other.sheetVisible) return false
            return error == other.error
        }

        override fun hashCode(): Int {
            var result = current?.hashCode() ?: 0
            result = 31 * result + isPlaying.hashCode()
            result = 31 * result + positionMs.hashCode()
            result = 31 * result + durationMs.hashCode()
            result = 31 * result + speed.hashCode()
            result = 31 * result + loop.hashCode()
            result = 31 * result + peaks.contentHashCode()
            result = 31 * result + sheetVisible.hashCode()
            result = 31 * result + (error?.hashCode() ?: 0)
            return result
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    private val speedList = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)

    /** 播放或切换指定录音 */
    fun play(info: RecordingInfo, openSheet: Boolean = true) {
        val cur = _state.value.current
        if (cur?.file?.absolutePath == info.file.absolutePath && player != null) {
            // 同一首歌曲：如果正在播放则暂停，暂停则继续，并按需打开弹层
            val p = player ?: return
            if (p.isPlaying) {
                p.pause()
                _state.update { it.copy(isPlaying = false, sheetVisible = if (openSheet) true else it.sheetVisible) }
            } else {
                if (p.duration > 0 && p.currentPosition >= p.duration - 200) {
                    p.seekTo(0)
                    _state.update { it.copy(positionMs = 0L) }
                }
                p.start()
                _state.update { it.copy(isPlaying = true, sheetVisible = if (openSheet) true else it.sheetVisible) }
            }
            return
        }

        // 切换新歌曲
        releasePlayer()
        val peaks = computePeaks(info.file, 120)
        try {
            val mp = MediaPlayer().apply {
                setDataSource(info.file.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                prepare()
                isLooping = false
                if (_state.value.speed != 1.0f) {
                    val params = playbackParams.setSpeed(_state.value.speed)
                    playbackParams = params
                }
                setOnCompletionListener {
                    if (_state.value.loop) {
                        seekTo(0)
                        start()
                        _state.update { it.copy(isPlaying = true, positionMs = 0L) }
                    } else {
                        seekTo(0)
                        _state.update { it.copy(isPlaying = false, positionMs = 0L) }
                    }
                }
            }
            player = mp
            mp.start()
            _state.update {
                it.copy(
                    current = info,
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = mp.duration.toLong(),
                    peaks = peaks,
                    sheetVisible = openSheet,
                    error = null,
                )
            }
            startProgressLoop()
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    current = info,
                    isPlaying = false,
                    error = "无法播放：${e.message}",
                    sheetVisible = openSheet,
                )
            }
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            _state.update { it.copy(isPlaying = false) }
        } else {
            if (p.duration > 0 && p.currentPosition >= p.duration - 200) {
                p.seekTo(0)
                _state.update { it.copy(positionMs = 0L) }
            }
            p.start()
            _state.update { it.copy(isPlaying = true) }
        }
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        val clamped = ms.coerceIn(0L, p.duration.coerceAtLeast(0).toLong())
        p.seekTo(clamped.toInt())
        _state.update { it.copy(positionMs = clamped) }
    }

    fun skip(deltaMs: Long) {
        val p = player ?: return
        seekTo(p.currentPosition.toLong() + deltaMs)
    }

    fun toggleLoop() {
        val next = !_state.value.loop
        _state.update { it.copy(loop = next) }
    }

    fun cycleSpeed() {
        val cur = _state.value.speed
        val curIdx = speedList.indexOfFirst { abs(it - cur) < 0.05f }
        val nextSpeed = if (curIdx >= 0) speedList[(curIdx + 1) % speedList.size] else 1.0f
        setSpeed(nextSpeed)
    }

    fun setSpeed(speed: Float) {
        val wasPlaying = _state.value.isPlaying
        _state.update { it.copy(speed = speed) }
        val p = player ?: return
        try {
            val params = p.playbackParams.setSpeed(speed)
            p.playbackParams = params
            if (!wasPlaying) {
                // 关键：Android 设置 playbackParams 会强制底层开始播放，若之前为暂停，必须立即 pause 保持暂停！
                p.pause()
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "变速失败: ${e.message}") }
        }
    }

    fun openSheet() {
        _state.update { it.copy(sheetVisible = true) }
    }

    fun closeSheet() {
        _state.update { it.copy(sheetVisible = false) }
    }

    fun closePlayer() {
        releasePlayer()
        _state.update {
            it.copy(
                current = null,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
                sheetVisible = false,
                error = null,
            )
        }
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        progressJob = null
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val p = player ?: break
                if (p.isPlaying) {
                    val curPos = p.currentPosition.toLong()
                    val dur = p.duration.toLong()
                    if (dur > 0 && curPos >= dur - 150 && _state.value.loop) {
                        p.seekTo(0)
                        _state.update { it.copy(positionMs = 0L) }
                    } else {
                        _state.update { it.copy(positionMs = curPos) }
                    }
                }
                delay(150)
            }
        }
    }

    /** 从 WAV 读 PCM 计算峰值，全量等步长采样 */
    private fun computePeaks(file: File, target: Int = 120): FloatArray {
        if (file.extension.lowercase() != "wav") return FloatArray(0)
        val fileLen = file.length()
        if (fileLen < 44) return FloatArray(0)
        val pcmBytes = fileLen - 44
        val totalSamples = (pcmBytes / 2).toInt()
        if (totalSamples <= 0) return FloatArray(0)

        val peaks = FloatArray(target)
        try {
            RandomAccessFile(file, "r").use { raf ->
                val stepSamples = (totalSamples / target).coerceAtLeast(1)
                val sampleBlock = ByteArray(minOf(stepSamples * 2, 4096))
                for (b in 0 until target) {
                    val sampleIdx = (b.toLong() * totalSamples / target).coerceAtMost(totalSamples - 1L)
                    val offset = 44L + sampleIdx * 2L
                    raf.seek(offset)
                    val readBytes = raf.read(sampleBlock)
                    var maxAbs = 0
                    var i = 0
                    while (i + 1 < readBytes) {
                        val lo = sampleBlock[i].toInt() and 0xFF
                        val hi = sampleBlock[i + 1].toInt()
                        val s = (lo or (hi shl 8)).toShort().toInt()
                        val a = abs(s)
                        if (a > maxAbs) maxAbs = a
                        i += 2
                    }
                    peaks[b] = (maxAbs / 32768f).coerceIn(0f, 1f)
                }
            }
        } catch (_: Exception) {
            return FloatArray(0)
        }
        return peaks
    }
}
