package app.myipwidget

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class City(val name: String, val region: String, val country: String, val lat: Double, val lon: Double)

/** [max] and [min] are today's highest and lowest temperature; [aqi] is the US air-quality index, or -1 when unknown. */
data class WeatherInfo(val temp: Double, val code: Int, val max: Double, val min: Double, val aqi: Int)

/** Temperature for a city the person chose (Open-Meteo: free, no key). */
object Weather {
    const val FRESH_MS = 25 * 60 * 1000L

    /** Cities matching a name: a list (maybe empty), or null when the search could not be made. */
    fun search(query: String): List<City>? {
        val text = query.trim()
        if (text.length < 2) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(text, "UTF-8") + "&count=8&language=fa&format=json"
        val body = IpLookup.httpGet(url) ?: return null
        try {
            val array = JSONObject(body).optJSONArray("results") ?: return emptyList()
            val found = ArrayList<City>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lat = item.optDouble("latitude", Double.NaN)
                val lon = item.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lon.isNaN() || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) continue
                val name = IpLookup.clean(item.optString("name"), 40)
                if (name.isEmpty()) continue
                found.add(
                    City(
                        name,
                        IpLookup.clean(item.optString("admin1"), 40),
                        IpLookup.clean(item.optString("country"), 40),
                        lat,
                        lon,
                    ),
                )
            }
            return found
        } catch (e: Exception) {
            return null
        }
    }

    fun fetch(lat: Double, lon: Double): WeatherInfo? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=" + "%.4f".format(Locale.US, lat) +
            "&longitude=" + "%.4f".format(Locale.US, lon) +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
            "&forecast_days=1&timezone=auto"
        val body = IpLookup.httpGet(url) ?: return null
        return try {
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            var max = temp
            var min = temp
            try {
                val daily = json.getJSONObject("daily")
                max = daily.getJSONArray("temperature_2m_max").getDouble(0)
                min = daily.getJSONArray("temperature_2m_min").getDouble(0)
            } catch (e: Exception) {
                // only the current temperature is known
            }
            WeatherInfo(temp, current.getInt("weather_code"), max, min, fetchAqi(lat, lon))
        } catch (e: Exception) {
            null
        }
    }

    private fun isRain(code: Int) = code in 51..67 || code in 80..82 || code in 95..99

    private fun isSnow(code: Int) = code in 71..77 || code in 85..86

    /** Rain or snow in the next three hours: (1 rain / 2 snow, hours from now), or null for none (or when it cannot be read). */
    fun fetchPrecip(lat: Double, lon: Double): Pair<Int, Int>? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=" + "%.4f".format(Locale.US, lat) +
            "&longitude=" + "%.4f".format(Locale.US, lon) + "&hourly=weather_code&forecast_days=2&timezone=auto"
        val body = IpLookup.httpGet(url) ?: return null
        return try {
            val json = JSONObject(body)
            val offset = json.optLong("utc_offset_seconds", 0L) * 1000L
            val times = json.getJSONObject("hourly").getJSONArray("time")
            val codes = json.getJSONObject("hourly").getJSONArray("weather_code")
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val nowLocal = System.currentTimeMillis() + offset
            for (i in 0 until times.length()) {
                val start = format.parse(times.getString(i))?.time ?: continue
                val code = codes.optInt(i, -1)
                // it is already raining or snowing now: nothing to warn about
                if (start <= nowLocal && nowLocal < start + 3600000L && (isRain(code) || isSnow(code))) return null
                val ahead = start - nowLocal
                if (ahead <= 0L || ahead > 3 * 3600000L) continue
                val hours = maxOf(1, Math.round(ahead / 3600000.0).toInt())
                if (isSnow(code)) return Pair(2, hours)
                if (isRain(code)) return Pair(1, hours)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Looks at the next hours (at most every 25 minutes) and raises the alert. Call from a background thread. */
    fun checkPrecip(context: Context) {
        if (!Prefs.rainAlert(context)) return
        val lat = Prefs.cityLat(context) ?: return
        val lon = Prefs.cityLon(context) ?: return
        val p = prefs(context)
        val age = System.currentTimeMillis() - p.getLong("precip_time", 0L)
        if (age in 0..FRESH_MS) return
        p.edit().putLong("precip_time", System.currentTimeMillis()).apply()
        val found = fetchPrecip(lat, lon) ?: return
        Alerts.rain(context, found.first, found.second)
    }

    /** Air quality (US AQI) from Open-Meteo; -1 when it cannot be read. */
    private fun fetchAqi(lat: Double, lon: Double): Int {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + "%.4f".format(Locale.US, lat) +
            "&longitude=" + "%.4f".format(Locale.US, lon) + "&current=us_aqi&timezone=auto"
        val body = IpLookup.httpGet(url) ?: return -1
        return try {
            val value = JSONObject(body).getJSONObject("current").optDouble("us_aqi", Double.NaN)
            if (value.isNaN() || value < 0) -1 else Math.round(value).toInt()
        } catch (e: Exception) {
            -1
        }
    }

    // ------------------------------------------------------------------ cache
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(context: Context): WeatherInfo? {
        val p = prefs(context)
        if (!p.contains("temp")) return null
        val temp = p.getFloat("temp", 0f).toDouble()
        return WeatherInfo(
            temp, p.getInt("code", 0),
            p.getFloat("max", temp.toFloat()).toDouble(), p.getFloat("min", temp.toFloat()).toDouble(),
            p.getInt("aqi", -1),
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** Looks the weather up again if the saved one is older than [maxAgeMs]. Call from a background thread. */
    fun refreshIfStale(context: Context, maxAgeMs: Long = FRESH_MS) {
        if (!Prefs.showWeather(context)) return
        val lat = Prefs.cityLat(context) ?: return
        val lon = Prefs.cityLon(context) ?: return
        val p = prefs(context)
        val age = System.currentTimeMillis() - p.getLong("time", 0L)
        if (p.contains("temp") && age in 0..maxAgeMs) return
        val info = fetch(lat, lon) ?: return
        p.edit()
            .putFloat("temp", info.temp.toFloat())
            .putInt("code", info.code)
            .putFloat("max", info.max.toFloat())
            .putFloat("min", info.min.toFloat())
            .putInt("aqi", info.aqi)
            .putLong("time", System.currentTimeMillis())
            .apply()
    }

    // ---------------------------------------------------------------- display
    fun symbol(code: Int): String = when (code) {
        0 -> "☀️"                                   // sun
        1, 2 -> "⛅"                                      // sun behind cloud
        3 -> "☁️"                                   // cloud
        45, 48 -> "🌫️"                        // fog
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"  // rain
        71, 73, 75, 77, 85, 86 -> "❄️"              // snow
        95, 96, 99 -> "⛈️"                          // thunderstorm
        else -> "☁️"
    }

    fun format(info: WeatherInfo): String = symbol(info.code) + " " + Math.round(info.temp) + "°C"

    /** "▲12° ▼3°": today's highest and lowest. */
    fun range(info: WeatherInfo): String = high(info) + " " + low(info)

    fun high(info: WeatherInfo): String = "\u25B2" + Math.round(info.max) + "\u00B0"

    fun low(info: WeatherInfo): String = "\u25BC" + Math.round(info.min) + "\u00B0"

    /** 0 good, 1 moderate, 2 unhealthy for sensitive people, 3 unhealthy, 4 very unhealthy or worse. */
    fun aqiLevel(aqi: Int): Int = when {
        aqi <= 50 -> 0
        aqi <= 100 -> 1
        aqi <= 150 -> 2
        aqi <= 200 -> 3
        aqi <= 300 -> 4
        else -> 5
    }
}
