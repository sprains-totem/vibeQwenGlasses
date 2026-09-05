package com.vibeqwen.glasses.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeqwen.glasses.audio.GlobalAudioPlayer
import com.vibeqwen.glasses.util.TimeFormat

/**
 * 全局底部常驻 Mini 播放条：即使关闭了 PlayerSheet 弹层，也能随时恢复、暂停、查看进度。
 */
@Composable
fun MiniPlayerBar(modifier: Modifier = Modifier) {
    val state by GlobalAudioPlayer.state.collectAsStateWithLifecycle()
    val cur = state.current ?: return

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

    val totalDur = state.durationMs.coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { GlobalAudioPlayer.openSheet() },
        ) {
            Column(Modifier.fillMaxWidth()) {
                // 顶部细进度指示条
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // 左侧图标
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // 标题和时间
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = friendlyTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${TimeFormat.clock(state.positionMs)} / ${TimeFormat.clock(state.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 播放/暂停按钮
                    FilledIconButton(
                        onClick = { GlobalAudioPlayer.togglePlayPause() },
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // 关闭播放器按钮
                    IconButton(
                        onClick = { GlobalAudioPlayer.closePlayer() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
