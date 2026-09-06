package com.vibeqwen.glasses.debug

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.vibeqwen.glasses.protocol.QwenConstants
import com.vibeqwen.glasses.protocol.QwenFramer
import com.vibeqwen.glasses.service.GlassesConnectionService
import com.vibeqwen.glasses.util.LogCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 动态报文调试与注入引擎 (Live Debug Bridge)：
 * 通过 ADB 广播实现毫秒级动态报文注入、参数调整与通道监听，彻底告别频繁改代码重编。
 *
 * 支持指令 (通过 adb shell am broadcast):
 * 1. 发送原始 Hex 报文:
 *    am broadcast -a com.vibeqwen.glasses.DEBUG_SEND_HEX --es hex "08000000054743000102"
 * 2. 自动 GCSP 封装下发 JSON 或 Hex:
 *    am broadcast -a com.vibeqwen.glasses.DEBUG_SEND_GCSP --es json '{"device":[]}' --ei cid 1
 * 3. 动态 Step 4 鉴权注入:
 *    am broadcast -a com.vibeqwen.glasses.DEBUG_AUTH --ei productId 8518 --es randomA "auto"
 * 4. 开启二级 RFCOMM 服务端监听 (等待眼镜主动连入):
 *    am broadcast -a com.vibeqwen.glasses.DEBUG_LISTEN_RFCOMM --es uuid "D5A74C04-894A-4E70-C2AE-0BDC687904FE"
 * 5. 动态连接指定 PSM (L2CAP) 或 UUID (RFCOMM):
 *    am broadcast -a com.vibeqwen.glasses.DEBUG_CONNECT --es mac "AA:BB:CC:DD:EE:FF" --ei psm 130
 */
