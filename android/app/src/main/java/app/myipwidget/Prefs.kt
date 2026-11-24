package app.myipwidget

import android.content.Context

/** The choices made in the app: what to show on the widget, and the city for the weather. */
object Prefs {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun showWeather(context: Context) = prefs(context).getBoolean("show_weather", true)

    fun setShowWeather(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_weather", value).apply()
    }

    fun showUsage(context: Context) = prefs(context).getBoolean("show_usage", true)

    fun setShowUsage(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_usage", value).apply()
    }

    fun showRate(context: Context) = prefs(context).getBoolean("show_rate", true)

    fun setShowRate(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_rate", value).apply()
    }

    fun showDate(context: Context) = prefs(context).getBoolean("show_date", true)

    fun setShowDate(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_date", value).apply()
    }

    /** Live speed (and weather / volume) in a notification, with the icon in the status bar. */
    fun showSpeedNotice(context: Context) = prefs(context).getBoolean("show_speed_notice", true)

    fun setShowSpeedNotice(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_speed_notice", value).apply()
    }

    /** A second notification whose status-bar icon is the country code. */
    fun showFlagNotice(context: Context) = prefs(context).getBoolean("show_flag_notice", false)

    fun setShowFlagNotice(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_flag_notice", value).apply()
    }

    /** One short line only: no weather / volume text under the speed. */
    fun compactNotice(context: Context) = prefs(context).getBoolean("compact_notice", false)

    fun setCompactNotice(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("compact_notice", value).apply()
    }


    /** Background style of the widget: 0 follows the phone (light / dark), then dark, light, glass, blue, green. */
    fun theme(context: Context) = prefs(context).getInt("theme", 0).coerceIn(0, 5)

    /** 0 = the original layout, 1..2 = the coloured tile layouts (first, second). */
    fun layout(context: Context): Int = prefs(context).getInt("layout", 0).let { if (it in 0..2) it else 0 }

    fun setLayout(context: Context, value: Int) {
        prefs(context).edit().putInt("layout", value).apply()
    }

    /** "fa" (default) or "en": the language of the app's own screens. */
    fun language(context: Context): String = if (prefs(context).getString("language", "fa") == "en") "en" else "fa"

    fun setLanguage(context: Context, value: String) {
        prefs(context).edit().putString("language", value).apply()
    }

    /** The same context, with the language chosen in the app. */
    fun localized(base: Context): Context {
        val config = android.content.res.Configuration(base.resources.configuration)
        val locale = java.util.Locale(language(base))
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    // ---- beep: five times, each with its own days of the week (bit 0 = Sunday ... bit 6 = Saturday)
    fun beepOn(context: Context, slot: Int) = prefs(context).getBoolean("beep_on_$slot", false)
    fun beepHour(context: Context, slot: Int) = prefs(context).getInt("beep_h_$slot", 8)
    fun beepMinute(context: Context, slot: Int) = prefs(context).getInt("beep_m_$slot", 0)
    fun beepDays(context: Context, slot: Int) = prefs(context).getInt("beep_d_$slot", 0x7F)

    fun setBeepOn(context: Context, slot: Int, value: Boolean) {
        prefs(context).edit().putBoolean("beep_on_$slot", value).apply()
    }

    fun setBeepTime(context: Context, slot: Int, hour: Int, minute: Int) {
        prefs(context).edit().putInt("beep_h_$slot", hour).putInt("beep_m_$slot", minute).apply()
    }

    fun setBeepDays(context: Context, slot: Int, mask: Int) {
        prefs(context).edit().putInt("beep_d_$slot", mask).apply()
    }

    // ---- the order of the lines in the notification: the first one ticked stands first
    private val NOTICE_KEYS = listOf("temp", "date", "prayer", "usage", "rate")

    private fun noticeKeyOn(context: Context, key: String) = when (key) {
        "temp" -> noticeTemp(context)
        "date" -> noticeDate(context)
        "rate" -> noticeRate(context)
        "usage" -> noticeUsage(context)
        else -> noticePrayer(context)
    }

    /** The ticked lines, in the order they were ticked (older installs: azan, temperature, rate, usage). */
    fun noticeOrder(context: Context): List<String> {
        // fixed order: temperature, prayer call, data usage, dollar rate
        return NOTICE_KEYS.filter { noticeKeyOn(context, it) }
    }

    /** Call when a line is ticked (goes to the end of the order) or unticked (leaves it). */
    fun touchNoticeOrder(context: Context, key: String, ticked: Boolean) {
        val saved = (prefs(context).getString("notice_order", "") ?: "").split(',').filter { it in NOTICE_KEYS && it != key }.toMutableList()
        if (ticked) saved.add(key)
        prefs(context).edit().putString("notice_order", saved.joinToString(",")).apply()
    }

    /** Which of the beep melodies is chosen (0..19). */
    fun beepSound(context: Context) = prefs(context).getInt("beep_sound", 0).coerceIn(0, 20)

    fun setBeepSound(context: Context, value: Int) {
        prefs(context).edit().putInt("beep_sound", value).apply()
    }

    fun beepEnabled(context: Context) = prefs(context).getBoolean("beep_enabled", false)

    fun setBeepEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("beep_enabled", value).apply()
    }

