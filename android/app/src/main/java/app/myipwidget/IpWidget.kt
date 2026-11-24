package app.myipwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class IpWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        NetWatch.start(context)
    }

    override fun onDisabled(context: Context) {
        if (!Prefs.anyNotice(context)) NetWatch.stop(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        NetWatch.start(context)
        val pending = goAsync()
        refresh(context) { pending.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // the button on the widget: also look at the weather again if it is a few minutes old
            ACTION_REFRESH -> {
                val pending = goAsync()
                refresh(context, BUTTON_WEATHER_AGE_MS, force = true) { pending.finish() }
            }
            // a prayer call has come: show the next one
            ACTION_PRAYER -> redrawFromCache(context)
            // the delayed second look after a network change
            NetWatch.ACTION_LATER -> {
                val pending = goAsync()
                refresh(context, force = true) { pending.finish() }
            }
            // the system says the connection changed
            NetWatch.ACTION_NET -> {
                if (!hasWidgets(context) && !Prefs.anyNotice(context)) {
                    NetWatch.stop(context)
                    return
                }
                if (NetWatch.networkChanged(context)) {
                    val pending = goAsync()
                    refresh(context, force = true) { pending.finish() }
                    NetWatch.scheduleLater(context, 8000)
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                SpeedService.sync(context)
                Beeper.schedule(context)
                Beeper.scheduleAzan(context)
                if (hasWidgets(context)) {
                    NetWatch.start(context)
                    val pending = goAsync()
                    refresh(context) { pending.finish() }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_REFRESH = "app.myipwidget.REFRESH"
        const val ACTION_PRAYER = "app.myipwidget.PRAYER"
        private val AQI_BACKGROUNDS = intArrayOf(
            R.drawable.aqi_bg_0, R.drawable.aqi_bg_1, R.drawable.aqi_bg_2, R.drawable.aqi_bg_3, R.drawable.aqi_bg_4, R.drawable.aqi_bg_5,
        )
        private val LAYOUTS = intArrayOf(
            R.layout.widget, R.layout.widget_l1, R.layout.widget_l2,
        )
        private const val BUTTON_WEATHER_AGE_MS = 2 * 60 * 1000L

        private val latest = AtomicInteger(0)
        private val fetchingSince = AtomicLong(0L)

        fun skyTile(code: Int, night: Boolean): Int = when (code) {
            45, 48 -> R.drawable.tile_sky_fog
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> R.drawable.tile_sky_rain
            71, 73, 75, 77, 85, 86 -> R.drawable.tile_sky_snow
            3 -> R.drawable.tile_sky_cloud
            else -> if (night) R.drawable.tile_sky_night else R.drawable.tile_sky
        }

        private fun hasWidgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(ComponentName(context, IpWidget::class.java)).isNotEmpty()
        }

        fun describeCached(context: Context): Display {
            val cached = IpLookup.load(context)
            return if (cached != null) IpLookup.describe(context, cached) else IpLookup.checking(context)
        }

        /** Draws the widget again from what is saved (after a setting changed). */
        fun redrawFromCache(context: Context) {
            render(context.applicationContext, describeCached(context.applicationContext))
        }

        /**
         * Shows the last known values with a "busy" button at once, then looks up the IP on a
         * background thread and updates every widget on the home screen. The flag is drawn as soon
         * as the IP is known; the weather and today's volume follow.
         */
        fun refresh(
            context: Context,
            weatherMaxAgeMs: Long = Weather.FRESH_MS,
            force: Boolean = false,
            done: () -> Unit = {},
        ) {
            val app = context.applicationContext
            render(app, describeCached(app), busy = true)

            // One lookup at a time: when the net is bad, piling up more would only hold more sockets open.
            val now = System.currentTimeMillis()
            val since = fetchingSince.get()
            // A real change (network, VPN, the button) always starts a new lookup: the one in flight may
            // be asking through the old connection, and its answer is thrown away.
            if (!force && since != 0L && now - since < 20_000L) {
                done()
                return
            }
            fetchingSince.set(now)
            val mine = latest.incrementAndGet()

            Thread {
                try {
                    val info = if (NetWatch.isOnline(app)) IpLookup.fetch() else null
                    // a newer refresh may have started meanwhile; only the newest one may save and draw
                    if (info != null && mine == latest.get()) IpLookup.save(app, info)
                    if (mine == latest.get()) {
                        render(app, if (info != null) IpLookup.describe(app, info) else IpLookup.offline(app), busy = true)
                    }
                    try {
                        if (info != null) {
                            Weather.refreshIfStale(app, weatherMaxAgeMs)
                            Rate.refreshIfStale(app, weatherMaxAgeMs)
                        }
                        Usage.update(app)
                    } catch (e: Exception) {
                        // the extras are optional
                    }
                    if (mine == latest.get()) {
                        render(app, if (info != null) IpLookup.describe(app, info) else IpLookup.offline(app))
                    }
                } finally {
                    if (mine == latest.get()) fetchingSince.set(0L)
                    done()
                }
            }.start()
        }

        private class Look(val bg: Int, val text: Int, val dim: Int, val up: Int, val down: Int, val prayer: Int, val button: Int, val panel: Int)

        private fun look(theme: Int): Look? = when (theme) {
            1 -> Look(R.drawable.bg_theme_dark, 0xFFF2F2F2.toInt(), 0xFFA8A8A8.toInt(), 0xFFFF8A65.toInt(), 0xFF64B5F6.toInt(), 0xFFFFD27A.toInt(), R.drawable.btn_on_dark, R.drawable.panel_on_dark)
            2 -> Look(R.drawable.bg_theme_light, 0xFF111111.toInt(), 0xFF5C5C5C.toInt(), 0xFFD84315.toInt(), 0xFF1565C0.toInt(), 0xFF8A5A00.toInt(), R.drawable.btn_on_light, R.drawable.panel_on_light)
            3 -> Look(R.drawable.bg_theme_glass, 0xFFFFFFFF.toInt(), 0xFFE8E8E8.toInt(), 0xFFFF8A65.toInt(), 0xFF90CAF9.toInt(), 0xFFFFD27A.toInt(), R.drawable.btn_on_dark, R.drawable.panel_on_dark)
            4 -> Look(R.drawable.bg_theme_blue, 0xFFEAF1FF.toInt(), 0xFF9DB3D9.toInt(), 0xFFFF8A65.toInt(), 0xFF64B5F6.toInt(), 0xFFFFD27A.toInt(), R.drawable.btn_on_dark, R.drawable.panel_on_dark)
            5 -> Look(R.drawable.bg_theme_green, 0xFFEAFFF0.toInt(), 0xFF9FCBB0.toInt(), 0xFFFF8A65.toInt(), 0xFF64B5F6.toInt(), 0xFFFFD27A.toInt(), R.drawable.btn_on_dark, R.drawable.panel_on_dark)
            else -> null   // 0: the layout's own colours, which follow the phone's light / dark mode
        }

        private fun applyTheme(context: Context, views: RemoteViews) {
            val look = look(Prefs.theme(context)) ?: return
            views.setInt(R.id.root, "setBackgroundResource", look.bg)
            for (id in intArrayOf(R.id.weather, R.id.date, R.id.country, R.id.country2, R.id.ip, R.id.wifi, R.id.sim, R.id.refresh, R.id.clock)) {
                views.setTextColor(id, look.text)
            }
            for (id in intArrayOf(R.id.date2, R.id.detail, R.id.lbl_wifi, R.id.lbl_sim)) views.setTextColor(id, look.dim)
            views.setTextColor(R.id.temp_high, look.up)
            views.setTextColor(R.id.temp_low, look.down)
            views.setTextColor(R.id.prayer_label, look.prayer)
            views.setInt(R.id.prayer_time, "setTextColor", look.prayer)
            views.setInt(R.id.refresh, "setBackgroundResource", look.button)
            views.setInt(R.id.info, "setBackgroundResource", look.panel)
        }

        /** An IPv6 address is long: "IPv6 2a01:4f8…1" is enough for the widget. */
        private fun shortIp(ip: String): String {
            if (!ip.contains(':')) return ip
            val groups = ip.split(':').filter { it.isNotEmpty() }
            if (groups.size < 3) return "IPv6 $ip"
            return "IPv6 " + groups[0] + ":" + groups[1] + "\u2026" + groups.last()
        }

        /** Wakes the widget up when the prayer call comes, so it can switch to the next one. */
        private fun schedulePrayerRedraw(context: Context, at: Long) {
            try {
                val alarms = context.getSystemService(AlarmManager::class.java) ?: return
                val pending = PendingIntent.getBroadcast(
                    context, 9, Intent(context, IpWidget::class.java).setAction(ACTION_PRAYER),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarms.setAndAllowWhileIdle(AlarmManager.RTC, at + 2000L, pending)
            } catch (e: Exception) {
                // the widget is then redrawn at its next normal update
            }
        }

        fun render(context: Context, display: Display, busy: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, IpWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, build(context, display, busy, Prefs.layout(context)))
        }

        /** The widget's views in the given layout; also used for the preview inside the app. */
        fun build(context: Context, display: Display, busy: Boolean, layout: Int): RemoteViews {
            val views = RemoteViews(context.packageName, LAYOUTS[layout])
            val styled = layout != 0   // the coloured tile layouts have their own colours
            val clock = Prefs.showClock(context)
            // the IP number and the carrier text are only in the app: the widget has the flag and the country
            views.setViewVisibility(R.id.ip, View.GONE)
            views.setViewVisibility(R.id.detail, View.GONE)
            if (layout == 0) {
                // the plain layout: the flag and country move into the clock's row, beside the clock
                views.setViewVisibility(R.id.flag_row, if (clock) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.clock_row, if (clock) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.flag2, display.flag)
                views.setTextViewText(R.id.country2, display.title)
            }
            views.setViewVisibility(R.id.clock, if (clock) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.flag, display.flag)
            views.setTextViewText(R.id.country, display.title)
            views.setTextViewText(R.id.refresh, if (busy) "…" else "↻")

            // what to show, as chosen in the app
            val showWeather = Prefs.showWeather(context) && Prefs.hasCity(context)
            val showUsage = Prefs.showUsage(context)
            val showDate = Prefs.showDate(context)
            val showRate = Prefs.showRate(context)
            val weather = Weather.cached(context)
            val today = Usage.cached(context)

            // temperature, high / low, air quality
            views.setViewVisibility(R.id.weather_box, if (showWeather) View.VISIBLE else if (styled) View.GONE else View.INVISIBLE)
            if (styled) {
                val night = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it < 6 || it >= 18 }
                val code = weather?.code ?: 0
                views.setTextViewText(
                    R.id.sun,
                    if (weather == null) "" else if (night && code == 0) "\uD83C\uDF19" else Weather.symbol(code),
                )
                // the tile's colour follows the weather
                views.setInt(R.id.weather_box, "setBackgroundResource", skyTile(code, night))
                views.setTextViewText(R.id.weather, if (weather != null) Math.round(weather.temp).toString() + "\u00B0" else "\u2026")
            } else {
                views.setTextViewText(R.id.weather, if (weather != null) Weather.format(weather) else "\u2026")
            }
            // the city name with its province next to the air quality (a very long one is cut to its first part)
            var city = Prefs.cityLabel(context)
            if (city.length > 22) city = city.split("-", "\u060C", ",").first().trim()
            views.setTextViewText(R.id.city_label, if (city.isEmpty()) "" else "\u2068" + city + "\u2069")
            views.setViewVisibility(R.id.minmax, if (weather != null) View.VISIBLE else View.INVISIBLE)
            if (weather != null) {
                views.setTextViewText(R.id.temp_high, Weather.high(weather))
                views.setTextViewText(R.id.temp_low, Weather.low(weather))
            }
            val aqi = weather?.aqi ?: -1
            if (showWeather && Prefs.showAqi(context) && aqi >= 0) {
                val level = Weather.aqiLevel(aqi)
                views.setViewVisibility(R.id.aqi, View.VISIBLE)
                views.setTextViewText(R.id.aqi, "AQI $aqi")
                views.setInt(R.id.aqi, "setBackgroundResource", AQI_BACKGROUNDS[level])
                views.setTextColor(R.id.aqi, if (level == 1 || level == 2) 0xFF222222.toInt() else 0xFFFFFFFF.toInt())
            } else {
                views.setViewVisibility(R.id.aqi, View.GONE)
            }

            // dates and the time left to the next prayer call
            val azan = if (Prefs.showPrayer(context)) Prayer.next(context) else null
            views.setViewVisibility(R.id.date, if (showDate) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.date2, if (showDate) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.date, PersianDate.short())
            views.setTextViewText(R.id.date2, PersianDate.gregorian())
            if (azan != null) {
                val left = azan.time - System.currentTimeMillis()
                views.setViewVisibility(R.id.prayer_row, View.VISIBLE)
                views.setTextViewText(
                    R.id.prayer_label,
                    context.getString(
                        if (styled) R.string.widget_azan_full else R.string.widget_azan_prefix,
                        context.getString(azan.nameRes),
                    ),
                )
                views.setChronometerCountDown(R.id.prayer_time, true)
                views.setChronometer(R.id.prayer_time, SystemClock.elapsedRealtime() + left, null, true)
                schedulePrayerRedraw(context, azan.time)
            } else {
                views.setViewVisibility(R.id.prayer_row, View.GONE)
            }
            // keep the space, so the button stays at the right edge
            views.setViewVisibility(R.id.info, if (showDate || azan != null || styled) View.VISIBLE else View.INVISIBLE)
            // the day's occasion, under the dates (the coloured layouts)
            val occasion = if (showDate) Occasions.today() else null
            if (occasion != null) {
                views.setViewVisibility(R.id.occasion, View.VISIBLE)
                views.setTextViewText(R.id.occasion, Occasions.sign(occasion))
                views.setInt(R.id.occasion, "setBackgroundResource", if (occasion.holiday) R.drawable.chip_red else R.drawable.chip_purple)
            } else {
                views.setViewVisibility(R.id.occasion, View.GONE)
            }

            // bottom row: three cells, each with a small label and a big value
            views.setTextViewText(R.id.lbl_rate, (if (styled) "\uD83D\uDCB5 " else "") + context.getString(R.string.widget_rate_label))
            views.setTextViewText(R.id.lbl_wifi, context.getString(R.string.widget_wifi_label))
            views.setTextViewText(R.id.lbl_sim, context.getString(R.string.widget_sim_label))
            views.setTextViewText(R.id.rate, Rate.format(Rate.cached(context)))
            views.setTextViewText(R.id.wifi, (if (styled) "Wi-Fi " else "") + Usage.wifiValue(context, today))
            views.setTextViewText(R.id.sim, (if (styled) "SIM " else "") + Usage.simValue(context, today))
            views.setViewVisibility(R.id.usage_box, if (showUsage) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.cell_rate, if (showRate) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.cell_wifi, if (showUsage) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.cell_sim, if (showUsage) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.extra, if (showRate || showUsage) View.VISIBLE else View.GONE)

            if (!styled) applyTheme(context, views)

            val refreshIntent = Intent(context, IpWidget::class.java).setAction(ACTION_REFRESH)
            val refreshPending = PendingIntent.getBroadcast(
                context, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.refresh, refreshPending)

            // tapping the rest of the widget opens the app
            val openPending = PendingIntent.getActivity(
                context, 3, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.root, openPending)

            return views
        }
    }
}
