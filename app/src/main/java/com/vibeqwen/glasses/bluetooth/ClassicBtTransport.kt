package com.vibeqwen.glasses.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import com.vibeqwen.glasses.protocol.QwenConstants
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 蓝牙传输层（支持 BLE GATT 前置链路、L2CAP CoC PSM 130 与 RFCOMM 通道）。
 */
class ClassicBtTransport(
    private val device: BluetoothDevice,
    private val context: Context? = null,
    private val controlCandidates: List<UUID> = QwenConstants.DEFAULT_CONTROL_UUIDS,
    private val audioCandidates: List<UUID> = QwenConstants.DEFAULT_AUDIO_UUIDS,
) {
    interface Listener {
        /** 控制通道原始字节（JSON 为主，间或夹杂非 JSON 残余） */
        fun onControlData(bytes: ByteArray)

        /** 音频通道原始字节（HFP AT 协商 + 398B 音频帧混合流） */
        fun onAudioData(bytes: ByteArray)

        /** 控制通道建立成功 */
        fun onConnected()

        /** 任意通道错误 */
        fun onError(message: String)

        /** 连接断开（网络层触发） */
        fun onDisconnected()
    }

    private val tag = "ClassicBtTransport"

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var controlSocket: BluetoothSocket? = null

    @Volatile
    private var audioSocket: BluetoothSocket? = null

    @Volatile
    private var cancelled = false

    @Volatile
    private var listener: Listener? = null

    private var controlThread: Thread? = null
    private var audioThread: Thread? = null

    /** 控制通道是否已连接 */
    val isConnected: Boolean
        get() = controlSocket?.isConnected == true

    /** 建立控制通道（阻塞；后台线程调用） */
    fun connect(listener: Listener): Boolean {
        this.listener = listener
        cancelled = false
        // 官方 APP 用 L2CAP PSM=130（逆向确认），优先尝试；失败再走 RFCOMM 候选
        val sock = openL2capOrControl()
        if (sock == null) return false
        controlSocket = sock
        startReadLoop(sock, isAudio = false)
        listener.onConnected()
        return true
    }

    /** L2CAP(PSM=130) 优先，失败回退 RFCOMM 候选 */
    private fun openL2capOrControl(): BluetoothSocket? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 官方 APP (classes4.dex GattL2capManager): 先建 BLE GATT 连接并 discoverServices，再连 L2CAP PSM 130
            if (context != null) {
                try {
                    val gattLatch = CountDownLatch(1)
                    com.vibeqwen.glasses.util.LogCollector.c("正在建立底层 BLE GATT 连接 (TRANSPORT_LE)...")
                    val g = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        device.connectGatt(context, false, object : BluetoothGattCallback() {
                            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                                if (newState == BluetoothProfile.STATE_CONNECTED) {
                                    com.vibeqwen.glasses.util.LogCollector.c("BLE GATT 已连接，请求发现服务...")
                                    gatt.discoverServices()
                                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                    com.vibeqwen.glasses.util.LogCollector.c("BLE GATT 已断开 (status=$status)")
                                    gattLatch.countDown()
                                }
                            }

                            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                                com.vibeqwen.glasses.util.LogCollector.c("BLE GATT 服务已发现 (status=$status)")
                                for (svc in gatt.services) {
                                    for (ch in svc.characteristics) {
                                        val uuidStr = ch.uuid.toString().uppercase()
                                        if (uuidStr.contains("FED7") || uuidStr.contains("FED8") || uuidStr.contains("2A05")) {
                                            com.vibeqwen.glasses.util.LogCollector.c("  发现关键特征: $uuidStr")
                                        }
                                    }
                                }

                                // 1. 使能 AIS_NOTIFY (FED8) 与 CCCD (0x2902)
                                val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                val aisSvcUuid = UUID.fromString("0000feb3-0000-1000-8000-00805f9b34fb")
                                val aisCharUuid = UUID.fromString("0000fed7-0000-1000-8000-00805f9b34fb")
                                val aisNotifyUuid = UUID.fromString("0000fed8-0000-1000-8000-00805f9b34fb")

                                val aisSvc = gatt.getService(aisSvcUuid)
                                val notifyChar = aisSvc?.getCharacteristic(aisNotifyUuid)
                                if (notifyChar != null) {
                                    gatt.setCharacteristicNotification(notifyChar, true)
                                    val desc = notifyChar.getDescriptor(cccdUuid)
                                    if (desc != null) {
                                        com.vibeqwen.glasses.util.LogCollector.c("写入 AIS_NOTIFY (FED8) CCCD 描述符 (0x0002)...")
                                        desc.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                        gatt.writeDescriptor(desc)
                                    }
                                }

                                // 2. 尝试读取加密特征 FED7 (触发系统 HCI_LE_Start_Encryption)
                                val readChar = aisSvc?.getCharacteristic(aisCharUuid)
                                if (readChar != null) {
                                    com.vibeqwen.glasses.util.LogCollector.c("读取加密特征 FED7 以触发 BLE 链路加密...")
                                    gatt.readCharacteristic(readChar)
                                }

                                // 3. 请求 MTU 1245
                                gatt.requestMtu(1245)
                            }

                            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                                com.vibeqwen.glasses.util.LogCollector.c("BLE MTU 协商完成: mtu=$mtu, status=$status")
                                gattLatch.countDown()
                            }

                            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                                com.vibeqwen.glasses.util.LogCollector.c("GATT 描述符写入完成: ${descriptor.uuid} status=$status")
                                gattLatch.countDown()
                            }

                            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                                com.vibeqwen.glasses.util.LogCollector.c("GATT 特征读取响应: ${characteristic.uuid} status=$status")
                            }
                        }, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        device.connectGatt(context, false, object : BluetoothGattCallback() {
                            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                                if (newState == BluetoothProfile.STATE_CONNECTED) {
                                    gatt.discoverServices()
                                }
                            }
                            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                                gattLatch.countDown()
                            }
                        })
                    }
                    gatt = g
                    val ok = gattLatch.await(5, TimeUnit.SECONDS)
                    com.vibeqwen.glasses.util.LogCollector.c("GATT 准备就绪: $ok，开始建立 L2CAP PSM=130...")
                } catch (e: Exception) {
                    com.vibeqwen.glasses.util.LogCollector.e("GATT 前置连接异常: ${e.message}")
                }
            }

            try {
                // 优先使用官方 APP 使用的 Insecure L2CAP 通道 (isSecured = false)
                val s = device.createInsecureL2capChannel(QwenConstants.L2CAP_PSM)
                s.connect()
                Log.i(tag, "[control] Insecure L2CAP PSM=${QwenConstants.L2CAP_PSM} 连接成功")
                com.vibeqwen.glasses.util.LogCollector.c("Insecure L2CAP PSM=${QwenConstants.L2CAP_PSM} 连接成功")
                return s
            } catch (e: Exception) {
                Log.w(tag, "[control] Insecure L2CAP 失败: ${e.message}，尝试 Secure L2CAP")
                try {
                    val s = device.createL2capChannel(QwenConstants.L2CAP_PSM)
                    s.connect()
                    Log.i(tag, "[control] Secure L2CAP PSM=${QwenConstants.L2CAP_PSM} 连接成功")
                    com.vibeqwen.glasses.util.LogCollector.c("Secure L2CAP PSM=${QwenConstants.L2CAP_PSM} 连接成功")
                    return s
                } catch (e2: Exception) {
                    Log.w(tag, "[control] L2CAP PSM=${QwenConstants.L2CAP_PSM} 失败: ${e2.message}，回退 RFCOMM")
                    com.vibeqwen.glasses.util.LogCollector.e("L2CAP 连接失败: ${e2.message}")
                }
            }
        }
        return connectWithCandidates(controlCandidates, "control")
    }

    /** 建立音频第二通道（官方抓包证实走经典蓝牙 RFCOMM） */
    fun openAudioChannel(listener: Listener): Boolean {
        this.listener = listener
        // 1. 优先使用官方标准 RFCOMM UUID 建立连接（与官方 App 严格一致）
        val sock = connectWithCandidates(listOf(QwenConstants.UUID_GMA_RFCOMM) + audioCandidates, "audio")
            ?: tryConnectRfcommChannel(QwenConstants.RFCOMM_AUDIO_CHANNEL)
        if (sock == null) return false
        audioSocket = sock
        startReadLoop(sock, isAudio = true)
        return true
    }

    private fun tryConnectRfcommChannel(channel: Int): BluetoothSocket? {
        for (methodName in listOf("createInsecureRfcommSocket", "createRfcommSocket")) {
            if (cancelled) return null
            try {
                val method = device.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
                val s = method.invoke(device, channel) as BluetoothSocket
                s.connect()
                Log.i(tag, "[audio] $methodName($channel) 连接成功！")
                com.vibeqwen.glasses.util.LogCollector.log("CONN", "★ 经典蓝牙私有音频推流通道 (RFCOMM $channel) 建立成功！")
                return s
            } catch (e: Exception) {
                Log.w(tag, "[audio] $methodName($channel) 尝试失败: ${e.message}")
            }
        }
        return null
    }

    private fun connectWithCandidates(candidates: List<UUID>, label: String): BluetoothSocket? {
        var lastError: String? = null
        for (uuid in candidates) {
            if (cancelled) return null
            val sock = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: Exception) {
                lastError = "createRfcomm($uuid) 失败: ${e.message}"
                continue
            }
            try {
                sock.connect()
                Log.i(tag, "[$label] 连接成功 uuid=$uuid")
                return sock
            } catch (e: IOException) {
                lastError = "connect($uuid) 失败: ${e.message}"
                try { sock.close() } catch (_: IOException) { }
            } catch (e: Exception) {
                lastError = "connect($uuid) 异常: ${e.message}"
                try { sock.close() } catch (_: IOException) { }
            }
        }
        // 只有控制通道失败才上报致命错误；音频第二通道是可选通道，失败不影响主会话
        if (label == "control") {
            listener?.onError("[$label] $lastError")
        } else {
            Log.w(tag, "[$label] 可选音频通道建立跳过/失败: $lastError")
        }
        return null
    }

    /** 写控制指令（线程安全即可，调用方负责串行化） */
    fun write(bytes: ByteArray) {
        val sock = controlSocket
        if (sock == null || !sock.isConnected) return
        try {
            sock.outputStream.write(bytes)
            sock.outputStream.flush()
        } catch (e: IOException) {
            listener?.onError("写失败: ${e.message}")
            notifyDisconnected()
        }
    }

    /** 主动断开并清理资源 */
    fun disconnect() {
        cancelled = true
        tryClose(controlSocket)
        tryClose(audioSocket)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
        controlSocket = null
        audioSocket = null
    }

    private val RFCOMM_CREDIT_ACK = byteArrayOf(
        0x85.toByte(), 0xFF.toByte(), 0x01.toByte(), 0x02.toByte(), 0x6B.toByte()
    )

    private fun startReadLoop(sock: BluetoothSocket, isAudio: Boolean) {
        val label = if (isAudio) "audio" else "control"
        val thread = Thread({
            val input = try { sock.inputStream } catch (e: IOException) { return@Thread }
            val buf = ByteArray(4096)
            var frameCount = 0
            try {
                while (!cancelled && sock.isConnected) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val chunk = buf.copyOf(n)
                    com.vibeqwen.glasses.util.LogCollector.log("IO", "[$label] 收到 ${n}B: " + chunk.take(24).joinToString("") { "%02X".format(it) })
                    if (isAudio) {
                        listener?.onAudioData(chunk)
                        frameCount++
                        if (frameCount % 2 == 0) {
                            try {
                                sock.outputStream.write(RFCOMM_CREDIT_ACK)
                                sock.outputStream.flush()
                            } catch (_: Exception) {}
                        }
                    } else {
                        listener?.onControlData(chunk)
                    }
                }
            } catch (e: IOException) {
                // 正常断开 / 远端关闭
            } catch (e: Exception) {
                if (!cancelled) listener?.onError("读取异常: ${e.message}")
            } finally {
                if (!cancelled) {
                    notifyDisconnected()
                }
            }
        }, if (isAudio) "vqg-audio-reader" else "vqg-control-reader").apply {
            isDaemon = true
            start()
        }
        if (isAudio) audioThread = thread else controlThread = thread
    }

    private fun notifyDisconnected() {
        if (cancelled) return
        cancelled = true
        listener?.onDisconnected()
    }

    private fun tryClose(sock: BluetoothSocket?) {
        if (sock == null) return
        try { sock.close() } catch (_: IOException) { }
    }
}