class DebugBridge(
    private val context: Context,
    private val service: GlassesConnectionService,
    private val scope: CoroutineScope
) {
    private val tag = "DebugBridge"

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptedSocket: BluetoothSocket? = null
    private var customClientSocket: BluetoothSocket? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            LogCollector.log("DEBUG", "收到 ADB 调试广播: $action")
            when (action) {
                ACTION_SEND_HEX -> handleSendHex(intent)
                ACTION_SEND_GCSP -> handleSendGcsp(intent)
                ACTION_AUTH -> handleAuth(intent)
                ACTION_LISTEN_RFCOMM -> handleListenRfcomm(intent)
                ACTION_CONNECT -> handleConnect(intent)
                ACTION_STATUS -> handleStatus()
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_SEND_HEX)
            addAction(ACTION_SEND_GCSP)
            addAction(ACTION_AUTH)
            addAction(ACTION_LISTEN_RFCOMM)
            addAction(ACTION_CONNECT)
            addAction(ACTION_STATUS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        LogCollector.log("DEBUG", "Live Debug Bridge 已就绪，支持 adb 动态调试")
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
        closeSockets()
    }

    private fun handleSendHex(intent: Intent) {
        val hex = intent.getStringExtra("hex")?.replace(" ", "") ?: return
        val channel = intent.getIntExtra("channel", 0) // 0 = default service transport, 1 = accepted server socket, 2 = custom client socket
        try {
            val bytes = hexToBytes(hex)
            LogCollector.log("DEBUG", "→ 发送原始 HEX (${bytes.size}B, 通道 $channel): $hex")
            when (channel) {
                0 -> service.transport()?.write(bytes)
                1 -> acceptedSocket?.outputStream?.let { it.write(bytes); it.flush() }
                2 -> customClientSocket?.outputStream?.let { it.write(bytes); it.flush() }
            }
        } catch (e: Exception) {
            LogCollector.e("DEBUG 发送 HEX 失败: ${e.message}")
        }
    }

    private fun handleSendGcsp(intent: Intent) {
        val json = intent.getStringExtra("json")
        val hex = intent.getStringExtra("hex")
        val cid = intent.getIntExtra("cid", 1)
        val msgType = intent.getIntExtra("msgType", 1)
        val crc = intent.getBooleanExtra("crc", true)

        try {
            val payload = when {
                json != null -> json.toByteArray(Charsets.UTF_8)
                hex != null -> hexToBytes(hex)
                else -> return
            }
            val frame = QwenFramer.wrap(payload, msgType = msgType, cid = cid, appendCrc = crc)
            val frameHex = bytesToHex(frame)
            LogCollector.log("DEBUG", "→ 发送 GCSP 封装帧 (CID=$cid, Len=${frame.size}B): $frameHex")
            service.transport()?.write(frame)
        } catch (e: Exception) {
            LogCollector.e("DEBUG 发送 GCSP 失败: ${e.message}")
        }
    }

    private fun handleAuth(intent: Intent) {
        scope.launch(Dispatchers.IO) {
            val productId = intent.getIntExtra("productId", 8518)
            val randomAStr = intent.getStringExtra("randomA")
            val bleKeyHex = intent.getStringExtra("bleKey") ?: "00000000000000000000000000000000"

            val randomA = if (randomAStr == null || randomAStr == "auto") {
                ByteArray(16).also { SecureRandom().nextBytes(it) }
            } else {
                hexToBytes(randomAStr)
            }

            LogCollector.log("AUTH", "发起 Step 4 鉴权: productId=$productId, randomA=${bytesToHex(randomA)}, key=$bleKeyHex")

            // 构造 0x14 鉴权请求载荷: [CmdId 2B: 0x0014] [NameSpace 1B: 0x01] [MsgId 1B: 0x01] [PayloadLen 2B] [ProductId 4B] [RandomA 16B]
            val authPayload = ByteArray(2 + 1 + 1 + 2 + 4 + 16).apply {
                this[0] = 0x14
                this[1] = 0x00
                this[2] = 0x01 // GMA Auth Namespace
                this[3] = 0x01 // MsgId
                this[4] = 20   // ProductId(4) + RandomA(16)
                this[5] = 0x00
                // ProductId (LE)
                this[6] = (productId and 0xFF).toByte()
                this[7] = ((productId shr 8) and 0xFF).toByte()
                this[8] = ((productId shr 16) and 0xFF).toByte()
                this[9] = ((productId shr 24) and 0xFF).toByte()
                // RandomA
                System.arraycopy(randomA, 0, this, 10, 16)
            }

            val gcspFrame = QwenFramer.wrap(authPayload, msgType = 1, cid = 1, appendCrc = true)
            LogCollector.log("AUTH", "→ 下发 0x14 鉴权包 (${gcspFrame.size}B): ${bytesToHex(gcspFrame)}")
            service.transport()?.write(gcspFrame)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleListenRfcomm(intent: Intent) {
        val uuidStr = intent.getStringExtra("uuid") ?: QwenConstants.UUID_OFFICIAL_BIND.toString()
        val insecure = intent.getBooleanExtra("insecure", true)
        val name = intent.getStringExtra("name") ?: "QwenSecondaryAudio"

        scope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
                val uuid = UUID.fromString(uuidStr)

                LogCollector.log("RFCOMM_SRV", "开启服务端监听: $name (UUID: $uuid, insecure=$insecure)")
                val server = if (insecure) {
                    adapter.listenUsingInsecureRfcommWithServiceRecord(name, uuid)
                } else {
                    adapter.listenUsingRfcommWithServiceRecord(name, uuid)
                }
                serverSocket = server

                // 阻塞等待眼镜主动连入
                LogCollector.log("RFCOMM_SRV", "正在等待眼镜主动连接...")
                val socket = server.accept()
                acceptedSocket = socket
                LogCollector.log("RFCOMM_SRV", "★ 眼镜已成功接入服务端! Remote: ${socket.remoteDevice.address}")

                // 开启读取循环
                val input = socket.inputStream
                val buf = ByteArray(4096)
                while (socket.isConnected) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val chunk = buf.copyOf(n)
                    val hex = bytesToHex(chunk.take(32).toByteArray())
                    LogCollector.log("RFCOMM_SRV", "收到眼镜推流数据 (${n}B): $hex")
                }
            } catch (e: Exception) {
                LogCollector.log("RFCOMM_SRV", "服务端监听异常/退出: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleConnect(intent: Intent) {
        val mac = intent.getStringExtra("mac")
            ?: com.vibeqwen.glasses.bluetooth.DeviceScanner.findGlasses(context)?.mac
            ?: return
        val psm = intent.getIntExtra("psm", -1)
        val uuidStr = intent.getStringExtra("uuid")

        scope.launch(Dispatchers.IO) {
            try {
                customClientSocket?.close()
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
                val device = adapter.getRemoteDevice(mac)

                val socket = when {
                    psm > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        LogCollector.log("DEBUG_CONN", "动态发起 L2CAP 连接 -> $mac (PSM=$psm)")
                        device.createL2capChannel(psm)
                    }
                    uuidStr != null -> {
                        val u = UUID.fromString(uuidStr)
                        LogCollector.log("DEBUG_CONN", "动态发起 RFCOMM 连接 -> $mac (UUID=$u)")
                        device.createInsecureRfcommSocketToServiceRecord(u)
                    }
                    else -> return@launch
                }
                socket.connect()
                customClientSocket = socket
                LogCollector.log("DEBUG_CONN", "★ 自定义通道连接成功!")

                val input = socket.inputStream
                val buf = ByteArray(4096)
                while (socket.isConnected) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val chunk = buf.copyOf(n)
                    LogCollector.log("DEBUG_CONN", "收到回包 (${n}B): " + bytesToHex(chunk.take(32).toByteArray()))
                }
            } catch (e: Exception) {
                LogCollector.log("DEBUG_CONN", "自定义通道连接异常: ${e.message}")
            }
        }
    }

    private fun handleStatus() {
        val transport = service.transport()
        val isConn = transport?.isConnected == true
        LogCollector.log("DEBUG_STATUS", "Service 连接状态: $isConn, ServerSocket活跃: ${acceptedSocket?.isConnected == true}")
    }

    private fun closeSockets() {
        try { serverSocket?.close() } catch (_: Exception) {}
        try { acceptedSocket?.close() } catch (_: Exception) {}
        try { customClientSocket?.close() } catch (_: Exception) {}
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val byteStr = clean.substring(i * 2, i * 2 + 2)
            result[i] = byteStr.toInt(16).toByte()
        }
        return result
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }

    companion object {
        const val ACTION_SEND_HEX = "com.vibeqwen.glasses.DEBUG_SEND_HEX"
        const val ACTION_SEND_GCSP = "com.vibeqwen.glasses.DEBUG_SEND_GCSP"
        const val ACTION_AUTH = "com.vibeqwen.glasses.DEBUG_AUTH"
        const val ACTION_LISTEN_RFCOMM = "com.vibeqwen.glasses.DEBUG_LISTEN_RFCOMM"
        const val ACTION_CONNECT = "com.vibeqwen.glasses.DEBUG_CONNECT"
        const val ACTION_STATUS = "com.vibeqwen.glasses.DEBUG_STATUS"
    }
}