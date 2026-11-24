package app.myipwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The two alerts (battery full, rain or snow coming): a notification and a spoken sentence, in the language chosen in the app. */
object Alerts {
    private const val CHANNEL = "alerts"
    private const val ID_CHARGE = 7001
    private const val ID_RAIN = 7002

    private fun text(context: Context): Context = Prefs.localized(context.applicationContext)

    private fun show(context: Context, id: Int, title: String, body: String) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, text(app).getString(R.string.alert_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = android.app.Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { manager.notify(id, notification) } catch (e: Exception) { }
    }

    /** Says the sentence with the phone's own voice, in the app's language. False when the phone has no such voice. */
    fun speak(context: Context, sentence: String): Boolean {
        val latch = CountDownLatch(1)
        var spoken = false
        var tts: TextToSpeech? = null
        val locale = Locale(Prefs.language(context))
        Handler(Looper.getMainLooper()).post {
            tts = TextToSpeech(context.applicationContext) { status ->
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null || engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                    latch.countDown()
                } else {
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { spoken = true; latch.countDown() }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { latch.countDown() }
                    })
                    engine.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "alert")
                }
            }
        }
        latch.await(15, TimeUnit.SECONDS)
        Handler(Looper.getMainLooper()).post { try { tts?.shutdown() } catch (e: Exception) { } }
        return spoken
    }

    // ------------------------------------------------------------------ battery full
    private val sticky = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    /** Called with every battery change: alerts once when the charger is plugged and the battery has reached 100%. */
    fun checkCharge(context: Context, battery: Intent?) {
        battery ?: return
        val app = context.applicationContext
        val level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        val plugged = battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        val status = battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val full = plugged && (percent >= 100 || status == android.os.BatteryManager.BATTERY_STATUS_FULL)
        val p = app.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        if (!plugged || percent in 0..94) {
            if (p.getBoolean("charge_done", false)) p.edit().putBoolean("charge_done", false).apply()
            return
        }
        if (!full || p.getBoolean("charge_done", false) || !Prefs.chargeAlert(app)) return
        p.edit().putBoolean("charge_done", true).apply()
        val t = text(app)
        val title = t.getString(R.string.alert_charge_title)
        val body = t.getString(R.string.alert_charge_text)
        show(app, ID_CHARGE, title, body)
        Thread { speak(app, body) }.start()
    }

    fun currentBattery(context: Context): Intent? = context.registerReceiver(null, sticky)

    // ------------------------------------------------------------------ rain or snow
    /** [kind] 1 = rain, 2 = snow; [hours] = how many hours from now. Alerts at most once in four hours. */
    fun rain(context: Context, kind: Int, hours: Int) {
        val app = context.applicationContext
        if (!Prefs.rainAlert(app)) return
        val p = app.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("rain_time", 0L) < 4 * 60 * 60 * 1000L) return
        p.edit().putLong("rain_time", now).apply()
        val t = text(app)
        val title = t.getString(if (kind == 2) R.string.alert_snow_title else R.string.alert_rain_title)
        val body = t.getString(if (kind == 2) R.string.alert_snow_text else R.string.alert_rain_text, hours)
        show(app, ID_RAIN, title, body)
        Thread { speak(app, body) }.start()
    }
}
