package com.vibeqwen.glasses.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeqwen.glasses.util.LogCollector
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    val context = LocalContext.current
    var logList by remember { mutableStateOf(LogCollector.dump()) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // 监听新日志
    DisposableEffect(Unit) {
        val listener = LogCollector.LogListener { newLine ->
            logList = logList + newLine
        }
        LogCollector.addListener(listener)
        onDispose {
            LogCollector.removeListener(listener)
        }
    }

    // 过滤日志
    val filteredLogs = remember(logList, selectedFilter) {
        if (selectedFilter == "ALL") {
            logList
        } else {
            logList.filter { it.contains("[$selectedFilter]") }
        }
    }

    // 自动滚动到底部
    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部工具栏与操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "运行日志 (${filteredLogs.size} 行)",
                style = MaterialTheme.typography.titleMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 自动滚动开关
                IconButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "自动滚动",
                        tint = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 复制全部
                IconButton(onClick = {
                    val allText = filteredLogs.joinToString("\n")
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("vibeLogs", allText))
                    Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制日志")
                }

                // 清空
                IconButton(onClick = {
                    LogCollector.clear()
                    logList = emptyList()
                }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "清空日志")
                }
            }
        }

        // 标签筛选器
        val tags = listOf("ALL", "CONN", "HANDSHAKE", "RECORD", "PROTO", "ERROR", "DEBUG", "IO")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach { tag ->
                FilterChip(
                    selected = selectedFilter == tag,
                    onClick = { selectedFilter = tag },
                    label = { Text(tag) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

        // 日志列表
        SelectionContainer(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(filteredLogs) { line ->
                    LogItem(line)
                }
            }
        }
    }
}

@Composable
private fun LogItem(line: String) {
    val color = when {
        line.contains("[ERROR]") -> Color(0xFFEF5350)
        line.contains("[RECORD]") -> Color(0xFF66BB6A)
        line.contains("[CONN]") -> Color(0xFF42A5F5)
        line.contains("[HANDSHAKE]") -> Color(0xFF26C6DA)
        line.contains("[PROTO]") -> Color(0xFFAB47BC)
        line.contains("[DEBUG]") -> Color(0xFFFFA726)
        line.contains("[IO]") -> Color(0xFF78909C)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = line,
        color = color,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
