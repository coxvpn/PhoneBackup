package app.myipwidget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the new version from GitHub and hands it to the phone's installer (the phone asks "install?"). */
object Updater {
    /** Downloads to the app's cache; [onProgress] gets 0..100 (or -1 when the size is unknown). Returns null on failure. */
    fun download(context: Context, url: String, onProgress: (Int) -> Unit): File? {
        var conn: HttpURLConnection? = null
        return try {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "update.apk")
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mostafa-MultiWidget-Android")
            }
            if (conn.responseCode != 200) return null
            val total = conn.contentLengthLong
            var done = 0L
            var last = -2
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != last) { last = percent; onProgress(percent) }
                    }
                }
            }
            if (file.length() < 100_000L) null else file
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Gives the file to the system installer. The result comes to [UpdateReceiver]. */
    fun install(context: Context, file: File): Boolean {
        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("update.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, UpdateReceiver::class.java).setAction("app.myipwidget.UPDATE_DONE")
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, id, intent, flags)
                session.commit(pending.intentSender)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** The installer's answer: it needs the person's "install" tap (shown here), or it failed. */
class UpdateReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(confirm) } catch (e: Exception) { }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> { }
            else -> Toast.makeText(context, context.getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
        }
    }
}
