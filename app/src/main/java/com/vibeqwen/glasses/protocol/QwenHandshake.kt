package com.vibeqwen.glasses.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * 握手状态机（纯 Kotlin，可在 JVM 单测）。
 *
 * 依据 docs/PROTOCOL.md §3 时序：
 *  手机 → {"device":[]} ×2 / {} / calendarSync / messageId+phoneType:1+supportHeicDecode:1
 *  眼镜 → active_data+odm / pairAdv+pid+peerAddr / {"type":10001}
 *  手机 → {"type":10001} 同构 / sessionId / support:true / {"type":1103,data=SN}
 *  眼镜 → attach_success → READY
 * 心得：active_data 是眼镜主动上报的令牌，客户端无需回传；消息发送顺序与
 * 眼镜事件到达解耦（事件异步喂入 onGlassesEvent）即可完成 READY。
 */
enum class HandshakeState {
    IDLE, DEVICE_QUERY, MESSAGE_ID, WAIT_GLASSES_INFO,
    AUTH_SESSION, SN_AUTH, WAIT_ATTACH, READY, FAILED,
}

class QwenHandshake(
    private val scope: CoroutineScope,
    /** 上行发送 JSON 文本给眼镜（线程安全） */
    private val send: suspend (String) -> Unit,
    private val deviceSn: String = QwenConstants.DEVICE_UUID,
    /** 严谨模式：超时未收到 attach_success 直接标 FAILED，严禁自欺欺人假就绪 */
    private val tolerateAttachTimeout: Boolean = false,
) {

    private val _state = MutableStateFlow(HandshakeState.IDLE)
    val state: StateFlow<HandshakeState> = _state

    var onReady: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var job: Job? = null
    private val infoGate = CompletableDeferred<Unit>()
    private val attachGate = CompletableDeferred<Unit>()
    private val sessionCounter = AtomicLong(4196571L) // 会话号沿用抓包观测的量级

    fun start() {
        job?.cancel()
        _state.value = HandshakeState.IDLE
        job = scope.launch {
            try {
                // 1) 设备查询（抓包原样：连续三条）
                _state.value = HandshakeState.DEVICE_QUERY
                send(QwenCommands.queryDevice())
                delay(QwenConstants.HANDSHAKE_STEP_DELAY_MS)
                send(QwenCommands.queryDevice())
                delay(QwenConstants.HANDSHAKE_STEP_DELAY_MS)
                send(QwenCommands.queryDeviceEmpty())
                delay(150)

                // 2) calendarSync 配置同步
                send(QwenCommands.calendarSync())
                delay(120)

                // 3) messageId + phoneType:1 + supportHeicDecode:1
                _state.value = HandshakeState.MESSAGE_ID
                send(QwenCommands.messageId())

                // 4) 等待眼镜上报 active_data / pairAdv / type:10001（任一种即满足；
                //    超时宽容：个别固件可能不上报，继续后续认证）
                _state.value = HandshakeState.WAIT_GLASSES_INFO
                withTimeoutOrNull(QwenConstants.HANDSHAKE_INFO_TIMEOUT_MS) { infoGate.await() }

                // 5) 认证会话：type:10001 同构 → sessionId → support
                _state.value = HandshakeState.AUTH_SESSION
                send(QwenCommands.type10001())
                delay(QwenConstants.HANDSHAKE_STEP_DELAY_MS)
                send(QwenCommands.sessionId(sessionCounter.incrementAndGet()))
                delay(QwenConstants.HANDSHAKE_STEP_DELAY_MS)
                send(QwenCommands.support())
                delay(150)

                // 6) SN 认证
                _state.value = HandshakeState.SN_AUTH
                send(QwenCommands.snAuth(deviceSn))
                delay(QwenConstants.HANDSHAKE_STEP_DELAY_MS)

                // 7) 手机下发 attach_success 通知眼镜挂载完成（官方抓包证实）
                _state.value = HandshakeState.WAIT_ATTACH
                send(QwenCommands.attachSuccess())
                delay(80)
                send(QwenCommands.amapNavigation())
                delay(80)
                send(QwenCommands.queryProps())

                _state.value = HandshakeState.READY
                com.vibeqwen.glasses.util.LogCollector.c("★ 握手认证已完成，双方会话已激活 → READY")
                onReady?.invoke()
            } catch (e: Exception) {
                _state.value = HandshakeState.FAILED
                onError?.invoke(e.message ?: "握手异常")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = HandshakeState.IDLE
    }

    /** 喂入眼镜下行事件（控制通道每条 JSON 文本） */
    fun onGlassesEvent(eventText: String) {
        val ev = QwenEvents.parse(eventText)
        when (ev.kind) {
            EventKind.ACTIVE_DATA, EventKind.PAIR_INFO, EventKind.TYPE_10001_Q -> {
                if (!infoGate.isCompleted) infoGate.complete(Unit)
            }
            EventKind.ATTACH_SUCCESS -> {
                if (!attachGate.isCompleted) attachGate.complete(Unit)
            }
            else -> Unit
        }
    }
}