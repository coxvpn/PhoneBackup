package app.myipwidget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** The battery's state, read from the system: power, current, voltage, level, temperature. */
object ChargeInfo {
    class Info(
        val charging: Boolean,
        val full: Boolean,
        val source: Int,          // 0 none, 1 cable (AC), 2 USB, 3 wireless
        val level: Int,           // percent
        val milliAmps: Int,       // current, always positive; 0 when unknown
        val millivolts: Int,
        val watts: Double,        // 0.0 when unknown
        val tempTenths: Int,      // tenths of a degree Celsius
        val minutesLeft: Int,     // minutes to full; -1 when unknown
    )

    fun read(context: Context): Info? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale
        val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> 3
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> 2
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> 1
            else -> 0
        }
        // BATTERY_PROPERTY_CURRENT_NOW is in microamps on most phones (a few give milliamps); the sign differs between makers
        var raw = try { manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L } catch (e: Exception) { 0L }
        if (raw == Long.MIN_VALUE) raw = 0L
        val abs = Math.abs(raw)
        val milliAmps = if (abs == 0L) 0 else if (abs > 20000L) (abs / 1000L).toInt() else abs.toInt()
        val watts = if (milliAmps > 0 && volt > 0) milliAmps / 1000.0 * volt / 1000.0 else 0.0
        val left = try {
            if (android.os.Build.VERSION.SDK_INT >= 28 && charging) {
                val ms = manager?.computeChargeTimeRemaining() ?: -1L
                if (ms > 0) (ms / 60000L).toInt() else -1
            } else -1
        } catch (e: Exception) { -1 }
        return Info(charging, full, source, level, milliAmps, volt, watts, temp, left)
    }
}
