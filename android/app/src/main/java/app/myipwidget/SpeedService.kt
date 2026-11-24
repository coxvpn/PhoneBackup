package app.myipwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.text.format.Formatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps up to two notifications alive:
 *  - the speed one: the status-bar icon shows the download speed; the text shows both speeds,
 *    the weather and today's volume;
 *  - the flag one: the status-bar icon shows the country code (status-bar icons can only be
 *    one-colour, so a coloured flag is not possible there); the text has the flag, country and IP.
 */
class SpeedService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val busy = AtomicBoolean(false)
    private var running = false
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTime = 0L
    private var down = 0.0
    private var up = 0.0
    private var ticks = 0
    private var flagShown: String? = null
    private var asForeground = false
    // far in the future and fixed: the system lists the newest notification first, so this one stays on top
    private val startTime = System.currentTimeMillis() + 3_000_000_000_000L

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        private fun changed() {
            val app = applicationContext
            if (NetWatch.networkChanged(app)) {
                IpWidget.refresh(app, force = true)
                NetWatch.scheduleLater(app, 8000)
            }
        }

        override fun onAvailable(network: android.net.Network) = changed()
        override fun onLost(network: android.net.Network) = changed()
        override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) = changed()
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Alerts.checkCharge(applicationContext, intent)
        }
    }
    private var batteryWatched = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                measure()
                if (ticks % 20 == 0) refreshExtras()
                ticks++
                render()
            } catch (e: Exception) {
                // never let one bad second stop the service
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        // the old, low-priority channel is removed; the new one has a high importance (stands above the others), still without a sound or vibration
        manager.deleteNotificationChannel("speed")
        manager.deleteNotificationChannel("speed_top")
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notice_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            },
        )
        thread = HandlerThread("speed").also { it.start() }
        handler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.anyNotice(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        enterForeground()
        NetWatch.start(applicationContext)
        if (!running) {
            running = true
            try {
                val request = android.net.NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build()
                getSystemService(ConnectivityManager::class.java).registerNetworkCallback(request, netCallback)
            } catch (e: Exception) {
                // the system wake-up through NetWatch still works
            }
            if (!batteryWatched) {
                try {
                    registerReceiver(batteryReceiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    batteryWatched = true
                } catch (e: Exception) { }
            }
            lastRx = -1L
            handler.post { IpWidget.refresh(applicationContext) }   // make sure the IP / flag are current
            handler.post(tick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(netCallback)
        } catch (e: Exception) {
            // it was not registered
        }
        if (batteryWatched) {
            try { unregisterReceiver(batteryReceiver) } catch (e: Exception) { }
            batteryWatched = false
        }
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        getSystemService(NotificationManager::class.java).cancel(ID_FLAG)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------ foreground
    private fun enterForeground() {
        val notification = if (hasMain()) buildMain() else buildFlag()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_MAIN, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_MAIN, notification)
        }
        asForeground = true
    }

    // ------------------------------------------------------------------ measuring
    private fun vpnActive(): Boolean {
        return try {
            val manager = getSystemService(ConnectivityManager::class.java)
            val caps = manager.getNetworkCapabilities(manager.activeNetwork)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
        }
    }

    private fun measure() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = System.nanoTime()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            down = 0.0
            up = 0.0
            return
        }
        if (lastRx >= 0 && now > lastTime) {
            val seconds = (now - lastTime) / 1e9
            // with a VPN every byte passes the phone twice (the real link and the tunnel)
            val factor = if (vpnActive()) 0.5 else 1.0
            down = maxOf(0L, rx - lastRx) / seconds * factor
            up = maxOf(0L, tx - lastTx) / seconds * factor
            lastSpeed = down + up
        }
        lastRx = rx
        lastTx = tx
        lastTime = now
    }

    /** Weather and today's volume change slowly: look them up now and then, off the main loop. */
    private fun refreshExtras() {
        if (!busy.compareAndSet(false, true)) return
        val app = applicationContext
        Thread {
            try {
                Weather.refreshIfStale(app, Weather.FRESH_MS)
                Weather.checkPrecip(app)
                Rate.refreshIfStale(app, Weather.FRESH_MS)
                Usage.update(app)
            } catch (e: Exception) {
                // optional extras
            } finally {
                busy.set(false)
            }
        }.start()
    }

    // ------------------------------------------------------------------ showing
    /** True when the main notification has something to show: the speed, or any of the extra lines. */
    private fun hasMain(): Boolean = Prefs.showSpeedNotice(this) || Prefs.noticeTemp(this) || Prefs.noticeDate(this) || Prefs.noticeRate(this) ||
        Prefs.noticeUsage(this) || Prefs.noticePrayer(this)

    private fun render() {
        if (!Prefs.anyNotice(this) || (!hasMain() && !Prefs.showFlagNotice(this))) {
            stopSelf()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val flagOn = Prefs.showFlagNotice(this)
        if (hasMain()) {
            manager.notify(ID_MAIN, buildMain())
            if (flagOn && !mergeFlag()) {
                val key = describeCached().let { it.flag + it.title + it.ip + it.detail } + codeCached()
                if (key != flagShown) {
                    flagShown = key
                    manager.notify(ID_FLAG, buildFlag())
                }
            } else {
                flagShown = null
                manager.cancel(ID_FLAG)
            }
        } else {
            // only the flag is wanted: it takes the place of the main notification
            manager.notify(ID_MAIN, buildFlag())
            flagShown = null
            manager.cancel(ID_FLAG)
        }
    }

    /** One status-bar icon instead of two: the country code (US) and the speed share the app's small icon, and the flag lines join the main notification. */
    private fun mergeFlag(): Boolean = Prefs.showFlagNotice(this) && codeCached().isNotEmpty()


    private fun describeCached(): Display {
        val cached = IpLookup.load(this)
        return if (cached != null) IpLookup.describe(this, cached) else IpLookup.checking(this)
    }

    private fun codeCached(): String = IpLookup.load(this)?.code ?: ""

    private fun open(): PendingIntent = PendingIntent.getActivity(
        this, 7, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val FSI = "\u2068"   // isolate: keeps Persian words from reordering the numbers around them
    private val PDI = "\u2069"
    private val LRM = "\u200E"
    private val DOT = "  \u00B7  "

    /** Weather and city: "14°C · Karaj" (empty when off or unknown). */
    private fun tempPart(withAqi: Boolean): String {
        if (!Prefs.noticeTemp(this) || !Prefs.hasCity(this)) return ""
        val weather = Weather.cached(this) ?: return ""
        return LRM + Weather.format(weather) +
            (if (withAqi && weather.aqi > 0) DOT + "AQI " + weather.aqi else "") +
            DOT + FSI + (if (withAqi) Prefs.cityLabel(this) else Prefs.cityName(this)) + PDI + LRM +
            (if (withAqi) (Occasions.today()?.let { DOT + FSI + Occasions.sign(it) + PDI } ?: "") else "")
    }

    /** The Persian part of the date line, made a little bigger in the expanded text. */
    private var datePersian = ""

    private fun datePart(): String {
        if (!Prefs.noticeDate(this)) return ""
        val now = java.util.Calendar.getInstance()
        val persian = PersianDate.full(now).substringBefore("\n")
        datePersian = persian
        return FSI + persian + PDI + DOT + PersianDate.gregorian(now) +
            (Occasions.today()?.let { DOT + FSI + Occasions.sign(it) + PDI } ?: "")
    }

    /** The Persian date alone, for the one-line text. */
    private fun shortDate(): String {
        if (!Prefs.noticePrayer(this) && !Prefs.noticeDate(this)) return ""
        return FSI + PersianDate.full(java.util.Calendar.getInstance()).substringBefore("\n") + PDI
    }

    private fun ratePart(): String {
        if (!Prefs.noticeRate(this)) return ""
        val rate = Rate.cached(this) ?: return ""
        return LRM + Rate.format(rate)
    }

    private fun usagePart(): String {
        if (!Prefs.noticeUsage(this)) return ""
        val used = Usage.cached(this) ?: return ""
        return getString(R.string.today_short) + ": " + Usage.oneLine(this, used)
    }

    /** The prayer call after the speed in the title: short, or with the clock time when [long]. */
    private fun azanPart(long: Boolean): String {
        if (!Prefs.noticePrayer(this)) return ""
        val azan = Prayer.next(this) ?: return ""
        val left = Prayer.remainingText(azan.time - System.currentTimeMillis())
        val name = getString(azan.nameRes)
        val text = if (long) {
            "\uD83D\uDD4C " + getString(R.string.azan_word) + " " + name + " " + Prayer.clockText(azan.time) + DOT + left + " " + getString(R.string.azan_left)
        } else {
            "\uD83D\uDD4C " + name + " " + left + " " + getString(R.string.azan_left)
        }
        return FSI + text + PDI
    }

    /**
     * The main notification. The speed (when on) is alone in the title, so it stays at the left edge;
     * the prayer call, weather and rate are in the text under it. Nothing sits beside a changing number.
     */
    private fun buildMain(): Notification {
        val speedOn = Prefs.showSpeedNotice(this)
        val compact = Prefs.compactNotice(this)
        // the lines stand in the order they were ticked in the settings
        val keys = Prefs.noticeOrder(this)
        fun longOf(key: String): String = when (key) {
            "temp" -> tempPart(true)
            "rate" -> ratePart()
            "usage" -> usagePart()
            "date" -> datePart()
            else -> azanPart(true)
        }
        fun shortOf(key: String): String = when (key) {
            "usage" -> ""   // too long for the one-line text
            // the one-line text shows the Persian date where the prayer call would stand (the expanded text keeps the prayer call)
            "prayer" -> if (Prefs.noticePrayer(this)) shortDate() else ""
            "date" -> if (Prefs.noticePrayer(this)) "" else shortDate()
            "temp" -> tempPart(false)
            else -> longOf(key)
        }
        val longs = keys.map { it to longOf(it) }.filter { it.second.isNotEmpty() }
        val shorts = keys.map { shortOf(it) }.filter { it.isNotEmpty() }

        val title: String
        val bigTitle: String
        val collapsedParts: List<String>
        val expandedLines: List<String>
        if (speedOn) {
            // download and upload added together: one number
            // the unit never moves: the number is padded on the left with figure spaces (as wide as a digit)
            val total = if (down + up < MIN_SPEED) 0L else (down + up).toLong()
            // KB/s up to one megabyte, then MB/s with one decimal; the number is padded so the unit does not move
            val number = if (total >= 1024L * 1024L) {
                java.util.Locale.US.let { String.format(it, "%.1f", total / 1048576.0) } + " MB/s"
            } else {
                (total / 1024L).toString() + " KB/s"
            }
            title = "\u2193\u2191 " + number.substringBefore(' ').padStart(5, '\u2007') + " " + number.substringAfter(' ')
            bigTitle = title
            collapsedParts = shorts
            expandedLines = longs.map { it.second }
        } else if (longs.isNotEmpty()) {
            // no speed: the first line takes the title
            val first = longs.first().first
            title = shortOf(first).ifEmpty { longs.first().second }
            bigTitle = longs.first().second
            collapsedParts = shorts.drop(if (shortOf(first).isEmpty()) 0 else 1)
            expandedLines = longs.drop(1).map { it.second }
        } else {
            title = getString(R.string.app_name)
            bigTitle = title
            collapsedParts = emptyList()
            expandedLines = emptyList()
        }
        val merged = mergeFlag()
        val flagLine = if (merged) describeCached().let { it.flag + " " + it.title } else ""
        val collapsed = (collapsedParts + (if (merged) listOf(flagLine) else emptyList())).filter { it.isNotEmpty() }.joinToString("\u2003\u2003\u2003")
        val expanded = (expandedLines + (if (merged) listOf(flagLine) else emptyList())).filter { it.isNotEmpty() }.joinToString("\n")

        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(
                if (speedOn && merged) speedIcon(down + up, codeCached())
                else if (speedOn) speedIcon(down + up)
                else if (merged) textIcon(codeCached())
                else Icon.createWithResource(this, R.drawable.ic_stat),
            )
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setWhen(startTime)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open())
        if (!compact && expanded.isNotEmpty()) {
            builder.setContentText(collapsed)
            val styled = android.text.SpannableString(expanded)
            val at = if (datePersian.isEmpty()) -1 else expanded.indexOf(datePersian)
            if (at >= 0) {
                val end = at + datePersian.length
                styled.setSpan(android.text.style.RelativeSizeSpan(1.25f), at, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            builder.setStyle(Notification.BigTextStyle().setBigContentTitle(bigTitle).bigText(styled))
        }
        return builder.build()
    }

    private fun buildFlag(): Notification {
        val display = describeCached()
        val code = codeCached()
        // only the flag and the country name: the IP number and the carrier text are in the app
        val text = ""
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(textIcon(if (code.isEmpty()) "?" else code))
            .setContentTitle(display.flag + " " + display.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setWhen(startTime)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open())
        if (text.isNotEmpty()) {
            builder.setContentText(text.replace("\n", "  -  "))
            builder.setStyle(Notification.BigTextStyle().bigText(text))
        }
        return builder.build()
    }

    // ------------------------------------------------------------------ icons
    /**
     * Download plus upload as one big number with a small unit under it (K or M). Below
     * [MIN_SPEED] it shows 0, so the icon does not flicker with tiny background traffic.
     */
    private fun speedIcon(bytesPerSecond: Double, code: String = ""): Icon {
        val kb = if (bytesPerSecond < MIN_SPEED) 0.0 else bytesPerSecond / 1024.0
        val number: String
        val unit: String
        if (kb < 1024.0) {
            number = if (kb in 0.1..9.99) "%.1f".format(java.util.Locale.US, kb) else kb.toInt().toString()
            unit = "K"
        } else {
            val mb = kb / 1024.0
            number = if (mb < 10) "%.1f".format(java.util.Locale.US, mb) else mb.toInt().toString()
            unit = "M"
        }
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = 78f
        }
        if (code.isEmpty()) {
            while (paint.measureText(number) > size - 2 && paint.textSize > 24f) paint.textSize -= 2f
            canvas.drawText(number, size / 2f, 64f, paint)
            paint.textSize = 32f
            canvas.drawText(unit, size / 2f, 94f, paint)
        } else {
            // two lines: the country code above, the speed (number and unit) below
            val label = code.uppercase().take(3)
            paint.textSize = 40f
            while (paint.measureText(label) > size - 2 && paint.textSize > 20f) paint.textSize -= 2f
            canvas.drawText(label, size / 2f, 38f, paint)
            val speed = number + unit
            paint.textSize = 54f
            while (paint.measureText(speed) > size - 2 && paint.textSize > 20f) paint.textSize -= 2f
            canvas.drawText(speed, size / 2f, 90f, paint)
        }
        return Icon.createWithBitmap(bitmap)
    }

    /** White text on a transparent square: the system tints it to match the status bar. */
    private fun textIcon(text: String): Icon {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = 72f
        }
        while (paint.measureText(text) > size - 4 && paint.textSize > 20f) paint.textSize -= 2f
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, size / 2f, baseline, paint)
        return Icon.createWithBitmap(bitmap)
    }

    companion object {
        /** The latest speed (download plus upload, bytes per second); the quick-settings tile reads it. */
        @Volatile var lastSpeed = 0.0

        const val CHANNEL = "speed_high"
        private const val MIN_SPEED = 20 * 1024.0   // bytes per second: below this the icon shows 0
        private const val ID_MAIN = 41
        private const val ID_FLAG = 42

        /** Starts or stops the service to match the choices made in the app. */
        fun sync(context: Context) {
            val app = context.applicationContext
            try {
                val intent = Intent(app, SpeedService::class.java)
                if (Prefs.anyNotice(app)) {
                    app.startForegroundService(intent)
                } else {
                    app.stopService(intent)
                }
            } catch (e: Exception) {
                // the system may refuse a start from the background; the next app visit retries
            }
        }
    }
}
