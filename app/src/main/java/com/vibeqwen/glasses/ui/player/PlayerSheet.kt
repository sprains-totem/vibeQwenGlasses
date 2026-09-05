package com.vibeqwen.glasses.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibeqwen.glasses.audio.RecordingFileManager.RecordingInfo
import com.vibeqwen.glasses.ui.components.WaveformBar
import com.vibeqwen.glasses.util.TimeFormat

/**
 * 播放器底部弹层：变速(0.5-2.0x) / ±10s 跳转 / 循环 / 波形进度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    info: RecordingInfo,
    onDismiss: () -> Unit,
    vm: PlayerViewModel = viewModel(factory = PlayerViewModelFactory(info)),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var draggingPosition by remember { mutableStateOf<Float?>(null) }

    val displayPos = draggingPosition?.toLong() ?: ui.positionMs
    val totalDur = ui.durationMs.coerceAtLeast(1L)
    val progress = (displayPos.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        info.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${TimeFormat.size(info.sizeBytes)} · 16kHz WAV PCM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 动态进度波形图
            WaveformBar(
                values = ui.peaks.toList(),
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
            )

            // 进度拖动条
            Slider(
                value = (draggingPosition ?: ui.positionMs.toFloat()).coerceIn(0f, totalDur.toFloat()),
                onValueChange = { draggingPosition = it },
                onValueChangeFinished = {
                    draggingPosition?.let { vm.seekTo(it.toLong()) }
                    draggingPosition = null
                },
                valueRange = 0f..totalDur.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )

            // 时间显示
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    TimeFormat.clock(displayPos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    TimeFormat.clock(ui.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // 主播放控制栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 循环切换
                IconButton(onClick = { vm.toggleLoop() }) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = "循环",
                        tint = if (ui.loop) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 快退 10 秒
                FilledIconButton(
                    onClick = { vm.skip(-10_000) },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "后退10秒")
                }

                // 核心播放 / 暂停按钮（大圆高亮）
                FilledIconButton(
                    onClick = { vm.playPause() },
                    modifier = Modifier.width(64.dp).height(64.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                ) {
                    Icon(
                        if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        modifier = Modifier.width(36.dp).height(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                // 快进 10 秒
                FilledIconButton(
                    onClick = { vm.skip(10_000) },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Icon(Icons.Filled.FastForward, contentDescription = "前进10秒")
                }

                // 倍速切换芯片
                OutlinedButton(
                    onClick = { vm.cycleSpeed() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${ui.speed}x".replace(".0x", "x"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            ui.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 为 PlayerSheet 构造带参数的 ViewModel */
internal class PlayerViewModelFactory(private val info: RecordingInfo) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        PlayerViewModel(info) as T
}