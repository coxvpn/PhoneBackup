package app.myipwidget

import android.content.Context
import java.util.Locale

/**
 * The dollar rate, read from the newest post of a public channel page that mentions "دلار"
 * (the page has no fixed layout we can rely on, so the page text is searched, not its tags).
 */
object Rate {
    const val URL = "https://eitaa.com/talaclinic1"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("rate", Context.MODE_PRIVATE)

    private fun latinDigits(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    '٬', '،' -> ','
                    else -> c
                },
            )
        }
    }

    /** The newest number written after the word "دلار" (as digits only), or null. */
    fun parse(html: String): String? {
        val text = latinDigits(
            html.replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").replace(Regex("&[a-zA-Z#0-9]+;"), " "),
        )
        // the line looks like "دلار: 268,300 تومان"; a number after the word is the fallback
        val match = Regex("دلار\\s*[:：]\\s*([0-9][0-9,.]{3,})").findAll(text).lastOrNull()
            ?: Regex("دلار[^0-9]{0,40}([0-9][0-9,.]{3,})").findAll(text).lastOrNull()
            ?: return null
        val digits = match.groupValues[1].filter { it.isDigit() }
        return if (digits.length in 4..9) digits else null
    }

    fun cached(context: Context): String? = prefs(context).getString("digits", null)

    /** Reads the page again if the saved rate is older than [maxAgeMs]. Call from a background thread. */
    fun refreshIfStale(context: Context, maxAgeMs: Long) {
        if (!Prefs.showRate(context)) return
        val p = prefs(context)
        val age = System.currentTimeMillis() - p.getLong("time", 0L)
        if (p.contains("digits") && age in 0..maxAgeMs) return
        val body = IpLookup.httpGet(URL, 600_000) ?: return
        val digits = parse(body) ?: return
        p.edit().putString("digits", digits).putLong("time", System.currentTimeMillis()).apply()
    }

    /** "$ 98,500" */
    fun format(digits: String?): String {
        val value = digits?.toLongOrNull() ?: return "$ …"
        return "$ " + "%,d".format(Locale.US, value)
    }
}
