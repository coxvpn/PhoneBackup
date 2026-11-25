package com.phonebackup.helper

import android.content.Context
import android.content.pm.ApplicationInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.Writer

object AppsIO {

    /** اول {"ok":true,"count":N} و بعد N خط: p=پکیج n=نام برنامه v=نسخه s=حجم k=نوع f=مسیر فایل‌های APK */
    fun stream(ctx: Context, w: Writer) {
        val pm = ctx.packageManager
        val items = ArrayList<JSONObject>()
        for (pi in pm.getInstalledPackages(0)) {
            val ai = pi.applicationInfo ?: continue
            if (pi.packageName == ctx.packageName) continue
            val sys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val upd = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            // k: 0 = برنامه‌ی کاربر، 1 = سیستمی، 2 = سیستمی به‌روزشده
            val kind = if (!sys) 0 else if (upd) 2 else 1

            val paths = ArrayList<String>()
            if (ai.sourceDir != null) paths.add(ai.sourceDir)
            val splits = ai.splitSourceDirs
            if (splits != null) for (s in splits) paths.add(s)
            if (paths.isEmpty()) continue

            var size = 0L
            for (p in paths) size += File(p).length()
            val label = try { ai.loadLabel(pm).toString() } catch (_: Exception) { pi.packageName }

            items.add(
                JSONObject()
                    .put("p", pi.packageName)
                    .put("n", label)
                    .put("v", pi.versionName ?: "")
                    .put("s", size)
                    .put("k", kind)
                    .put("f", JSONArray(paths))
            )
        }
        items.sortBy { it.getString("n").lowercase() }
        w.write(JSONObject().put("ok", true).put("count", items.size).toString() + "\n")
        for (o in items) {
            w.write(o.toString())
            w.write("\n")
        }
    }
}
