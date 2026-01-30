package com.example.dutycaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat

class CallService : InCallService() {

    companion object {
        var instance: CallService? = null
        const val ACTION_HANGUP_NOW = "com.example.dutycaller.ACTION_HANGUP_NOW"
        const val ACTION_STATS_UPDATED = "com.example.dutycaller.ACTION_STATS_UPDATED"
        const val ACTION_HANGUP_FROM_NOTIF = "com.example.dutycaller.ACTION_HANGUP_FROM_NOTIF"
        
        // New Actions for In-Call Controls
        const val ACTION_TOGGLE_MUTE = "com.example.dutycaller.ACTION_TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.example.dutycaller.ACTION_TOGGLE_SPEAKER"
        
        // UI Update Broadcasts
        const val ACTION_CALL_STATE_CHANGED = "com.example.dutycaller.ACTION_CALL_STATE_CHANGED"
        const val EXTRA_IS_ONGOING = "is_ongoing"
        
        const val ACTION_AUDIO_STATE_CHANGED = "com.example.dutycaller.ACTION_AUDIO_STATE_CHANGED"
        const val EXTRA_IS_MUTED = "is_muted"
        const val EXTRA_IS_SPEAKER = "is_speaker"
        
        private const val CHANNEL_ID_CALL = "channel_ongoing_call"
        private const val NOTIFICATION_ID_CALL = 999
    }

    private val handler = Handler(Looper.getMainLooper())
    private var callStartTime: Long = 0

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_HANGUP_NOW, ACTION_HANGUP_FROM_NOTIF -> {
                    disconnectAllCalls()
                }
                ACTION_TOGGLE_MUTE -> {
                    val currentMute = callAudioState?.isMuted ?: false
                    setMuted(!currentMute)
                }
                ACTION_TOGGLE_SPEAKER -> {
                    val currentRoute = callAudioState?.route
                    if (currentRoute == CallAudioState.ROUTE_SPEAKER) {
                        setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
                    } else {
                        setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        
        val filter = IntentFilter().apply {
            addAction(ACTION_HANGUP_NOW)
            addAction(ACTION_HANGUP_FROM_NOTIF)
            addAction(ACTION_TOGGLE_MUTE)
            addAction(ACTION_TOGGLE_SPEAKER)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopNotification()
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {}
    }
    
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        broadcastAudioState(audioState)
    }
    
    private fun broadcastAudioState(audioState: CallAudioState?) {
        val isMuted = audioState?.isMuted ?: false
        val isSpeaker = audioState?.route == CallAudioState.ROUTE_SPEAKER
        
        val intent = Intent(ACTION_AUDIO_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_MUTED, isMuted)
            putExtra(EXTRA_IS_SPEAKER, isSpeaker)
        }
        sendBroadcast(intent)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        
        val isIncoming = call.state == Call.STATE_RINGING

        if (call.state == Call.STATE_ACTIVE) {
            if (callStartTime == 0L) {
                callStartTime = System.currentTimeMillis()
                showNotification()
                notifyCallState(true) // Notify UI shown
            }
        }
        
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)
                
                if (state == Call.STATE_ACTIVE) {
                    if (callStartTime == 0L) {
                        callStartTime = System.currentTimeMillis()
                        showNotification()
                        notifyCallState(true)
                    }
                    
                    if (!isIncoming && Prefs.isAutoHangupEnabled(this@CallService)) {
                        scheduleHangup(call)
                    }
                }
                
                if (state == Call.STATE_DISCONNECTED) {
                    stopNotification()
                    val duration = if (callStartTime > 0) (System.currentTimeMillis() - callStartTime) / 1000 else 0
                    if (duration > 0) {
                        Prefs.addCallDuration(this@CallService, duration)
                        sendBroadcast(Intent(ACTION_STATS_UPDATED))
                    }
                    callStartTime = 0
                    notifyCallState(false) // Notify UI hidden
                    notifyCallEnded()
                }
            }
        })

        if (isIncoming) {
            notifyCallState(true) // Show UI for incoming too
            if (Prefs.isAutoAnswerEnabled(this)) {
                handler.postDelayed({ call.answer(0) }, 2000)
            }
        }
    }
    
    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        if (calls.isEmpty()) {
            notifyCallState(false)
            stopNotification()
        }
    }
    
    val hasOngoingCall: Boolean
        get() = !try { calls.isEmpty() } catch (e: Exception) { true }

    private fun notifyCallState(isOngoing: Boolean) {
        val intent = Intent(ACTION_CALL_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_ONGOING, isOngoing)
            setPackage(packageName) // Explicit broadcast
        }
        sendBroadcast(intent)
        // Also update audio state when call starts
        if (isOngoing) broadcastAudioState(callAudioState)
    }
    
    fun disconnectAllCalls() {
        val currentCalls = try { calls } catch (e: Exception) { null }
        if (currentCalls.isNullOrEmpty()) return

        for (call in currentCalls) {
            if (call.state != Call.STATE_DISCONNECTED && call.state != Call.STATE_DISCONNECTING) {
                call.disconnect()
            }
        }
    }

    private fun scheduleHangup(call: Call) {
        val interval = Prefs.getHangupInterval(this)
        val delayMinutes = Utils.getRandomDelay(interval.first, interval.second)
        val delayMillis = delayMinutes * 60 * 1000
        
        Log.d("CallService", "Scheduling hangup in $delayMinutes minutes")

        handler.postDelayed({
            if (call.state == Call.STATE_ACTIVE) {
                call.disconnect()
            }
        }, delayMillis)
    }
    
    private fun notifyCallEnded() {
        val intent = Intent(this, AutomationService::class.java)
        intent.action = AutomationService.ACTION_CALL_ENDED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_CALL,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_HIGH 
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val hangupIntent = Intent(ACTION_HANGUP_FROM_NOTIF)
        val hangupPendingIntent = PendingIntent.getBroadcast(
            this, 0, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = Intent(this, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CALL)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("통화 중")
            .setUsesChronometer(true)
            .setWhen(callStartTime)
            .setOngoing(true)
            .setContentIntent(appPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "통화 종료", hangupPendingIntent)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_CALL, notification)
    }

    private fun stopNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID_CALL)
    }
}
