package com.vibeqwen.glasses.ui.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeqwen.glasses.audio.RecordingFileManager.RecordingInfo
import kotlinx.coroutines.Job
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
 * 播放器 ViewModel：MediaPlayer 封装 + 变速 / 循环 / 跳转 / 波形峰值。
 */
class PlayerViewModel(private val info: RecordingInfo) : ViewModel() {

    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val speed: Float = 1f,
        val loop: Boolean = false,
        val peaks: FloatArray = FloatArray(0),
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    init {
        prepare()
    }

    private fun prepare() {
        _ui.update { it.copy(peaks = computePeaks(info.file, 200)) }
        player = try {
            MediaPlayer().apply {
                setDataSource(info.file.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                prepare()
                isLooping = false
                setOnCompletionListener {
                    seekTo(0)
                    _ui.update { it.copy(isPlaying = false, positionMs = 0L) }
                }
            }
        } catch (e: Exception) {
            _ui.update { it.copy(error = "无法播放：${e.message}") }
            null
        }
        _ui.update { it.copy(durationMs = player?.duration?.toLong() ?: 0L) }
        startProgress()
    }

    fun playPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            _ui.update { it.copy(isPlaying = false) }
        } else {
            if (p.duration > 0 && p.currentPosition >= p.duration - 300) {
                p.seekTo(0)
                _ui.update { it.copy(positionMs = 0L) }
            }
            p.start()
            _ui.update { it.copy(isPlaying = true) }
        }
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        val clamped = ms.coerceIn(0L, p.duration.coerceAtLeast(0).toLong())
        p.seekTo(clamped.toInt())
        _ui.update { it.copy(positionMs = clamped) }
    }

    /** ±10s 跳转 */
    fun skip(deltaMs: Long) {
        val p = player ?: return
        seekTo(p.currentPosition.toLong() + deltaMs)
    }

    fun toggleLoop() {
        val next = !_ui.value.loop
        player?.isLooping = next
        _ui.update { it.copy(loop = next) }
    }

    private val speedList = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)

    fun cycleSpeed() {
        val cur = _ui.value.speed
        val curIdx = speedList.indexOfFirst { abs(it - cur) < 0.05f }
        val nextSpeed = if (curIdx >= 0) speedList[(curIdx + 1) % speedList.size] else 1.0f
        setSpeed(nextSpeed)
    }

    /** 变速 0.5x ~ 2.0x（PlaybackParams，API 23+） */
    fun setSpeed(speed: Float) {
        val p = player ?: return
        try {
            p.playbackParams = p.playbackParams.setSpeed(speed)
            _ui.update { it.copy(speed = speed) }
        } catch (e: Exception) {
            _ui.update { it.copy(error = "变速失败：${e.message}") }
        }
    }

    private fun startProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val p = player ?: break
                val dur = _ui.value.durationMs
                if (p.isPlaying) {
                    _ui.update { it.copy(positionMs = p.currentPosition.toLong()) }
                } else if (dur > 0 && !p.isLooping && p.currentPosition >= p.duration - 200) {
                    // 播放到末尾自动停下并复位
                    _ui.update { it.copy(isPlaying = false, positionMs = 0L) }
                }
                delay(200)
            }
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        player?.release()
        player = null
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