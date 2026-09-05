package com.vibeqwen.glasses.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeqwen.glasses.audio.GlobalAudioPlayer
import com.vibeqwen.glasses.ui.components.WaveformBar
import com.vibeqwen.glasses.util.TimeFormat

/**
 * 播放器底部弹层：交互式波形进度条 + 变速循环控制，完全基于 GlobalAudioPlayer 单一信源。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    onDismiss: () -> Unit = { GlobalAudioPlayer.closeSheet() },
) {
    val state by GlobalAudioPlayer.state.collectAsStateWithLifecycle()
    val cur = state.current ?: return
    var draggingPosition by remember { mutableStateOf<Float?>(null) }

    val totalDur = state.durationMs.coerceAtLeast(1L)
    val displayPos = (draggingPosition?.toLong() ?: state.positionMs).coerceIn(0L, totalDur)
    val progress = (displayPos.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)

    // 人性化标题处理
    val friendlyTitle = remember(cur.displayName) {
        val regex = Regex("""rec_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})""")
        val m = regex.find(cur.displayName)
        if (m != null) {
            val (_, mth, day, hour, min) = m.destructured
            "${mth}月${day}日 ${hour}:${min} 的现场录音"
        } else {
            cur.displayName.removeSuffix(".wav")
        }
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                friendlyTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${TimeFormat.size(cur.sizeBytes)} · 16kHz WAV PCM · ${cur.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // 交互式波形播放卡片（支持点击/拖拽即时 Seek）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .pointerInput(totalDur) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            GlobalAudioPlayer.seekTo((ratio * totalDur).toLong())
                        }
                    }
                    .pointerInput(totalDur) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                draggingPosition = ratio * totalDur
                            },
                            onDragEnd = {
                                draggingPosition?.let { GlobalAudioPlayer.seekTo(it.toLong()) }
                                draggingPosition = null
                            },
                            onHorizontalDrag = { change, _ ->
                                val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                                draggingPosition = ratio * totalDur
                            }
                        )
                    }
            ) {
                WaveformBar(
                    values = state.peaks.toList(),
                    progress = progress,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(8.dp))

            // 时间行与进度指示
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    TimeFormat.clock(displayPos),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    TimeFormat.clock(state.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // 主播放控制栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 循环切换（带明显激活背景指示）
                FilledIconButton(
                    onClick = { GlobalAudioPlayer.toggleLoop() },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.loop) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = if (state.loop) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = if (state.loop) "循环模式开启" else "循环模式关闭",
                        modifier = Modifier.size(22.dp),
                    )
                }

                // 快退 10 秒
                FilledIconButton(
                    onClick = { GlobalAudioPlayer.skip(-10_000) },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "后退10秒")
                }

                // 核心播放 / 暂停按钮（大圆强调）
                FilledIconButton(
                    onClick = { GlobalAudioPlayer.togglePlayPause() },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                // 快进 10 秒
                FilledIconButton(
                    onClick = { GlobalAudioPlayer.skip(10_000) },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Icon(Icons.Filled.FastForward, contentDescription = "前进10秒")
                }

                // 倍速切换胶囊 + 下拉精选菜单
                var showSpeedMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { showSpeedMenu = true },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "${state.speed}x".replace(".0x", "x"),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { sp ->
                            val isSel = kotlin.math.abs(state.speed - sp) < 0.05f
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (sp == 1.0f) "1.0x (原速)" else "${sp}x",
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        style = if (isSel) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
                                    )
                                },
                                onClick = {
                                    GlobalAudioPlayer.setSpeed(sp)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
