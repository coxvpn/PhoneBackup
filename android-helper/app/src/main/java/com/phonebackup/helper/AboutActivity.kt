package com.phonebackup.helper

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "درباره"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(48, 48, 48, 48)
        }

        val appTitle = TextView(this).apply {
            text = "پشتیبان گوشی"
            textSize = 22f
        }
        layout.addView(appTitle)

        val version = try {
            val pi = if (Build.VERSION.SDK_INT >= 33)
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            else
                @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
            pi.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        if (version.isNotEmpty()) {
            layout.addView(TextView(this).apply {
                text = "نسخه $version"
                textSize = 14f
                setTextColor(0xFF777777.toInt())
            }, margin(0, 6, 0, 0))
        }

        layout.addView(TextView(this).apply {
            text = "این برنامه فقط برای ارتباط با برنامه‌ی ویندوز «پشتیبان‌گیری و بازیابی گوشی اندروید» " +
                "و بازیابی/بکاپ دستی مخاطبین، پیامک‌ها و برنامه‌ها روی خود گوشی ساخته شده است."
            textSize = 15f
        }, margin(0, 20, 0, 0))

        layout.addView(TextView(this).apply {
            text = "سازنده: مصطفی ماندگاری"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, margin(0, 24, 0, 0))

        setContentView(layout)
    }

    private fun margin(l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(l, t, r, b) }
}
