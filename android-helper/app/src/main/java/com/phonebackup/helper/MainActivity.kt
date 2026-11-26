package com.phonebackup.helper

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.nio.charset.StandardCharsets

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var resultText: TextView

    companion object {
        const val REQ_PICK_CONTACTS = 101
        const val REQ_PICK_SMS = 102
        const val REQ_SAVE_CONTACTS = 103
        const val REQ_SAVE_SMS = 104
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(48, 120, 48, 48)
        }
        status = TextView(this).apply { textSize = 17f }
        layout.addView(status)

        val smsRoleBtn = Button(this).apply {
            text = "تنظیم به‌عنوان برنامه پیامک پیش‌فرض"
            setOnClickListener { requestSmsRole() }
        }
        layout.addView(smsRoleBtn, margin(0, 24, 0, 0))

        val backupTitle = TextView(this).apply {
            text = "\nبکاپ‌گیری مستقیم از گوشی (بدون کامپیوتر)\nهر دکمه یک فایل JSON می‌سازد؛ محل ذخیره را خودت انتخاب می‌کنی."
            textSize = 15f
        }
        layout.addView(backupTitle, margin(0, 32, 0, 0))

        val backupContactsBtn = Button(this).apply {
            text = "بکاپ مخاطبین به فایل"
            setOnClickListener { saveFile(REQ_SAVE_CONTACTS, "contacts.json") }
        }
        layout.addView(backupContactsBtn, margin(0, 12, 0, 0))

        val backupSmsBtn = Button(this).apply {
            text = "بکاپ پیامک‌ها به فایل"
            setOnClickListener { saveFile(REQ_SAVE_SMS, "sms.json") }
        }
        layout.addView(backupSmsBtn, margin(0, 12, 0, 0))

        val backupAppsBtn = Button(this).apply {
            text = "بکاپ برنامه‌ها (انتخابی)"
            setOnClickListener { startActivity(Intent(this@MainActivity, AppsActivity::class.java)) }
        }
        layout.addView(backupAppsBtn, margin(0, 12, 0, 0))

        val sectionTitle = TextView(this).apply {
            text = "\nبازیابی دستی از روی فایل بکاپ\n(اگر برنامه‌ی ویندوز فایل را روی گوشی کپی کرده، معمولاً در\nDownload/PhoneBackup_Restore)"
            textSize = 15f
        }
        layout.addView(sectionTitle, margin(0, 32, 0, 0))

        val contactsBtn = Button(this).apply {
            text = "انتخاب فایل و بازیابی مخاطبین"
            setOnClickListener { pickFile(REQ_PICK_CONTACTS) }
        }
        layout.addView(contactsBtn, margin(0, 12, 0, 0))

        val smsBtn = Button(this).apply {
            text = "انتخاب فایل و بازیابی پیامک‌ها"
            setOnClickListener {
                if (Telephony.Sms.getDefaultSmsPackage(this@MainActivity) != packageName) {
                    Toast.makeText(
                        this@MainActivity,
                        "اول دکمه‌ی «تنظیم به‌عنوان برنامه پیامک پیش‌فرض» را بزن و تأیید کن",
                        Toast.LENGTH_LONG
                    ).show()
                    requestSmsRole()
                } else {
                    pickFile(REQ_PICK_SMS)
                }
            }
        }
        layout.addView(smsBtn, margin(0, 12, 0, 0))

        resultText = TextView(this).apply { textSize = 15f }
        layout.addView(resultText, margin(0, 24, 0, 0))

        val aboutBtn = Button(this).apply {
            text = "درباره"
            setOnClickListener { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) }
        }
        layout.addView(aboutBtn, margin(0, 32, 0, 0))

        setContentView(ScrollView(this).apply { addView(layout) })
        handle(intent)
    }

    private fun margin(l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(l, t, r, b) }

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

    // ---------------- بازیابی دستی از روی یک فایل JSON که خود کاربر روی گوشی انتخاب می‌کند ----------------

    private fun saveFile(requestCode: Int, suggestedName: String) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }
        try {
            startActivityForResult(i, requestCode)
        } catch (_: Exception) {
            Toast.makeText(this, "برنامه‌ی ذخیره‌ی فایل روی این گوشی پیدا نشد", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickFile(requestCode: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(Intent.createChooser(i, "انتخاب فایل JSON"), requestCode)
        } catch (_: Exception) {
            Toast.makeText(this, "برنامه‌ی انتخاب فایل روی این گوشی پیدا نشد", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        when (requestCode) {
            REQ_PICK_CONTACTS, REQ_PICK_SMS -> restoreFromUri(requestCode, uri)
            REQ_SAVE_CONTACTS, REQ_SAVE_SMS -> backupToUri(requestCode, uri)
        }
    }

    private fun restoreFromUri(requestCode: Int, uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            } ?: throw Exception("فایل خوانده نشد")
            val arr = JSONArray(text)

            if (requestCode == REQ_PICK_CONTACTS) {
                val r = ContactsIO.write(this, arr, true)
                showResult("مخاطبین بازیابی شدند", r)
            } else {
                if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
                    resultText.text = "این برنامه هنوز پیامک پیش‌فرض نیست؛ اول آن را تنظیم کن و دوباره تلاش کن."
                    return
                }
                val r = SmsIO.write(this, arr, true)
                showResult("پیامک‌ها بازیابی شدند", r)
            }
        } catch (e: Exception) {
            resultText.text = "خطا: " + (e.message ?: e.toString())
        }
    }

    private fun backupToUri(requestCode: Int, uri: Uri) {
        try {
            val arr = if (requestCode == REQ_SAVE_CONTACTS) ContactsIO.read(this) else SmsIO.readAll(this)
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(arr.toString().toByteArray(StandardCharsets.UTF_8))
            } ?: throw Exception("فایل ساخته نشد")
            val label = if (requestCode == REQ_SAVE_CONTACTS) "مخاطبین" else "پیامک‌ها"
            resultText.text = "$label ذخیره شد: ${arr.length()} مورد."
            Toast.makeText(this, resultText.text, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            resultText.text = "خطا: " + (e.message ?: e.toString())
        }
    }

    private fun showResult(title: String, r: org.json.JSONObject) {
        val added = r.optInt("added", 0)
        val skipped = r.optInt("skipped", 0)
        resultText.text = "$title: $added مورد اضافه شد، $skipped مورد تکراری رد شد."
        Toast.makeText(this, resultText.text, Toast.LENGTH_LONG).show()
    }

    private fun refresh() {
        val def = Telephony.Sms.getDefaultSmsPackage(this) == packageName
        status.text = "این برنامه فقط برای ارتباط با برنامه ویندوز یا بازیابی دستی است.\n\n" +
            "سرویس: " + (if (ServerService.running) "فعال" else "غیرفعال") + "\n" +
            "پیامک پیش‌فرض: " + (if (def) "بله" else "خیر") + "\n\n" +
            "بعد از پایان بازیابی پیامک‌ها، برنامه پیامک قبلی خود را دوباره پیش‌فرض کنید."
    }
}
