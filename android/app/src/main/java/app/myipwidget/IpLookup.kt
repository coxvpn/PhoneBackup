package app.myipwidget

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Raw result of a successful check. Empty strings mean "not known". */
data class IpInfo(
    val ip: String,
    val country: String,
    val code: String,
    val city: String,
    val isp: String,
)

/** What the screen / widget actually shows. */
data class Display(
    val flag: String,
    val title: String,
    val ip: String,
    val detail: String,
)

object IpLookup {
    private const val TIMEOUT_MS = 4000
    private const val PIRATE = "🏴‍☠️"
    private val IP_SHAPE = Regex("^[0-9a-fA-F:.]{2,45}$")

    // ------------------------------------------------------------------ network
    fun httpGet(url: String, limit: Int = 20000): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                // no kept-alive connections: a half-closed one would sit in CLOSE_WAIT until it is reused
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "Mostafa-MultiWidget-Android/1.21.11")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.use { readLimited(it, limit) }
            } else {
                conn.errorStream?.close()
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - total))
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toString("UTF-8")
    }

    fun clean(value: String?, limit: Int = 60): String {
        val text = (value ?: "").filter { !it.isISOControl() }.trim().take(limit)
        return if (text == "null") "" else text
    }

    private fun cleanCode(value: String?): String {
        val code = clean(value, 8).uppercase()
        return if (code.length == 2 && code.all { it in 'A'..'Z' }) code else ""
    }

    private fun fetchIp(): String? {
        val body = httpGet("https://api.ipify.org?format=json")
        if (body != null) {
            try {
                val ip = JSONObject(body).optString("ip").trim()
                if (IP_SHAPE.matches(ip)) return ip
            } catch (e: Exception) {
                // try the next source
            }
        }
        for (url in listOf("https://ipv4.icanhazip.com", "https://ifconfig.me/ip")) {
            val ip = httpGet(url)?.trim() ?: continue
            if (IP_SHAPE.matches(ip)) return ip
        }
        return null
    }

    private fun lookup(ip: String): IpInfo? {
        val first = httpGet("https://ipwho.is/$ip")
        if (first != null) {
            try {
                val d = JSONObject(first)
                if (d.optBoolean("success", false)) {
                    val conn = d.optJSONObject("connection")
                    val info = IpInfo(
                        ip,
                        clean(d.optString("country")),
                        cleanCode(d.optString("country_code")),
                        clean(d.optString("city")),
                        clean(conn?.optString("isp")),
                    )
                    if (info.country.isNotEmpty() || info.code.isNotEmpty()) return info
                }
            } catch (e: Exception) {
                // try the next source
            }
        }
        val second = httpGet("https://ipapi.co/$ip/json/")
        if (second != null) {
            try {
                val d = JSONObject(second)
                if (!d.has("error")) {
                    val info = IpInfo(
                        ip,
                        clean(d.optString("country_name")),
                        cleanCode(d.optString("country_code")),
                        clean(d.optString("city")),
                        clean(d.optString("org")),
                    )
                    if (info.country.isNotEmpty() || info.code.isNotEmpty()) return info
                }
            } catch (e: Exception) {
                // give up below
            }
        }
        return null
    }

    /** One request that returns both the IP and its location (faster than two). */
    private fun lookupSelf(): IpInfo? {
        val body = httpGet("https://ipwho.is/") ?: return null
        try {
            val d = JSONObject(body)
            if (!d.optBoolean("success", false)) return null
            val ip = d.optString("ip").trim()
            if (!IP_SHAPE.matches(ip)) return null
            val conn = d.optJSONObject("connection")
            val info = IpInfo(
                ip,
                clean(d.optString("country")),
                cleanCode(d.optString("country_code")),
                clean(d.optString("city")),
                clean(conn?.optString("isp")),
            )
            return if (info.country.isNotEmpty() || info.code.isNotEmpty()) info else null
        } catch (e: Exception) {
            return null
        }
    }

    /** Current public IP with location, or null when there is no internet. */
    fun fetch(): IpInfo? {
        val quick = lookupSelf()
        if (quick != null) return quick
        // that service is down: ask for the IP and the location separately, from other services
        val ip = fetchIp() ?: return null
        return lookup(ip) ?: IpInfo(ip, "", "", "", "")
    }

    // -------------------------------------------------------------------- cache
    fun save(context: Context, info: IpInfo) {
        context.getSharedPreferences("cache", Context.MODE_PRIVATE).edit()
            .putString("ip", info.ip)
            .putString("country", info.country)
            .putString("code", info.code)
            .putString("city", info.city)
            .putString("isp", info.isp)
            .apply()
    }

    fun load(context: Context): IpInfo? {
        val p = context.getSharedPreferences("cache", Context.MODE_PRIVATE)
        val ip = p.getString("ip", null) ?: return null
        return IpInfo(
            ip,
            p.getString("country", "") ?: "",
            p.getString("code", "") ?: "",
            p.getString("city", "") ?: "",
            p.getString("isp", "") ?: "",
        )
    }

    // ------------------------------------------------------------------ display
    private fun flagEmoji(code: String): String {
        if (code.length != 2) return PIRATE
        val sb = StringBuilder()
        for (c in code) sb.appendCodePoint(0x1F1E6 + (c - 'A'))
        return sb.toString()
    }

    fun describe(context: Context, info: IpInfo): Display {
        val detail = listOf(info.city, info.isp).filter { it.isNotEmpty() }.joinToString(" - ")
        val title = if (info.country.isNotEmpty()) info.country else context.getString(R.string.unknown_location)
        return Display(flagEmoji(info.code), title, info.ip, detail)
    }

    fun checking(context: Context) = Display(PIRATE, context.getString(R.string.checking), "", "")

    fun offline(context: Context) = Display(PIRATE, context.getString(R.string.no_internet), "", "")
}