    /** The prayer-call sound for one call ("azan_fajr", "azan_dhuhr", "azan_maghrib"); off by default. */
    fun azanSound(context: Context, key: String) = prefs(context).getBoolean("azan_sound_$key", false)

    fun setAzanSound(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean("azan_sound_$key", value).apply()
    }

    /** A sound file picked from the phone (sound number 20). */
    fun beepCustomUri(context: Context): String = prefs(context).getString("beep_custom_uri", "") ?: ""
    fun beepCustomName(context: Context): String = prefs(context).getString("beep_custom_name", "") ?: ""

    fun setBeepCustom(context: Context, uri: String, name: String) {
        prefs(context).edit().putString("beep_custom_uri", uri).putString("beep_custom_name", name).apply()
    }

    /** True after the first time the app was opened (the notification is switched on then, once). */
    fun firstRunDone(context: Context) = prefs(context).getBoolean("first_run_done", false)

    fun setFirstRunDone(context: Context) {
        prefs(context).edit().putBoolean("first_run_done", true).apply()
    }

    /** The line with the IP address can show the clock instead. */
    fun showClock(context: Context) = prefs(context).getBoolean("show_clock", false)

    fun setShowClock(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_clock", value).apply()
    }

    fun setTheme(context: Context, value: Int) {
        prefs(context).edit().putInt("theme", value).apply()
    }

    // ---- widget: more things to show
    fun showAqi(context: Context) = prefs(context).getBoolean("show_aqi", true)

    fun setShowAqi(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_aqi", value).apply()
    }

    fun showPrayer(context: Context) = prefs(context).getBoolean("show_prayer", true)

    fun setShowPrayer(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_prayer", value).apply()
    }

    // ---- notification: what the text under the speed holds
    fun noticeTemp(context: Context) = prefs(context).getBoolean("notice_temp", true)

