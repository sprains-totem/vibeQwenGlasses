package com.vibeqwen.glasses.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibeqwen.glasses.service.ConnectionState
import com.vibeqwen.glasses.service.GlassesBus
import com.vibeqwen.glasses.ui.components.RecordButton
import com.vibeqwen.glasses.ui.components.WaveformBar
import com.vibeqwen.glasses.ui.connect.ConnectViewModel
import com.vibeqwen.glasses.util.TimeFormat

/**
 * 录音页：大录音按钮 + 实时波形 + 计时 + 分贝。
 */
@Composable
fun RecordScreen(
    vm: RecordViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by GlassesBus.uiState.collectAsStateWithLifecycle()
    val waveform by GlassesBus.waveform.collectAsStateWithLifecycle()

    val hasPerm = ConnectViewModel.hasPermissions(context)
    val canRecord = hasPerm || state.recording

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 状态提示
        Text(
            when {
                state.recording -> "私有通道高清录音中 (16kHz PCM)"
                state.connection == ConnectionState.READY -> "就绪，点击开始录音"
                state.connection == ConnectionState.CONNECTING -> "正在连接眼镜…"
                state.connection == ConnectionState.HANDSHAKING -> "正在握手认证…"
                state.deviceMac != null -> "眼镜已连接，点击开始录音"
                else -> "准备就绪，点击开始录音"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (state.recording) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.message?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(36.dp))

        // 计时器
        Text(
            TimeFormat.clock(if (state.recording) state.recordingSeconds * 1000 else 0),
            fontSize = 44.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        // 分贝 + 帧数
        Text(
            if (state.recording) "%.1f dB".format(state.db) else "-- dB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "已采集 ${state.frames} 帧",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        // 大录音按钮
        RecordButton(
            recording = state.recording,
            enabled = canRecord,
            onClick = {
                if (state.recording) vm.stopRecording(context) else vm.startRecording(context)
            },
        )

        Spacer(Modifier.height(40.dp))

        // 实时波形
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.width(340.dp),
        ) {
            WaveformBar(
                values = waveform,
                progress = 1.0f,
                modifier = Modifier
                    .width(340.dp)
                    .height(110.dp)
                    .padding(10.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            if (state.recording) "眼镜端收到" else "波形将在录音期间实时绘制",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}