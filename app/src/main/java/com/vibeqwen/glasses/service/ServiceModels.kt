package com.vibeqwen.glasses.service

import kotlinx.coroutines.flow.MutableStateFlow

/** 连接状态（与录音状态分离：录音用 recording 标志） */
enum class ConnectionState { DISCONNECTED, CONNECTING, HANDSHAKING, READY, ERROR }

/** 服务对外 UI 状态（Compose 直接收集） */
data class ServiceUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val deviceMac: String? = null,
    val message: String? = null,
    val recording: Boolean = false,
    val recordingSeconds: Long = 0,
    val db: Float = -100f,
    val frames: Long = 0,
    val lastError: String? = null,
    val battery: Int? = null,
)

/** 服务状态总线（单例，Activity/ViewModel 直接观察） */
object GlassesBus {
    val uiState = MutableStateFlow(ServiceUiState())
    val waveform = MutableStateFlow<List<Float>>(emptyList())
}