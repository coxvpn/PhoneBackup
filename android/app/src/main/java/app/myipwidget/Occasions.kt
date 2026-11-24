package app.myipwidget

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Calendar

/** What today is: an official holiday or a well-known night. [holiday] is true for a day off. */
data class Occasion(val text: String, val holiday: Boolean)

object Occasions {
    // Persian calendar: (month, day) -> name
    private val FIXED = mapOf(
        (1 to 1) to "نوروز", (1 to 2) to "نوروز", (1 to 3) to "نوروز", (1 to 4) to "نوروز",
        (1 to 12) to "روز جمهوری اسلامی", (1 to 13) to "سیزده‌بدر",
        (3 to 14) to "رحلت امام خمینی", (3 to 15) to "قیام ۱۵ خرداد",
        (11 to 22) to "پیروزی انقلاب", (12 to 29) to "ملی شدن نفت",
    )

    // Islamic calendar (month 1 = Muharram): (month, day) -> name. The phone works the date out itself,
    // so it can be one day away from the day the Iranian calendar announces.
    private val HIJRI = mapOf(
        (1 to 9) to "تاسوعا", (1 to 10) to "عاشورا", (2 to 20) to "اربعین",
        (2 to 28) to "رحلت پیامبر و شهادت امام حسن", (3 to 8) to "شهادت امام حسن عسکری",
        (3 to 17) to "میلاد پیامبر", (6 to 3) to "شهادت حضرت فاطمه", (7 to 13) to "ولادت امام علی",
        (7 to 27) to "مبعث", (8 to 15) to "نیمه شعبان", (9 to 21) to "شهادت امام علی",
        (10 to 1) to "عید فطر", (10 to 2) to "تعطیل عید فطر", (10 to 25) to "شهادت امام صادق",
        (12 to 10) to "عید قربان", (12 to 18) to "عید غدیر",
    )

    fun today(now: Calendar = Calendar.getInstance()): Occasion? {
        val gy = now.get(Calendar.YEAR)
        val gm = now.get(Calendar.MONTH) + 1
        val gd = now.get(Calendar.DAY_OF_MONTH)
        val (_, pm, pd) = PersianDate.fromGregorian(gy, gm, gd)
        FIXED[pm to pd]?.let { return Occasion(it, true) }
        try {
            val h = HijrahDate.from(LocalDate.of(gy, gm, gd))
            val hm = h.get(ChronoField.MONTH_OF_YEAR)
            val hd = h.get(ChronoField.DAY_OF_MONTH)
            HIJRI[hm to hd]?.let { return Occasion(it, true) }
            // the last day of Safar: the martyrdom of Imam Reza
            if (hm == 2 && hd >= 29 && hd == h.lengthOfMonth()) return Occasion("شهادت امام رضا", true)
        } catch (e: Exception) {
            // no Islamic calendar: only the Persian dates
        }
        if (pm == 9 && pd == 30) return Occasion("شب یلدا", false)
        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) return Occasion("جمعه", true)
        return null
    }

    /** Only the little sign (the widget and the notification show just this). */
    fun sign(o: Occasion): String = if (o.holiday) "🔴" else "🌙"

    /** The text with its little sign, for the widget and the notification. */
    fun label(o: Occasion): String =
        if (o.holiday) "🔴 " + o.text + (if (o.text.contains("تعطیل")) "" else " (تعطیل)") else "🌙 " + o.text
}
