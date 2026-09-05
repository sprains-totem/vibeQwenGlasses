package com.vibeqwen.glasses.ui.recordings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibeqwen.glasses.audio.RecordingFileManager.RecordingInfo
import com.vibeqwen.glasses.ui.player.PlayerSheet
import com.vibeqwen.glasses.util.TimeFormat

/**
 * 录音库：录音列表（时长/大小）、删除、分享、播放。
 */
@Composable
fun RecordingsScreen(
    vm: RecordingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf<RecordingInfo?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh(context) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "录音库",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
            if (recordings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有录音\n连接眼镜完成一次录音后，文件会出现在这里",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    items(recordings, key = { it.file.absolutePath }) { r ->
                        RecordingRow(
                            info = r,
                            onPlay = { vm.play(r) },
                            onDelete = { showDeleteConfirm = r },
                            onShare = { vm.share(context, r) },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // 播放器弹层
    playing?.let { info ->
        PlayerSheet(info = info, onDismiss = { vm.dismissPlayer() })
    }

    // 删除确认
    showDeleteConfirm?.let { info ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除录音") },
            text = { Text("确定删除「${info.displayName}」吗？此操作不可恢复。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.delete(context, info)
                    showDeleteConfirm = null
                }) { Text("删除") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RecordingRow(
    info: RecordingInfo,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    // 将文件名如 rec_20260905_181832.wav 转换为人性化标题
    val friendlyTitle = remember(info.displayName) {
        val regex = Regex("""rec_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})""")
        val m = regex.find(info.displayName)
        if (m != null) {
            val (_, mth, day, hour, min) = m.destructured
            "${mth}月${day}日 ${hour}:${min} 的现场录音"
        } else {
            info.displayName.removeSuffix(".wav")
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            Modifier
                .clickable(onClick = onPlay)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp).height(28.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    friendlyTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${TimeFormat.clock(info.durationMs)} · ${TimeFormat.size(info.sizeBytes)} · ${TimeFormat.date(info.modifiedMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "分享",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}