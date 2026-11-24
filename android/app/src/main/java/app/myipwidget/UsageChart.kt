package app.myipwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Locale

/** Seven bars, one per day: Wi-Fi below, SIM above. The last bar (today) has a yellow outline. */
class UsageChart(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var data: List<Usage.Today> = emptyList()
        set(value) { field = value; invalidate() }
    var labels: List<String> = emptyList()
    var textColor: Int = Color.GRAY

    private val wifiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00A0FF") }
    private val simPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EE5A9C") }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9D423"); style = Paint.Style.STROKE; strokeWidth = 3f * resources.displayMetrics.density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 12f * resources.displayMetrics.scaledDensity
    }

    private fun short(bytes: Long): String {
        val gb = bytes / 1073741824.0
        return if (gb >= 1) String.format(Locale.US, "%.1fG", gb) else String.format(Locale.US, "%dM", bytes / 1048576)
    }

    override fun onDraw(canvas: Canvas) {
        if (data.isEmpty()) return
        text.color = textColor
        val top = text.textSize * 1.8f
        val bottom = text.textSize * 1.9f
        val baseY = height - bottom
        val maxTotal = data.maxOf { maxOf(0L, it.wifi) + maxOf(0L, it.mobile) }.coerceAtLeast(1L)
        val slot = width.toFloat() / data.size
        val barW = slot * 0.55f
        val round = 8f * resources.displayMetrics.density
        for ((i, d) in data.withIndex()) {
            val wifi = maxOf(0L, d.wifi)
            val sim = maxOf(0L, d.mobile)
            val total = wifi + sim
            val full = (baseY - top) * total / maxTotal
            val wifiH = if (total == 0L) 0f else full * wifi / total
            val left = slot * i + (slot - barW) / 2
            val right = left + barW
            canvas.drawRoundRect(left, baseY - wifiH, right, baseY, round, round, wifiPaint)
            canvas.drawRoundRect(left, baseY - full, right, baseY - wifiH, round, round, simPaint)
            if (i == data.size - 1) canvas.drawRoundRect(left - 2f, baseY - full - 2f, right + 2f, baseY + 2f, round, round, todayPaint)
            canvas.drawText(short(total), slot * i + slot / 2, baseY - full - text.textSize * 0.5f, text)
            canvas.drawText(labels.getOrElse(i) { "" }, slot * i + slot / 2, height - text.textSize * 0.5f, text)
        }
    }
}
