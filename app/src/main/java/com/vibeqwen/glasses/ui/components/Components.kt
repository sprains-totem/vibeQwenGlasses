package com.vibeqwen.glasses.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 实时波形（竖条图）：输入 0..1 幅度序列，支持传入 progress 进度（0f..1f）高亮显示已播放部分。
 */
@Composable
fun WaveformBar(
    values: List<Float>,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val count = values.size
        val step = size.width / count
        val clampedProgress = progress.coerceIn(0f, 1f)
        for (i in values.indices) {
            val v = values[i].coerceIn(0.04f, 1f)
            val h = (v * size.height * 0.92f).coerceAtLeast(3f)
            val x = i * step + step * 0.18f
            val w = (step * 0.64f).coerceAtLeast(2f)
            val isPlayed = (i.toFloat() / count) <= clampedProgress
            drawRoundRect(
                color = if (isPlayed) activeColor else inactiveColor,
                topLeft = Offset(x, size.height / 2f - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}

/**
 * 大录音按钮：圆形，录音中红色渐变并内缩。
 */
@Composable
fun RecordButton(
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val gradient = if (recording) {
        Brush.linearGradient(listOf(Color(0xFFFF5C6C), Color(0xFFB3203A)))
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    }
    val ringColor = if (recording) Color(0xFFFF5C6C) else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(base)
            .border(3.dp, ringColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (recording) size * 0.62f else size * 0.78f)
                .clip(CircleShape)
                .background(gradient)
        )
    }
}