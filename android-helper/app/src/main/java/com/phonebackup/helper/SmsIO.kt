package com.phonebackup.helper

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.io.Writer

object SmsIO {

    private var cache = HashSet<String>()

    private fun key(date: Long, type: Int, addr: String?) = "$date|$type|${addr ?: ""}"

    /** اول یک خط {"ok":true,"count":N} و بعد N خط، هر کدام یک پیامک. */
    fun stream(ctx: Context, w: Writer) {
        val proj = arrayOf("address", "body", "date", "date_sent", "type", "read")
        val c = ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, proj, null, null, "date ASC")
        if (c == null) {
            w.write(JSONObject().put("ok", true).put("count", 0).toString() + "\n")
            return
        }
        c.use {
            w.write(JSONObject().put("ok", true).put("count", it.count).toString() + "\n")
            while (it.moveToNext()) {
                val o = JSONObject()
                    .put("a", it.getString(0) ?: "")
                    .put("b", it.getString(1) ?: "")
                    .put("d", it.getLong(2))
                    .put("ds", it.getLong(3))
                    .put("t", it.getInt(4))
                    .put("r", it.getInt(5))
                w.write(o.toString())
                w.write("\n")
            }
        }
    }

    fun write(ctx: Context, arr: JSONArray, first: Boolean): JSONObject {
        if (Telephony.Sms.getDefaultSmsPackage(ctx) != ctx.packageName) {
            throw IllegalStateException("برنامه کمکی هنوز پیامک پیش‌فرض نیست")
        }
        val cr = ctx.contentResolver
        if (first) {
            cache = HashSet()
            cr.query(Telephony.Sms.CONTENT_URI, arrayOf("address", "date", "type"), null, null, null)?.use {
                while (it.moveToNext()) cache.add(key(it.getLong(1), it.getInt(2), it.getString(0)))
            }
        }
        var added = 0
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val d = o.getLong("d")
            val t = o.optInt("t", 1)
            val a = o.optString("a")
            if (!cache.add(key(d, t, a))) {
                skipped++
                continue
            }
            val v = ContentValues()
            v.put("address", a)
            v.put("body", o.optString("b"))
            v.put("date", d)
            v.put("date_sent", o.optLong("ds", 0))
            v.put("type", t)
            v.put("read", o.optInt("r", 1))
            v.put("seen", 1)
            if (cr.insert(Telephony.Sms.CONTENT_URI, v) != null) added++
        }
        return JSONObject().put("added", added).put("skipped", skipped)
    }
}
