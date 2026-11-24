package com.phonebackup.helper

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(48, 120, 48, 48)
        }
        status = TextView(this).apply { textSize = 17f }
        layout.addView(status)
        val btn = Button(this).apply {
            text = "تنظیم به‌عنوان برنامه پیامک پیش‌فرض"
            setOnClickListener { requestSmsRole() }
        }
        layout.addView(btn)
        setContentView(layout)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun handle(i: Intent) {
        i.getStringExtra("token")?.let { startServer(it) }
        if (i.getBooleanExtra("request_sms_role", false)) requestSmsRole() else requestMissingPermissions()
    }

    private fun startServer(token: String) {
        val s = Intent(this, ServerService::class.java).putExtra("token", token)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s) else startService(s)
    }

    private fun requestMissingPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) wanted.add("android.permission.POST_NOTIFICATIONS")
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 2)
    }

    private fun requestSmsRole() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 1)
                }
            } else {
                val i = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivity(i)
            }
        } catch (_: Exception) {
        }
    }

    private fun refresh() {
        val def = Telephony.Sms.getDefaultSmsPackage(this) == packageName
        status.text = "این برنامه فقط برای ارتباط با برنامه ویندوز است.\n\n" +
            "سرویس: " + (if (ServerService.running) "فعال" else "غیرفعال") + "\n" +
            "پیامک پیش‌فرض: " + (if (def) "بله" else "خیر") + "\n\n" +
            "بعد از پایان بازیابی پیامک‌ها، برنامه پیامک قبلی خود را دوباره پیش‌فرض کنید."
    }
}
