package com.rtk.telegrammanager.device

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import kotlin.math.roundToInt

class DeviceInfo(private val context: Context) {

    fun deviceText(): String {

        return """
            📱 RTK DEVICE

            Model: ${Build.MODEL}
            Manufacturer: ${Build.MANUFACTURER}
            Brand: ${Build.BRAND}

            Android: ${Build.VERSION.RELEASE}
            SDK: ${Build.VERSION.SDK_INT}

            Product: ${Build.PRODUCT}
            Device: ${Build.DEVICE}
        """.trimIndent()
    }

    fun batteryText(): String {

        val manager =
            context.getSystemService(Context.BATTERY_SERVICE)
                    as BatteryManager

        val level = manager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )

        val charging = manager.isCharging()

        return """
            🔋 BATTERY

            Level: $level%
            Charging: ${if (charging) "Yes" else "No"}
        """.trimIndent()
    }

    fun storageText(): String {

        val stat = StatFs(context.filesDir.path)

        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = total - free

        fun gb(value: Long): String {
            return "%.2f GB".format(
                value / 1024.0 / 1024.0 / 1024.0
            )
        }

        val usedPercent =
            (used.toDouble() / total * 100).roundToInt()

        return """
            💾 STORAGE

            Total: ${gb(total)}
            Used: ${gb(used)}
            Free: ${gb(free)}
            Usage: $usedPercent%
        """.trimIndent()
    }
}
