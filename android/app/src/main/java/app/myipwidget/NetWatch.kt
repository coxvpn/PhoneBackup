package app.myipwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock

/**
 * Lets the system wake the widget when the connection changes (Wi-Fi <-> mobile data,
 * VPN on / off / another server), so the flag changes within a second or two instead of
 * waiting for the next periodic update.
 */
object NetWatch {
    const val ACTION_NET = "app.myipwidget.NET_EVENT"
    const val ACTION_LATER = "app.myipwidget.REFRESH_LATER"

    private fun pending(context: Context, action: String, code: Int, flags: Int): PendingIntent {
        val intent = Intent(context, IpWidget::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }

    // The system fills extra information into this one, so it has to be mutable.
    private fun netPending(context: Context) =
        pending(context, ACTION_NET, 1, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

    private fun laterPending(context: Context) =
        pending(context, ACTION_LATER, 2, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Starts (or restarts) listening for connection changes. Safe to call many times. */
    fun start(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        val target = netPending(app)
        try {
            manager.unregisterNetworkCallback(target)
        } catch (e: Exception) {
            // it was not registered yet
        }
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                // by default VPN networks are left out; we want to hear about them
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            manager.registerNetworkCallback(request, target)
        } catch (e: Exception) {
            // without it the widget still updates by its normal schedule and by the button
        }
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        try {
            app.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(netPending(app))
        } catch (e: Exception) {
            // not registered
        }
        try {
            app.getSystemService(AlarmManager::class.java)?.cancel(laterPending(app))
        } catch (e: Exception) {
            // nothing scheduled
        }
        app.getSharedPreferences("net", Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** One more refresh a few seconds later: a VPN often starts carrying traffic a moment after it appears. */
    fun scheduleLater(context: Context, delayMs: Long) {
        val app = context.applicationContext
        try {
            app.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + delayMs,
                laterPending(app),
            )
        } catch (e: Exception) {
            // not essential
        }
    }

    /** True when there is a usable connection at all (no point asking the internet otherwise). */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * The system reports many small events (signal strength and so on). Only a real change of
     * networks - a different network, VPN added or removed - is worth a new look at the IP.
     */
    @Suppress("DEPRECATION")
    fun networkChanged(context: Context): Boolean {
        val app = context.applicationContext
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return true

        val parts = ArrayList<String>()
        for (network in manager.allNetworks) {
            val caps = manager.getNetworkCapabilities(network) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val kinds = StringBuilder()
            val transports = intArrayOf(
                NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.TRANSPORT_CELLULAR,
                NetworkCapabilities.TRANSPORT_ETHERNET,
                NetworkCapabilities.TRANSPORT_VPN,
            )
            for (t in transports) if (caps.hasTransport(t)) kinds.append(t)
            parts.add("$network:$kinds")
        }
        parts.sort()
        val now = (manager.activeNetwork?.toString() ?: "none") + "|" + parts.joinToString(",")

        val prefs = app.getSharedPreferences("net", Context.MODE_PRIVATE)
        if (prefs.getString("fingerprint", null) == now) return false
        prefs.edit().putString("fingerprint", now).apply()
        return true
    }
}
