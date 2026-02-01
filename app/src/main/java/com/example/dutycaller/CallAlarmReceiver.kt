package com.example.dutycaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CallAlarmReceiver", "Alarm received, starting AutoClickService to make a call.")
        val serviceIntent = Intent(context, AutoClickService::class.java).apply {
            action = AutoClickService.ACTION_MAKE_CALL
        }
        context.startService(serviceIntent)
    }
}
