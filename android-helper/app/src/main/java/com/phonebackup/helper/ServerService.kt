package com.phonebackup.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * یک سرور ساده روی localhost گوشی (فقط از طریق adb forward در دسترس است).
 * پروتکل: هر خط یک JSON. هر درخواست باید token درست داشته باشد.
 */
class ServerService : Service() {

    companion object {
        const val PORT = 38455
        @Volatile var token: String = ""
        @Volatile var running = false
    }

    @Volatile private var server: ServerSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("token")?.let { token = it }
        goForeground()
        if (!running) {
            running = true
            thread { serve() }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val builder: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("srv", "Backup", NotificationManager.IMPORTANCE_LOW))
            builder = Notification.Builder(this, "srv")
        } else {
            builder = Notification.Builder(this)
        }
        val n = builder
            .setContentTitle("پشتیبان گوشی")
            .setContentText("متصل به رایانه")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, n)
        }
    }

    private fun serve() {
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
            server = ss
            while (!ss.isClosed) {
                val s = ss.accept()
                thread {
                    try { handle(s) } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) {
        } finally {
            running = false
            server = null
        }
    }

    private fun send(w: Writer, o: JSONObject) {
        w.write(o.toString())
        w.write("\n")
    }

    private fun handle(sock: Socket) {
        sock.use {
            val r = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8), 1 shl 16)
            val w = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), 1 shl 16)
            var done = false
            while (!done) {
                val line = r.readLine() ?: break
                try {
                    val req = JSONObject(line)
                    if (token.isEmpty() || req.optString("token") != token) throw SecurityException("bad token")
                    when (req.optString("cmd")) {
                        "ping" -> send(w, JSONObject()
                            .put("ok", true)
                            .put("sdk", Build.VERSION.SDK_INT)
                            .put("defaultSms", Telephony.Sms.getDefaultSmsPackage(this) == packageName)
                            .put("defaultPkg", Telephony.Sms.getDefaultSmsPackage(this) ?: ""))
                        "get_contacts" -> {
                            val a = ContactsIO.read(this)
                            send(w, JSONObject().put("ok", true).put("count", a.length()))
                            for (i in 0 until a.length()) send(w, a.getJSONObject(i))
                        }
                        "put_contacts" -> send(w, ContactsIO.write(this, req.getJSONArray("data"), req.optBoolean("first")).put("ok", true))
                        "get_sms" -> SmsIO.stream(this, w)
                        "get_apps" -> AppsIO.stream(this, w)
                        "put_sms" -> send(w, SmsIO.write(this, req.getJSONArray("data"), req.optBoolean("first")).put("ok", true))
                        "stop" -> {
                            send(w, JSONObject().put("ok", true))
                            done = true
                            server?.close()
                            stopSelf()
                        }
                        else -> throw IllegalArgumentException("unknown command")
                    }
                } catch (e: Exception) {
                    send(w, JSONObject().put("ok", false).put("error", e.message ?: e.toString()))
                }
                w.flush()
            }
        }
    }
}
