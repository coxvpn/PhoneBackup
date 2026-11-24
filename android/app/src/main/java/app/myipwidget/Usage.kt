package app.myipwidget

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import android.text.format.Formatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Data downloaded / uploaded today over Wi-Fi and mobile data (needs "usage access", allowed once in Settings). */
object Usage {

    @Suppress("DEPRECATION")
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return try {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** Data used today: downloads and uploads added together, Wi-Fi and SIM kept apart (-1 = unknown). */
    data class Today(val wifi: Long, val mobile: Long)

    /** Bytes (down + up) of one kind of network since midnight, or -1 if it cannot be read. */
    private fun total(manager: NetworkStatsManager, type: Int, from: Long, to: Long): Long {
        return try {
            val bucket = manager.querySummaryForDevice(type, null, from, to)
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            -1L
        }
    }

    private fun query(context: Context): Today? {
        if (!hasAccess(context)) return null
        val manager = context.getSystemService(NetworkStatsManager::class.java) ?: return null
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val wifi = total(manager, ConnectivityManager.TYPE_WIFI, midnight, now)
        val mobile = total(manager, ConnectivityManager.TYPE_MOBILE, midnight, now)
        return if (wifi < 0 && mobile < 0) null else Today(wifi, mobile)
    }

    /** The apps that used the most data today: (name, bytes), biggest first. Call from a background thread. */
    fun topApps(context: Context, count: Int = 5): List<Pair<String, Long>> {
        if (!hasAccess(context)) return emptyList()
        val manager = context.getSystemService(NetworkStatsManager::class.java) ?: return emptyList()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val perUid = HashMap<Int, Long>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val stats = manager.querySummary(type, null, midnight, now)
                val bucket = android.app.usage.NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    perUid[bucket.uid] = (perUid[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
                }
                stats.close()
            } catch (e: Exception) {
                // this kind of network cannot be read
            }
        }
        val packages = context.packageManager
        val named = HashMap<String, Long>()
        for ((uid, bytes) in perUid) {
            if (bytes <= 0L) continue
            val label = try {
                val list = packages.getPackagesForUid(uid)
                if (list.isNullOrEmpty()) context.getString(R.string.top_apps_system)
                else packages.getApplicationLabel(packages.getApplicationInfo(list[0], 0)).toString()
            } catch (e: Exception) {
                context.getString(R.string.top_apps_system)
            }
            named[label] = (named[label] ?: 0L) + bytes
        }
        return named.entries.sortedByDescending { it.value }.take(count).map { Pair(it.key, it.value) }
    }

    /** The last [days] days (the oldest first, today last): Wi-Fi and SIM bytes of each. Call from a background thread. */
    fun history(context: Context, days: Int = 7): List<Today> {
        if (!hasAccess(context)) return emptyList()
        val manager = context.getSystemService(NetworkStatsManager::class.java) ?: return emptyList()
        val out = ArrayList<Today>()
        for (back in days - 1 downTo 0) {
            val start = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -back)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val from = start.timeInMillis
            val to = if (back == 0) System.currentTimeMillis() else (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            out.add(Today(total(manager, ConnectivityManager.TYPE_WIFI, from, to), total(manager, ConnectivityManager.TYPE_MOBILE, from, to)))
        }
        return out
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE)

    /** Reads the system's numbers and keeps them for the widget. Call from a background thread. */
    fun update(context: Context) {
        val result = query(context)
        val edit = prefs(context).edit()
        if (result == null) {
            edit.clear()
        } else {
            edit.clear().putString("date", today()).putLong("wifi", result.wifi).putLong("mobile", result.mobile)
        }
        edit.apply()
    }

    fun cached(context: Context): Today? {
        val p = prefs(context)
        if (p.getString("date", null) != today() || !p.contains("wifi")) return null
        return Today(p.getLong("wifi", -1L), p.getLong("mobile", -1L))
    }

    private fun size(context: Context, bytes: Long): String =
        if (bytes < 0) "\u2014" else Formatter.formatShortFileSize(context.applicationContext, bytes)

    fun wifiLine(context: Context, value: Today?): String =
        context.getString(R.string.label_wifi) + " " + size(context, value?.wifi ?: -1L)

    /** Just the number, for the widget (its label is a separate line). */
    fun wifiValue(context: Context, value: Today?): String = size(context, value?.wifi ?: -1L)

    fun simValue(context: Context, value: Today?): String = size(context, value?.mobile ?: -1L)

    fun simLine(context: Context, value: Today?): String =
        context.getString(R.string.label_sim) + " " + size(context, value?.mobile ?: -1L)

    /** Two lines: "Wi-Fi 1.2 GB" and "SIM 340 MB". */
    fun format(context: Context, value: Today?): String =
        context.getString(R.string.label_wifi) + " " + size(context, value?.wifi ?: -1L) + "\n" +
            context.getString(R.string.label_sim) + " " + size(context, value?.mobile ?: -1L)

    /** One line for the app and the notification. */
    fun oneLine(context: Context, value: Today): String =
        context.getString(R.string.label_wifi) + " " + size(context, value.wifi) + "   " +
            context.getString(R.string.label_sim) + " " + size(context, value.mobile)

    fun englishSize(bytes: Long): String = english(bytes)

    private fun english(bytes: Long): String {
        if (bytes < 0) return "\u2014"
        val kb = bytes / 1024.0
        val loc = java.util.Locale.US
        return when {
            kb < 1024.0 -> String.format(loc, "%.0f KB", kb)
            kb < 1024.0 * 1024.0 -> (kb / 1024.0).let { if (it >= 100) String.format(loc, "%.0f MB", it) else String.format(loc, "%.1f MB", it) }
            else -> String.format(loc, "%.2f GB", kb / 1024.0 / 1024.0)
        }
    }

    /** "Today: Wi-Fi 1.2 GB   SIM 340 MB" in English (for the app's first page). */
    fun englishLine(value: Today): String = "Today: Wi-Fi " + english(value.wifi) + "   SIM " + english(value.mobile)
}
