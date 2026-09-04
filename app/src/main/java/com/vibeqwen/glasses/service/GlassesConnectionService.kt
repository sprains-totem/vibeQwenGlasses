package com.vibeqwen.glasses.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vibeqwen.glasses.MainActivity
import com.vibeqwen.glasses.R
import com.vibeqwen.glasses.audio.AudioPipeline
import com.vibeqwen.glasses.audio.RecordingFileManager
import com.vibeqwen.glasses.bluetooth.ClassicBtTransport
import com.vibeqwen.glasses.bluetooth.DeviceScanner
import com.vibeqwen.glasses.protocol.EventKind
import com.vibeqwen.glasses.protocol.JsonStreamAssembler
import com.vibeqwen.glasses.protocol.QwenCommands
import com.vibeqwen.glasses.protocol.QwenEvents
import com.vibeqwen.glasses.protocol.QwenFrameParser
import com.vibeqwen.glasses.protocol.QwenFramer
import com.vibeqwen.glasses.protocol.QwenHandshake
import com.vibeqwen.glasses.protocol.QwenConstants
import com.vibeqwen.glasses.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台服务：持有蓝牙连接 + 协议会话 + 音频管线。
 *
 * - 连接生命周期：CONNECTING → HANDSHAKING → READY（握手状态机）
 * - 录音生命周期：startRecording → 3 条 JSON 指令 → 眼镜回推音频帧 → 收帧写盘
 * - 蓝牙断开：自动停止录音并保存，状态回 DISCONNECTED（支持重新连接）
 * - WakeLock + 前台通知保活，录音期间通知显示时长
 *
 * UI 通过 companion 的 startAction(...) 驱动，通过 GlassesBus 观察状态。
 */
class GlassesConnectionService : Service() {

    companion object {
        private const val TAG = "GlassesService"
        const val CHANNEL_ID = "vqg_service"
        const val NOTIFICATION_ID = 1001

        const val ACTION_CONNECT = "com.vibeqwen.glasses.action.CONNECT"
        const val ACTION_DISCONNECT = "com.vibeqwen.glasses.action.DISCONNECT"
        const val ACTION_START_RECORD = "com.vibeqwen.glasses.action.START_RECORD"
        const val ACTION_STOP_RECORD = "com.vibeqwen.glasses.action.STOP_RECORD"
        const val EXTRA_MAC = "mac"

        /** 当前服务实例（作用域内单例，供直接调用） */
        @Volatile
        var instance: GlassesConnectionService? = null

        /** 眼镜主动推流（眼镜端触控发起的录音）时自动开始录制 */
        const val AUTO_CAPTURE = true

        fun startAction(context: Context, action: String, mac: String? = null) {
            val intent = Intent(context, GlassesConnectionService::class.java).setAction(action)
            if (mac != null) intent.putExtra(EXTRA_MAC, mac)
            ContextCompat.startForegroundService(context, intent)
        }

        fun connect(context: Context, mac: String) = startAction(context, ACTION_CONNECT, mac)
        fun disconnect(context: Context) = startAction(context, ACTION_DISCONNECT)
        fun startRecord(context: Context) = startAction(context, ACTION_START_RECORD)
        fun stopRecord(context: Context) = startAction(context, ACTION_STOP_RECORD)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var transport: ClassicBtTransport? = null
    private var handshake: QwenHandshake? = null
    private var pipeline: AudioPipeline? = null
    private var debugBridge: com.vibeqwen.glasses.debug.DebugBridge? = null
    private var frameParser = QwenFrameParser()
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null
    private var recording = false
    private var recordStartMs = 0L

    fun transport(): ClassicBtTransport? = transport

    private val localBinder = object : Binder() {
        fun service(): GlassesConnectionService = this@GlassesConnectionService
    }

    /** 控制与数据通道解复用器：处理分包/粘包并重组 GCSP 帧 */
    private val demuxer = com.vibeqwen.glasses.protocol.GcspFrameReassembler(
        onJson = { handleControlJson(it) },
        onGmaCommand = { cid, bytes -> handleGmaCommand(cid, bytes) },
        onAudioFrame = { handleRawBytes(it) },
        onGcspControl = { handleGcspControl(it) },
    )

    private fun handleGmaCommand(cid: Int, bytes: ByteArray) {
        val ack = com.vibeqwen.glasses.protocol.GmaProtocolHandler.handleIncomingBytes(bytes)
        if (ack != null) {
            com.vibeqwen.glasses.util.LogCollector.h("←自动回复眼镜 GMA ACK (" + ack.size + "B)")
            transport?.write(ack)
        }
    }

    private fun handleGcspControl(bytes: ByteArray) {
        val ack = com.vibeqwen.glasses.protocol.GmaProtocolHandler.handleIncomingBytes(bytes)
        if (ack != null) {
            com.vibeqwen.glasses.util.LogCollector.h("←回复 GCSP 控制帧 (" + ack.size + "B)")
            transport?.write(ack)
        }
    }

    private val transportListener = object : ClassicBtTransport.Listener {
        override fun onControlData(bytes: ByteArray) = demuxer.feed(bytes)
        override fun onAudioData(bytes: ByteArray) = handleAudioBytes(bytes)
        override fun onConnected() { /* 连接成功，进入握手 */ }
        override fun onError(message: String) {
            Log.e(TAG, "传输错误: $message")
            publish { it.copy(connection = ConnectionState.ERROR, lastError = message) }
        }
        override fun onDisconnected() = handleDisconnect()
    }

    // ── Service 生命周期 ──

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.vibeqwen.glasses.util.LogCollector.init(applicationContext)
        createNotificationChannel()
        acquireWakeLock()
        debugBridge = com.vibeqwen.glasses.debug.DebugBridge(this, this, scope).also { it.register() }
    }

