package com.phonebackup.helper

import android.content.Context
import android.content.pm.ApplicationInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.Writer

data class AppEntry(
    val pkg: String,
    val name: String,
    val version: String,
    val size: Long,
    val kind: Int, // 0 = کاربر، 1 = سیستمی، 2 = سیستمی به‌روزشده
    val paths: List<String>
)

object AppsIO {

    /** لیست برنامه‌های قابل بکاپ (کاربر + سیستمی به‌روزشده + سیستمی) را جمع می‌کند، مرتب بر اساس نام. */
    fun collect(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val items = ArrayList<AppEntry>()
        for (pi in pm.getInstalledPackages(0)) {
            val ai = pi.applicationInfo ?: continue
            if (pi.packageName == ctx.packageName) continue
            val sys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val upd = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val kind = if (!sys) 0 else if (upd) 2 else 1

            val paths = ArrayList<String>()
            if (ai.sourceDir != null) paths.add(ai.sourceDir)
            ai.splitSourceDirs?.let { paths.addAll(it) }
            if (paths.isEmpty()) continue

            var size = 0L
            for (p in paths) size += File(p).length()
            val label = try { ai.loadLabel(pm).toString() } catch (_: Exception) { pi.packageName }

            items.add(AppEntry(pi.packageName, label, pi.versionName ?: "", size, kind, paths))
        }
        return items.sortedBy { it.name.lowercase() }
    }

    /** اول {"ok":true,"count":N} و بعد N خط: p=پکیج n=نام برنامه v=نسخه s=حجم k=نوع f=مسیر فایل‌های APK */
    fun stream(ctx: Context, w: Writer) {
        val items = collect(ctx)
        w.write(JSONObject().put("ok", true).put("count", items.size).toString() + "\n")
        for (a in items) {
            val o = JSONObject()
                .put("p", a.pkg)
                .put("n", a.name)
                .put("v", a.version)
                .put("s", a.size)
                .put("k", a.kind)
                .put("f", JSONArray(a.paths))
            w.write(o.toString())
            w.write("\n")
        }
    }
}
