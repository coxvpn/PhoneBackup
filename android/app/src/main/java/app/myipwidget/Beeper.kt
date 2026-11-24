package app.myipwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.net.Uri
import android.os.Build
import java.util.Calendar

/** The beeps: up to five times of day, each on the weekdays the person ticked. */
object Beeper {
    const val SLOTS = 5
    const val ACTION = "app.myipwidget.BEEP"

    /** The next moment any ticked beep is due, or null when none is on. */
    fun nextTime(context: Context, now: Long = System.currentTimeMillis()): Long? {
        if (!Prefs.beepEnabled(context)) return null
        var best: Long? = null
        for (slot in 0 until SLOTS) {
            if (!Prefs.beepOn(context, slot)) continue
            val mask = Prefs.beepDays(context, slot)
            if (mask == 0) continue
            for (offset in 0..7) {
                val c = Calendar.getInstance()
                c.timeInMillis = now
                c.add(Calendar.DAY_OF_YEAR, offset)
                c.set(Calendar.HOUR_OF_DAY, Prefs.beepHour(context, slot))
                c.set(Calendar.MINUTE, Prefs.beepMinute(context, slot))
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                // Calendar.SUNDAY is 1 ... SATURDAY is 7
                val bit = 1 shl (c.get(Calendar.DAY_OF_WEEK) - 1)
                if (mask and bit != 0 && c.timeInMillis > now + 1000L) {
                    if (best == null || c.timeInMillis < best) best = c.timeInMillis
                    break
                }
            }
        }
        return best
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 11, Intent(context, BeepReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets one alarm for the next beep (or removes it when there is none). Call after any change. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        try {
            val alarms = app.getSystemService(AlarmManager::class.java) ?: return
            val pending = pending(app)
            alarms.cancel(pending)
            val at = nextTime(app) ?: return
            val exactOk = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
            if (exactOk) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (e: Exception) {
            // without an alarm there is simply no beep
        }
    }

    // each melody: pairs of (frequency in Hz, length in ms); 0 Hz is a short rest
    private val MELODIES = arrayOf(
        intArrayOf(1568, 120, 2093, 120, 2637, 420),
        intArrayOf(1760, 350),
        intArrayOf(1319, 200, 1760, 400),
        intArrayOf(988, 500),
        intArrayOf(1568, 100, 1760, 100, 2093, 100, 2349, 350),
        intArrayOf(880, 180, 1175, 420),
        intArrayOf(1047, 130, 1319, 130, 1568, 130, 2093, 380),
        intArrayOf(2093, 260),
        intArrayOf(1175, 160, 988, 160, 1175, 400),
        intArrayOf(2349, 90, 2637, 90, 3136, 380),
        intArrayOf(523, 110, 659, 110, 784, 110, 1047, 380),
        intArrayOf(784, 220, 988, 420),
        intArrayOf(1397, 150, 1760, 450),
        intArrayOf(659, 200, 880, 200, 1319, 420),
        intArrayOf(523, 200, 784, 500),
        intArrayOf(1175, 120, 1397, 120, 1175, 120, 1568, 380),
        intArrayOf(1319, 100, 1568, 320),
        intArrayOf(880, 260, 1109, 420),
        intArrayOf(1047, 180, 880, 180, 1047, 480),
        intArrayOf(2093, 110, 1760, 110, 2349, 400),
    )

    const val ACTION_AZAN = "app.myipwidget.AZAN_SOUND"
    private val AZAN_KEYS = listOf("azan_fajr", "azan_dhuhr", "azan_maghrib")

    /** One alarm for the next prayer call, only when a call sound is ticked. Call after any change. */
    fun scheduleAzan(context: Context) {
        val app = context.applicationContext
        try {
            val alarms = app.getSystemService(AlarmManager::class.java) ?: return
            val pending = PendingIntent.getBroadcast(
                app, 12, Intent(app, AzanReceiver::class.java).setAction(ACTION_AZAN),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarms.cancel(pending)
            if (AZAN_KEYS.none { Prefs.azanSound(app, it) }) return
            val azan = Prayer.next(app, System.currentTimeMillis() + 3000L) ?: return
            val withKey = PendingIntent.getBroadcast(
                app, 12,
                Intent(app, AzanReceiver::class.java).setAction(ACTION_AZAN)
                    .putExtra("key", app.resources.getResourceEntryName(azan.nameRes)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val exactOk = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
            if (exactOk) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, azan.time, withKey)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, azan.time, withKey)
        } catch (e: Exception) {
            // no sound at the call
        }
    }

    /** Says "Allahu Akbar" with the phone's own voice (Arabic). False when the phone has no such voice. */
    fun sayAllahuAkbar(context: Context): Boolean {
        val latch = CountDownLatch(1)
        var spoken = false
        var tts: TextToSpeech? = null
        Handler(Looper.getMainLooper()).post {
            tts = TextToSpeech(context.applicationContext) { status ->
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    latch.countDown()
                } else if (engine.setLanguage(Locale("ar")) < TextToSpeech.LANG_AVAILABLE) {
                    latch.countDown()
                } else {
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { spoken = true; latch.countDown() }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { latch.countDown() }
                    })
                    val params = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Prefs.azanVolume(context) / 100f) }
                    engine.speak("الله أكبر، الله أكبر", TextToSpeech.QUEUE_FLUSH, params, "azan")
                }
            }
        }
        latch.await(12, TimeUnit.SECONDS)
        Handler(Looper.getMainLooper()).post { try { tts?.shutdown() } catch (e: Exception) { } }
        return spoken
    }

    private fun synth(melody: IntArray): ShortArray {
        val rate = 22050
        val out = ArrayList<Short>()
        var i = 0
        while (i < melody.size) {
            val freq = melody[i].toDouble()
            val ms = melody[i + 1]
            val n = rate * ms / 1000
            for (k in 0 until n) {
                val t = k.toDouble() / rate
                // a soft attack and a slow fade, a little third harmonic for a bell-like colour
                val attack = minOf(1.0, k / (rate * 0.015))
                val fade = Math.exp(-4.5 * k / n)
                val wave = Math.sin(2 * Math.PI * freq * t) + 0.12 * Math.sin(2 * Math.PI * freq * 3 * t)
                out.add((wave * attack * fade * 0.4 * 16000).toInt().toShort())
            }
            i += 2
        }
        return out.toShortArray()
    }

    /** The sound file picked from the phone; at most 15 seconds of it. */
    /** Plays the 3-second "Allahu Akbar" recording that is inside the app. False when it could not be played. */
    fun playAzanRecording(context: Context): Boolean {
        return try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            val file = context.applicationContext.resources.openRawResourceFd(R.raw.azan_allahu_akbar)
            player.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            file.close()
            player.prepare()
            val volume = Prefs.azanVolume(context) / 100f
            player.setVolume(volume, volume)
            player.start()
            val end = System.currentTimeMillis() + 8000L
            while (player.isPlaying && System.currentTimeMillis() < end) Thread.sleep(100)
            player.release()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun playFile(context: Context) {
        try {
            val uri = Prefs.beepCustomUri(context)
            if (uri.isEmpty()) return
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(context.applicationContext, Uri.parse(uri))
            player.prepare()
            player.start()
            val end = System.currentTimeMillis() + 15000L
            while (player.isPlaying && System.currentTimeMillis() < end) Thread.sleep(200)
            player.release()
        } catch (e: Exception) {
            // the file is gone or cannot be played
        }
    }

    /** Plays one melody and waits until it ends. Runs on its own thread. */
    fun play(context: Context, sound: Int) {
        if (sound >= MELODIES.size) {
            playFile(context)
            return
        }
        try {
            val data = synth(MELODIES[sound.coerceIn(0, MELODIES.size - 1)])
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(22050)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.play()
            Thread.sleep(data.size * 1000L / 22050 + 200)
            track.release()
        } catch (e: Exception) {
            // no sound this time
        }
    }
}

class BeepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                Beeper.play(app, Prefs.beepSound(app))
            } finally {
                Beeper.schedule(app)
                pending.finish()
            }
        }.start()
    }
}

class AzanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: ""
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                if (key.isNotEmpty() && Prefs.azanSound(app, key)) {
                    // the recording of "Allahu Akbar"; if it cannot play, the phone's own voice, then the chosen melody
                    if (!Beeper.playAzanRecording(app) && !Beeper.sayAllahuAkbar(app)) Beeper.play(app, Prefs.beepSound(app))
                }
            } finally {
                Beeper.scheduleAzan(app)
                pending.finish()
            }
        }.start()
    }
}