    override fun onDestroy() {
        debugBridge?.unregister()
        debugBridge = null
        instance = null
        disconnectAllInternal()
        releaseWakeLock()
        scope.cancelScope()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val mac = intent.getStringExtra(EXTRA_MAC) ?: DeviceScanner.findGlasses(this)?.mac
                startForegroundCompat()
                if (mac != null) connectTo(mac) else {
                    publish { it.copy(connection = ConnectionState.ERROR, lastError = "未找到眼镜设备") }
                }
            }
            ACTION_DISCONNECT -> disconnectAll()
            ACTION_START_RECORD -> startRecording()
            ACTION_STOP_RECORD -> stopRecording()
        }
        return START_NOT_STICKY
    }

    // ── 连接 ──

    private fun connectTo(mac: String) {
        val device = (getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)
            ?.adapter?.getRemoteDevice(mac)
        if (device == null) {
            publish { it.copy(connection = ConnectionState.ERROR, lastError = "无法解析设备 $mac") }
            return
        }
        publish {
            it.copy(
                connection = ConnectionState.CONNECTING, deviceMac = mac,
                deviceName = device.name ?: mac, lastError = null, message = "正在连接…",
            )
        }
        updateNotification()
        com.vibeqwen.glasses.util.LogCollector.c("开始连接 ${device.name} ($mac)")
        scope.launch {
            val t = ClassicBtTransport(device)
            val ok = t.connect(transportListener)
            if (ok) {
                com.vibeqwen.glasses.util.LogCollector.c("控制通道 (L2CAP PSM 130) 已连接")
                transport = t
                // 并行建立经典蓝牙 RFCOMM Channel 16 高清音频推流通道
                scope.launch {
                    t.openAudioChannel(transportListener)
                }
                startHandshake()
            } else {
                com.vibeqwen.glasses.util.LogCollector.e("传输层连接失败")
            }
        }
    }

    private fun startHandshake() {
        publish {
            it.copy(connection = ConnectionState.HANDSHAKING, message = "正在与眼镜握手…")
        }
        updateNotification()
        com.vibeqwen.glasses.protocol.QwenFramer.resetSeq()
        handshake = null

        scope.launch {
            // 1. 发送 GCSP 版本协商请求 (08 00 00 00 05 47 43 00 00 01)
            val negReq = com.vibeqwen.glasses.protocol.QwenFramer.versionNegFrame(1)
            com.vibeqwen.glasses.util.LogCollector.h("发送 GCSP 版本协商请求: " + negReq.joinToString("") { "%02X".format(it) })
            transport?.write(negReq)
            delay(200)

            // 2. 注册鉴权成功回调
            com.vibeqwen.glasses.protocol.GmaProtocolHandler.onAuthSuccess = {
                scope.launch {
                    com.vibeqwen.glasses.util.LogCollector.c("鉴权成功，发送空 node 初始化与 CBOR 系统信息...")
                    transport?.write(com.vibeqwen.glasses.protocol.QwenFramer.emptyNodeInitFrame())
                    delay(100)
                    val cbor = com.vibeqwen.glasses.protocol.QwenFramer.cborSystemInfoFrame()
                    transport?.write(cbor)
                    delay(150)
                    startJsonHandshake()
                }
            }

            // 3. 发送 GMA 快速鉴权挑战帧 (0x10)
            val challenge = com.vibeqwen.glasses.protocol.QwenFramer.fastAuthChallenge()
            com.vibeqwen.glasses.util.LogCollector.h("发送 GMA 快速鉴权挑战 (0x10): " + challenge.joinToString("") { "%02X".format(it) })
            transport?.write(challenge)

            // 4. 超时安全保护：若 2.5 秒内未完成快速鉴权（如老固件），直接启动 JSON 握手
            delay(2500)
            if (handshake == null) {
                com.vibeqwen.glasses.util.LogCollector.h("未收到 0x13 鉴权成功通知，直接启动 JSON 握手")
                startJsonHandshake()
            }
        }
    }

    private fun startJsonHandshake() {
        if (handshake != null) return
        handshake = QwenHandshake(
            scope = scope,
            send = { text ->
                com.vibeqwen.glasses.util.LogCollector.h("←发送(GCSP帧): ${text.take(120)}")
                transport?.write(com.vibeqwen.glasses.protocol.QwenFramer.wrapJson(text))
            },
        ).also { h ->
            h.onReady = {
                com.vibeqwen.glasses.util.LogCollector.c("握手完成 → READY")
                publish { st -> st.copy(connection = ConnectionState.READY, message = "已就绪，可开始录音") }
                updateNotification()
            }
            h.onError = { err ->
                com.vibeqwen.glasses.util.LogCollector.e("握手失败: $err")
                publish {
                    it.copy(connection = ConnectionState.ERROR, lastError = err, message = "握手失败：$err")
                }
                updateNotification()
            }
            h.start()
        }
    }

    // ── 下行数据分发 ──

    private fun handleControlJson(text: String) {
        com.vibeqwen.glasses.util.LogCollector.p("收到JSON: ${text.take(120)}")
        // 喂握手状态机（驱动 READY）
        handshake?.onGlassesEvent(text)
        when (val ev = QwenEvents.parse(text).kind) {
            EventKind.RECORD_START -> Log.i(TAG, "眼镜事件: record_start")
            EventKind.RECORD_END -> Log.i(TAG, "眼镜事件: record_end")
            EventKind.RECORD_STATUS -> {
                val status = QwenEvents.parse(text)
                Log.i(TAG, "眼镜事件: AudioRecording status=${status.recordStatus} stop=${status.reasonStop}")
                // 眼镜侧结束录音（reasonStop=KEY/CLOUD）：本地自动封口保存
                if (status.recordStatus == "Exited" && recording) {
                    finalizeRecording("眼镜侧结束录音")
                }
            }
            EventKind.RECORD_TELEMETRY -> Unit
            EventKind.HEARTBEAT -> publishHeartbeat()
            else -> Unit
        }
    }

    private fun handleAudioBytes(bytes: ByteArray) {
        // HFP AT 协商文本过滤（抓包证实音频通道早期是 AT 命令流）
        val text = String(bytes, Charsets.UTF_8).trimStart()
        if (text.startsWith("AT") || text.startsWith("\r\nAT") || text.startsWith("+")) {
            Log.d(TAG, "HFP AT: ${text.take(64)}")
            return
        }
        handleRawBytes(bytes)
    }

    private fun handleRawBytes(bytes: ByteArray) {
        com.vibeqwen.glasses.util.LogCollector.log("PROTO", "收到非JSON原始字节: ${bytes.size}B " + bytes.take(24).joinToString("") { "%02X".format(it) })

        // 1. 检查并处理 GMA / GCSP 协议帧，如果需要自动应答则回包
        val ack = com.vibeqwen.glasses.protocol.GmaProtocolHandler.handleIncomingBytes(bytes)
        if (ack != null) {
            com.vibeqwen.glasses.util.LogCollector.h("←自动回复眼镜 GMA ACK (" + ack.size + "B)")
            transport?.write(ack)
        }

        // 2. 检查并提取音频帧
        val frames = frameParser.feed(bytes)
        if (frames.isEmpty()) return
        for (frame in frames) {
            // 眼镜主动推流（已 READY 且未在录）：自动开始录制
            if (!recording && AUTO_CAPTURE && isReady()) {
                startRecording(auto = true)
            }
            pipeline?.writeFrame(frame.pcm)
            publish { it.copy(frames = it.frames + 1) }
        }
    }

    // ── 录音控制 ──

    private fun startRecording(auto: Boolean = false) {
        if (recording) return
        recording = true
        recordStartMs = System.currentTimeMillis()

        frameParser.reset()
        pipeline = AudioPipeline(RecordingFileManager.recordingsDir(this)).also { p ->
            p.levelCallback = { level ->
                publish { it.copy(db = level.db) }
                pushWaveform(level.amplitude01)
            }
            p.watchdogCallback = { idleMs ->
                Log.w(TAG, "无数据看门狗触发: ${idleMs}ms 无帧")
                if (recording) finalizeRecording("数据中断 $idleMs ms，自动保存")
            }
            p.start(recordStartMs)
        }

        // 同步下发官方 3 条开始录音指令（触发眼镜硬件启动麦克风推流）
        val cmds = QwenCommands.startRecord()
        scope.launch {
            for (c in cmds) {
                com.vibeqwen.glasses.util.LogCollector.r("←下发录音指令: $c")
                transport?.write(com.vibeqwen.glasses.protocol.QwenFramer.wrapJson(c))
                delay(150)
            }
        }

        publish {
            it.copy(recording = true, recordingSeconds = 0, message = if (auto) "眼镜端开始录音（自动采集）" else "录音中…")
        }
        updateNotification()
        startTicker()
    }

    private fun stopRecording() {
        if (!recording) return
        finalizeRecording("用户停止")
        // 下发停止指令（官方 10 字节私有帧封装）
        val cmds = QwenCommands.stopRecord()
        scope.launch {
            for (c in cmds) {
                com.vibeqwen.glasses.util.LogCollector.r("←下发停止指令: $c")
                transport?.write(com.vibeqwen.glasses.protocol.QwenFramer.wrapJson(c))
                delay(120)
            }
        }
    }

    /** 封口并保存当前录音 */
    private fun finalizeRecording(reason: String) {
        if (!recording) return
        recording = false
        tickerJob?.cancel()
        tickerJob = null
        pipeline?.stop()
        pipeline = null
        publish {
            it.copy(
                recording = false,
                recordingSeconds = 0,
                db = -100f,
                message = "录音已保存（$reason）",
            )
        }
        updateNotification()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                if (recording) {
                    val sec = (System.currentTimeMillis() - recordStartMs) / 1000
                    publish { it.copy(recordingSeconds = sec) }
                    updateNotification()
                }
                delay(1000)
            }
        }
    }

    // ── 断开与清理 ──

    private fun handleDisconnect() {
        if (recording) finalizeRecording("蓝牙已断开，自动保存")
        handshake?.cancel()
        handshake = null
        publish {
            it.copy(
                connection = ConnectionState.DISCONNECTED, message = "连接已断开",
                recording = false, deviceMac = null,
            )
        }
        updateNotification()
    }

    private fun disconnectAll() {
        stopRecordingIfActive()
        disconnectAllInternal()
        publish { it.copy(connection = ConnectionState.DISCONNECTED, message = "已断开", lastError = null) }
        stopForegroundCompat()
    }

    private fun stopRecordingIfActive() {
        if (recording) finalizeRecording("断开前自动保存")
    }

    private fun disconnectAllInternal() {
        transport?.disconnect()
        transport = null
        handshake?.cancel()
        handshake = null
        frameParser = QwenFrameParser()
    }

    // ── 通知 ──

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startForegroundCompat() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun stopForegroundCompat() {
        val s = GlassesBus.uiState.value
        if (s.connection == ConnectionState.DISCONNECTED && !s.recording) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val s = GlassesBus.uiState.value
        val (title, text) = when {
            s.recording -> getString(R.string.app_name) to
                getString(R.string.notification_recording, TimeFormat.clock(s.recordingSeconds * 1000))
            s.connection == ConnectionState.CONNECTING -> getString(R.string.app_name) to getString(R.string.notification_connecting)
            s.connection == ConnectionState.HANDSHAKING -> getString(R.string.app_name) to getString(R.string.notification_handshaking)
            s.connection == ConnectionState.ERROR -> getString(R.string.app_name) to
                getString(R.string.notification_error, s.lastError ?: "未知错误")
            s.connection == ConnectionState.READY -> getString(R.string.app_name) to
                getString(R.string.notification_connected)
            else -> getString(R.string.app_name) to "vibeQwenGlasses"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(s.connection != ConnectionState.DISCONNECTED || s.recording)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

        if (s.recording) {
            builder.addAction(
                0, getString(R.string.notification_stop_action),
                PendingIntent.getService(
                    this, 1,
                    Intent(this, GlassesConnectionService::class.java).setAction(ACTION_STOP_RECORD),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        return builder.build()
    }

    // ── 其他 ──

    private fun isReady(): Boolean =
        GlassesBus.uiState.value.connection == ConnectionState.READY

    private fun publish(update: (ServiceUiState) -> ServiceUiState) {
        GlassesBus.uiState.value = update(GlassesBus.uiState.value)
    }

    private fun pushWaveform(amp: Float) {
        val cur = GlassesBus.waveform.value
        val next = if (cur.size >= 128) cur.drop(1) + amp else cur + amp
        GlassesBus.waveform.value = next
    }

    private fun publishHeartbeat() {
        Log.d(TAG, "眼镜心跳（连接活性正常）")
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vibeQwenGlasses:conn").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // PARTIAL_WAKE_LOCK 24h 上限
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) { }
        wakeLock = null
    }

    private fun CoroutineScope.cancelScope() {
        coroutineContext[Job]?.cancel()
    }
}