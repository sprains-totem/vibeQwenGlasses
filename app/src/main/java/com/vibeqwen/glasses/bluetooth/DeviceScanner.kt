package com.vibeqwen.glasses.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.vibeqwen.glasses.protocol.QwenConstants

/**
 * 设备扫描/过滤：列出已配对设备，按名称 / 特征过滤眼镜。
 * 本版仅使用已配对列表（无需 BLUETOOTH_SCAN 主动发现），保证权限负担最小。
 */
object DeviceScanner {

    data class GlassesDevice(
        val name: String?,
        val mac: String,
        val bonded: Boolean,
    )

    /** 眼镜名称特征（固件常带 ODM/型号字样） */
    private val nameHints = listOf("AILABS", "SG02", "Quark", "Qwen", "千问", "Glass", "BES", "bes2800")

    fun adapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    /** 列出已配对设备（API 31+ 需要 BLUETOOTH_CONNECT 权限） */
    fun listBonded(context: Context): List<GlassesDevice> {
        val adapter = adapter(context) ?: return emptyList()
        return try {
            adapter.bondedDevices
                ?.map { GlassesDevice(it.name, it.address, true) }
                ?.sortedBy { if (isLikelyGlasses(it.name, it.mac)) 0 else 1 }
                ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** 名称特征匹配判断是否为眼镜 */
    fun isLikelyGlasses(name: String?, mac: String): Boolean {
        if (name.isNullOrBlank()) return false
        return nameHints.any { name.contains(it, ignoreCase = true) }
    }

    /** 直接定位眼镜（已配对集合内匹配） */
    fun findGlasses(context: Context): GlassesDevice? =
        listBonded(context).firstOrNull { isLikelyGlasses(it.name, it.mac) }
}