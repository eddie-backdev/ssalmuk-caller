package com.example.dutycaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STATS_UPDATED = "com.example.dutycaller.ACTION_STATS_UPDATED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                
                when (stateStr) {
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        Log.d("CallStateReceiver", "Call Started (OFFHOOK)")
                        pauseAutoCallSchedule(context)
                        val service = AutoClickService.instance
                        service?.scheduleAutoHangup()
                    }
                    
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        Log.d("CallStateReceiver", "Call Ended (IDLE)")
                        AutoClickService.instance?.cancelHangup()
                        
                        // Use REAL connection time from AutoClickService
                        val connectedAt = AutoClickService.connectedStartTime
                        if (connectedAt > 0) {
                            val durationSec = (System.currentTimeMillis() - connectedAt) / 1000
                            if (durationSec > 0) {
                                Log.d("CallStateReceiver", "Call Duration: $durationSec s. Updating stats.")
                                Prefs.addCallDuration(context, durationSec)
                                Prefs.incrementCallCount(context)
                                
                                val updateIntent = Intent(ACTION_STATS_UPDATED)
                                updateIntent.setPackage(context.packageName)
                                context.sendBroadcast(updateIntent)
                            }
                        } else {
                            Log.d("CallStateReceiver", "Call never connected (no timer detected). Stats skipped.")
                        }
                        
                        // Reset global start time in service
                        AutoClickService.connectedStartTime = 0
                        
                        if (Prefs.isAutoCallEnabled(context)) {
                            val nextIntent = Intent(context, AutoClickService::class.java)
                            nextIntent.action = AutoClickService.ACTION_CALL_ENDED
                            context.startService(nextIntent)
                        }
                    }
                    
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        Log.d("CallStateReceiver", "Incoming Call (RINGING)")
                        pauseAutoCallSchedule(context)
                        if (Prefs.isAutoAnswerEnabled(context)) {
                            AutoClickService.instance?.performAutoAnswer()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CallStateReceiver", "Error", e)
        }
    }
    
    private fun pauseAutoCallSchedule(context: Context) {
        val intent = Intent(context, AutoClickService::class.java)
        intent.action = AutoClickService.ACTION_CANCEL_SCHEDULE
        context.startService(intent)
    }
}