    fun setNoticeTemp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notice_temp", value).apply()
    }

    fun noticeDate(context: Context) = prefs(context).getBoolean("notice_date", true)

    fun setNoticeDate(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notice_date", value).apply()
    }

    fun noticeRate(context: Context) = prefs(context).getBoolean("notice_rate", true)

    fun setNoticeRate(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notice_rate", value).apply()
    }

    fun noticeUsage(context: Context) = prefs(context).getBoolean("notice_usage", true)

    fun setNoticeUsage(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notice_usage", value).apply()
    }

    fun noticePrayer(context: Context) = prefs(context).getBoolean("notice_prayer", true)

    fun setNoticePrayer(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notice_prayer", value).apply()
    }

    /** The master switch for the notification bar. Before it existed: on when speed or flag was on. */
    fun noticeEnabled(context: Context): Boolean {
        val p = prefs(context)
        return if (p.contains("notice_enabled")) p.getBoolean("notice_enabled", false)
        else showSpeedNotice(context) || showFlagNotice(context)
    }

    fun setNoticeEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notice_enabled", value).apply()
    }

    fun anyNotice(context: Context) = noticeEnabled(context)

    fun cityName(context: Context): String = prefs(context).getString("city_name", "") ?: ""

    fun cityLat(context: Context): Double? = prefs(context).getString("city_lat", null)?.toDoubleOrNull()

    fun cityLon(context: Context): Double? = prefs(context).getString("city_lon", null)?.toDoubleOrNull()

    fun hasCity(context: Context) = cityLat(context) != null && cityLon(context) != null

    fun setCity(context: Context, name: String, lat: Double, lon: Double) {
        prefs(context).edit()
            .putString("city_name", name)
            .putString("city_lat", lat.toString())
            .putString("city_lon", lon.toString())
            .apply()
        Weather.clear(context)  // the old city's temperature must not be shown for the new one
    }

    fun clearCity(context: Context) {
        prefs(context).edit().remove("city_name").remove("city_lat").remove("city_lon").apply()
        Weather.clear(context)
    }

    // ------------------------------------------------------------------ alerts
    /** The alert when the battery is full (on by default). */
    fun chargeAlert(context: Context) = prefs(context).getBoolean("charge_alert", true)

    fun setChargeAlert(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("charge_alert", value).apply()
    }

    /** The alert when rain or snow is coming in the next hours (on by default). */
    fun rainAlert(context: Context) = prefs(context).getBoolean("rain_alert", true)

    fun setRainAlert(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("rain_alert", value).apply()
    }

    /** All the choices as one text (for the backup file). */
    fun exportJson(context: Context): String {
        val out = org.json.JSONObject()
        for ((key, value) in prefs(context).all) {
            val item = org.json.JSONObject()
            when (value) {
                is Boolean -> item.put("b", value)
                is Int -> item.put("i", value)
                is Long -> item.put("l", value)
                is Float -> item.put("f", value.toDouble())
                is String -> item.put("s", value)
                else -> continue
            }
            out.put(key, item)
        }
        return out.toString()
    }

    /** Puts the choices of a backup file back. False when the text is not a backup. */
    fun importJson(context: Context, text: String): Boolean {
        return try {
            val json = org.json.JSONObject(text)
            val edit = prefs(context).edit()
            edit.clear()
            val keys = json.keys()
            var count = 0
            while (keys.hasNext()) {
                val key = keys.next()
                val item = json.getJSONObject(key)
                when {
                    item.has("b") -> edit.putBoolean(key, item.getBoolean("b"))
                    item.has("i") -> edit.putInt(key, item.getInt("i"))
                    item.has("l") -> edit.putLong(key, item.getLong("l"))
                    item.has("f") -> edit.putFloat(key, item.getDouble("f").toFloat())
                    item.has("s") -> edit.putString(key, item.getString("s"))
                    else -> continue
                }
                count++
            }
            if (count == 0) return false
            edit.apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** How loud the "Allahu Akbar" is, 0..100 (a share of the alarm volume of the phone). */
    fun azanVolume(context: Context) = prefs(context).getInt("azan_volume", 100).coerceIn(0, 100)

    fun setAzanVolume(context: Context, value: Int) {
        prefs(context).edit().putInt("azan_volume", value.coerceIn(0, 100)).apply()
    }

    /** The city with its province: "Karaj - Alborz" (just the city when the province is unknown or the same). */
    fun cityLabel(context: Context): String {
        val city = cityName(context)
        if (city.isEmpty()) return ""
        val province = NearCity.province(city, cityLat(context), cityLon(context))
        return if (province.isEmpty() || province == city) city else "$city - $province"
    }
}
