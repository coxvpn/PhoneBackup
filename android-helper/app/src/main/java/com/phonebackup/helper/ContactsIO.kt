package com.phonebackup.helper

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import org.json.JSONArray
import org.json.JSONObject

object ContactsIO {

    private var cache = HashSet<String>()

    private fun key(name: String, phone: String): String =
        name.trim().lowercase() + "|" + phone.filter { it.isDigit() }.takeLast(9)

    private fun keyOf(o: JSONObject): String {
        val p = o.optJSONArray("phones")
        val num = if (p != null && p.length() > 0) p.getJSONObject(0).optString("n") else ""
        return key(o.optString("name"), num)
    }

    fun read(ctx: Context): JSONArray {
        val cr = ctx.contentResolver
        val alive = HashSet<Long>()
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID), RawContacts.DELETED + "=0", null, null)?.use {
            while (it.moveToNext()) alive.add(it.getLong(0))
        }

        val map = LinkedHashMap<Long, JSONObject>()
        val proj = arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2)
        val mimes = arrayOf(
            StructuredName.CONTENT_ITEM_TYPE,
            Phone.CONTENT_ITEM_TYPE,
            Email.CONTENT_ITEM_TYPE,
            Organization.CONTENT_ITEM_TYPE
        )
        cr.query(Data.CONTENT_URI, proj, Data.MIMETYPE + " IN (?,?,?,?)", mimes, Data.RAW_CONTACT_ID)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (!alive.contains(id)) continue
                val v = c.getString(2) ?: continue
                if (v.isBlank()) continue
                val o = map.getOrPut(id) {
                    JSONObject().put("name", "").put("org", "")
                        .put("phones", JSONArray()).put("emails", JSONArray())
                }
                when (c.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> o.put("name", v)
                    Organization.CONTENT_ITEM_TYPE -> o.put("org", v)
                    Phone.CONTENT_ITEM_TYPE -> o.getJSONArray("phones").put(JSONObject().put("n", v).put("t", c.getInt(3)))
                    Email.CONTENT_ITEM_TYPE -> o.getJSONArray("emails").put(JSONObject().put("n", v).put("t", c.getInt(3)))
                }
            }
        }

        val out = JSONArray()
        for (o in map.values) {
            if (o.getString("name").isNotEmpty() || o.getJSONArray("phones").length() > 0) out.put(o)
        }
        return out
    }

    fun write(ctx: Context, arr: JSONArray, first: Boolean): JSONObject {
        if (first) {
            cache = HashSet()
            val ex = read(ctx)
            for (i in 0 until ex.length()) cache.add(keyOf(ex.getJSONObject(i)))
        }
        val cr = ctx.contentResolver
        val ops = ArrayList<ContentProviderOperation>()
        var added = 0
        var skipped = 0

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!cache.add(keyOf(o))) {
                skipped++
                continue
            }
            val idx = ops.size
            ops.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, null)
                    .withValue(RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            val name = o.optString("name")
            if (name.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, idx)
                        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(StructuredName.DISPLAY_NAME, name)
                        .build()
                )
            }
            val org = o.optString("org")
            if (org.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, idx)
                        .withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                        .withValue(Organization.COMPANY, org)
                        .build()
                )
            }
            val phones = o.optJSONArray("phones")
            if (phones != null) for (j in 0 until phones.length()) {
                val p = phones.getJSONObject(j)
                var t = p.optInt("t", 2)
                if (t <= 0) t = 2
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, idx)
                        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, p.optString("n"))
                        .withValue(Phone.TYPE, t)
                        .build()
                )
            }
            val emails = o.optJSONArray("emails")
            if (emails != null) for (j in 0 until emails.length()) {
                val e = emails.getJSONObject(j)
                var t = e.optInt("t", 1)
                if (t <= 0) t = 1
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, idx)
                        .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.ADDRESS, e.optString("n"))
                        .withValue(Email.TYPE, t)
                        .build()
                )
            }
            added++
            if (ops.size >= 300) {
                cr.applyBatch(ContactsContract.AUTHORITY, ops)
                ops.clear()
            }
        }
        if (ops.isNotEmpty()) cr.applyBatch(ContactsContract.AUTHORITY, ops)
        return JSONObject().put("added", added).put("skipped", skipped)
    }
}
