package com.phonebackup.helper

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.provider.Telephony

// این کلاس‌ها فقط لازم‌اند تا اندروید اجازه‌ی «پیامک پیش‌فرض شدن» بدهد.

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Telephony.Sms.getDefaultSmsPackage(ctx) != ctx.packageName) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (m in msgs) {
            val v = ContentValues()
            v.put("address", m.originatingAddress)
            v.put("body", m.messageBody)
            v.put("date", System.currentTimeMillis())
            v.put("date_sent", m.timestampMillis)
            v.put("type", 1)
            v.put("read", 0)
            v.put("seen", 0)
            ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, v)
        }
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {}
}

class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

class RespondService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
