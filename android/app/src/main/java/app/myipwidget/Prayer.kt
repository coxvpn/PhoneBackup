package app.myipwidget

import android.content.Context
import java.util.Calendar
import java.util.TimeZone

/** One prayer call: its name (a string id) and the moment it happens. */
data class Azan(val nameRes: Int, val time: Long)

/**
 * The three calls to prayer (Shia times, University of Tehran Institute of Geophysics method:
 * dawn 17.7 degrees, sunset 4.5 degrees). Worked out on the phone from the date and the city,
 * so no internet is needed.
 */
object Prayer {
    private const val FAJR_ANGLE = 17.7
    private const val MAGHRIB_ANGLE = 4.5

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    private fun fixHour(a: Double): Double {
        val r = a - 24.0 * Math.floor(a / 24.0)
        return r
    }

    private fun fixAngle(a: Double): Double = a - 360.0 * Math.floor(a / 360.0)

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = Math.floor(y / 100.0)
        val b = 2 - a + Math.floor(a / 4.0)
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Declination (degrees) and equation of time (hours) of the sun at a Julian date. */
    private fun sun(jd: Double): DoubleArray {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * Math.sin(rad(g)) + 0.020 * Math.sin(rad(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = deg(Math.atan2(Math.cos(rad(e)) * Math.sin(rad(l)), Math.cos(rad(l)))) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = deg(Math.asin(Math.sin(rad(e)) * Math.sin(rad(l))))
        return doubleArrayOf(decl, eqt)
    }

    private fun midDay(jd: Double, t: Double): Double = fixHour(12 - sun(jd + t)[1])

    /** Time (hours, local solar) when the sun is [angle] degrees below the horizon; before noon when [before]. */
    private fun angleTime(jd: Double, lat: Double, angle: Double, t: Double, before: Boolean): Double {
        val decl = sun(jd + t)[0]
        val x = (-Math.sin(rad(angle)) - Math.sin(rad(decl)) * Math.sin(rad(lat))) /
            (Math.cos(rad(decl)) * Math.cos(rad(lat)))
        val v = deg(Math.acos(x.coerceIn(-1.0, 1.0))) / 15.0
        return midDay(jd, t) + (if (before) -v else v)
    }

    /** Dawn, noon and sunset of one day, as hours of the local clock. */
    private fun hours(year: Int, month: Int, day: Int, lat: Double, lon: Double, zone: Double): DoubleArray {
        val jd = julian(year, month, day) - lon / (15.0 * 24.0)
        var fajr = 5.0
        var dhuhr = 12.0
        var maghrib = 18.0
        repeat(2) {
            fajr = angleTime(jd, lat, FAJR_ANGLE, fajr / 24.0, true)
            dhuhr = midDay(jd, dhuhr / 24.0)
            maghrib = angleTime(jd, lat, MAGHRIB_ANGLE, maghrib / 24.0, false)
        }
        val shift = zone - lon / 15.0
        return doubleArrayOf(fajr + shift, dhuhr + shift, maghrib + shift)
    }

    private val NAMES = intArrayOf(R.string.azan_fajr, R.string.azan_dhuhr, R.string.azan_maghrib)

    /** The calls of the day that [base] falls in, in time order. */
    private fun day(base: Calendar, lat: Double, lon: Double): List<Azan> {
        val zone = base.timeZone.getOffset(base.timeInMillis) / 3600000.0
        val h = hours(base.get(Calendar.YEAR), base.get(Calendar.MONTH) + 1, base.get(Calendar.DAY_OF_MONTH), lat, lon, zone)
        val midnight = (base.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return h.indices.map { Azan(NAMES[it], midnight + Math.round(h[it] * 3600000.0)) }
    }

    /** The next call after [now], or null when no city is chosen. */
    fun next(context: Context, now: Long = System.currentTimeMillis()): Azan? {
        val lat = Prefs.cityLat(context) ?: return null
        val lon = Prefs.cityLon(context) ?: return null
        val today = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = now }
        day(today, lat, lon).firstOrNull { it.time > now }?.let { return it }
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        return day(tomorrow, lat, lon).firstOrNull()
    }

    /** "02:10" or "1:05:09" style is left to the caller; this gives hours and minutes left, rounded up. */
    fun remainingText(millis: Long): String {
        val minutes = (millis + 59_999L) / 60_000L
        val h = minutes / 60
        val m = minutes % 60
        return PersianDate.digits(h.toInt()) + ":" + PersianDate.digits(m.toInt()).padStart(2, PersianDate.digits(0)[0])
    }

    /** The clock time of a call, like "17:02". */
    fun clockText(time: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        return PersianDate.digits(h) + ":" + PersianDate.digits(m).padStart(2, PersianDate.digits(0)[0])
    }
}
