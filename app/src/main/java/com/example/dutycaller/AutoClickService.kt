package com.example.dutycaller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Random

class AutoClickService : AccessibilityService() {

    companion object {
        var instance: AutoClickService? = null
        var connectedStartTime: Long = 0 // Real connection time
        const val ACTION_START_AUTO = "ACTION_START_AUTO"
        const val ACTION_STOP_AUTO = "ACTION_STOP_AUTO"
        const val ACTION_CALL_ENDED = "ACTION_CALL_ENDED"
        const val ACTION_CANCEL_SCHEDULE = "ACTION_CANCEL_SCHEDULE"
        const val ACTION_NEXT_CALL_SCHEDULED = "ACTION_NEXT_CALL_SCHEDULED"
        const val ACTION_UPDATE_CONFIG = "ACTION_UPDATE_CONFIG"
        const val ACTION_MAKE_CALL = "ACTION_MAKE_CALL" // New action
        const val EXTRA_DELAY_MILLIS = "EXTRA_DELAY_MILLIS"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isAutoMode = false
    private val random = Random()
    private var isCallConnected = false

    private val connectionMonitorRunnable = object : Runnable {
        override fun run() {
            if (!isCallConnected && isScreenShowingTimer()) {
                Log.d("AutoClickService", "Call connected! Recording start time.")
                isCallConnected = true
                connectedStartTime = System.currentTimeMillis() // Start counting NOW
                handler.removeCallbacks(forceHangupRunnable) 
                scheduleLongHangup() 
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }
    
    private val forceHangupRunnable = Runnable { performHangup() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "접근성 서비스 연결됨", Toast.LENGTH_SHORT).show()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null; isAutoMode = false
        cancelScheduledCall()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AutoClickService", "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_AUTO -> {
                if (!isAutoMode) {
                    isAutoMode = true
                    Log.d("AutoClickService", "Auto mode started. Scheduling first call.")
                    scheduleNextCall()
                }
            }
            ACTION_UPDATE_CONFIG -> {
                if (isAutoMode) {
                    Log.d("AutoClickService", "Config updated. Rescheduling call.")
                    cancelScheduledCall()
                    scheduleNextCall()
                }
            }
            ACTION_STOP_AUTO -> {
                Log.d("AutoClickService", "Auto mode stopped.")
                isAutoMode = false
                cancelScheduledCall()
                handler.removeCallbacksAndMessages(null) // Stop hangup/connection monitors
                sendBroadcast(Intent(ACTION_NEXT_CALL_SCHEDULED).putExtra(EXTRA_DELAY_MILLIS, 0L).setPackage(packageName))
            }
            ACTION_CANCEL_SCHEDULE -> {
                if (isAutoMode) {
                    Log.d("AutoClickService", "Call schedule cancelled by user.")
                    cancelScheduledCall()
                    sendBroadcast(Intent(ACTION_NEXT_CALL_SCHEDULED).putExtra(EXTRA_DELAY_MILLIS, -3L).setPackage(packageName))
                }
            }
            ACTION_CALL_ENDED -> {
                isCallConnected = false
                connectedStartTime = 0 // Reset
                handler.removeCallbacks(connectionMonitorRunnable)
                handler.removeCallbacks(forceHangupRunnable)
                if (isAutoMode) {
                    Log.d("AutoClickService", "Call ended. Scheduling next call.")
                    scheduleNextCall()
                }
            }
            ACTION_MAKE_CALL -> {
                if (isAutoMode) {
                    Log.d("AutoClickService", "ACTION_MAKE_CALL received, making call.")
                    makeCall()
                }
            }
        }
        return START_STICKY
    }

    private fun getCallAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, CallAlarmReceiver::class.java)
        // FLAG_IMMUTABLE is required for newer Android versions.
        // Using a request code of 0 is fine since we only have one alarm of this type.
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancelScheduledCall() {
        Log.d("AutoClickService", "Cancelling scheduled call.")
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getCallAlarmPendingIntent())
        Prefs.setNextCallTimestamp(this, 0L)
    }

    private fun scheduleNextCall(delayOverride: Long? = null) {
        if (!isAutoMode) {
            Log.w("AutoClickService", "scheduleNextCall called but auto mode is off.")
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getCallAlarmPendingIntent()
        var delayMillis: Long

        val allowedDays = Prefs.getCallDays(this)
        if (!Utils.isTodayAllowed(allowedDays)) {
            Log.d("AutoClickService", "Today is not an allowed call day. Scheduling for tomorrow.")
            sendBroadcast(Intent(ACTION_NEXT_CALL_SCHEDULED).putExtra(EXTRA_DELAY_MILLIS, -1L).setPackage(packageName))
            // Check again in 1 hour
            delayMillis = 60 * 60 * 1000L
        } else {
            val pauseTime = Prefs.getPauseTime(this)
            val pauseFeatures = Prefs.getPauseFeatures(this)
            if (pauseFeatures.contains("CALL") && Utils.isCurrentTimeInPauseRange(pauseTime.first, pauseTime.second)) {
                Log.d("AutoClickService", "Currently in a pause period. Scheduling for later.")
                sendBroadcast(Intent(ACTION_NEXT_CALL_SCHEDULED).putExtra(EXTRA_DELAY_MILLIS, -2L).setPackage(packageName))
                // Check again in 5 minutes
                delayMillis = 5 * 60 * 1000L
            } else {
                // This is the main path for scheduling a call
                val interval = Prefs.getCallInterval(this)
                val delaySec = Utils.getRandomDelay(interval.first, interval.second)
                delayMillis = delayOverride ?: (delaySec * 1000L)
                Log.d("AutoClickService", "Scheduling next call in ${delayMillis / 1000} seconds.")
                sendBroadcast(Intent(ACTION_NEXT_CALL_SCHEDULED).putExtra(EXTRA_DELAY_MILLIS, delayMillis).setPackage(packageName))
            }
        }

        val triggerAtMillis = System.currentTimeMillis() + delayMillis
        
        // Ensure the app has permission to schedule exact alarms.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e("AutoClickService", "Cannot schedule exact alarms. Please grant the permission.")
            Toast.makeText(this, "정확한 알람 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            // Fallback or notify user
            // For simplicity, we'll just log and fail here. 
            // A real app should guide the user to the settings.
            return
        }

        Log.d("AutoClickService", "Setting alarm to trigger in ${delayMillis}ms.")
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        Prefs.setNextCallTimestamp(this, triggerAtMillis)
    }

    private fun makeCall() {
        if (!isAutoMode) return
        Prefs.setNextCallTimestamp(this, 0L) // Clear timestamp as we are making the call now
        val numbers = Prefs.getPhoneNumbers(this)
        if (numbers.isEmpty()) return
        val number = numbers[random.nextInt(numbers.size)]
        try {
            val intent = Intent(Intent.ACTION_CALL); intent.data = Uri.parse("tel:" + number); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent)
        } catch (e: Exception) { Log.e("AutoClickService", "Call failed", e) }
    }

    fun performAutoAnswer() {
        if (!Prefs.isAutoAnswerEnabled(this)) return
        handler.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).acceptRingingCall(); return@postDelayed } catch (e: Exception) {}
            }
            if (clickTargetButtons(listOf("받기", "수신", "Answer", "Accept", "통화"))) return@postDelayed
            performSwipeToAnswer()
        }, 1500)
    }
    
    private fun performSwipeToAnswer() {
        val m = resources.displayMetrics; val w = m.widthPixels.toFloat(); val h = m.heightPixels.toFloat()
        val pR = Path().apply { moveTo(w * 0.2f, h * 0.75f); lineTo(w * 0.8f, h * 0.75f) }
        val pU = Path().apply { moveTo(w * 0.5f, h * 0.85f); lineTo(w * 0.5f, h * 0.5f) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(pR, 0, 500)).build(), null, null)
        handler.postDelayed({ dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(pU, 0, 500)).build(), null, null) }, 1000)
    }

    fun scheduleAutoHangup() {
        if (!isAutoMode && !Prefs.isAutoHangupEnabled(this)) return
        isCallConnected = false
        connectedStartTime = 0 // Important: Reset on new call
        val timeoutSec = Prefs.getNoAnswerTimeout(this)
        handler.postDelayed(forceHangupRunnable, timeoutSec * 1000L)
        handler.post(connectionMonitorRunnable)
    }
    
    private fun scheduleLongHangup() {
        val interval = Prefs.getHangupInterval(this)
        val delayMin = Utils.getRandomDelay(interval.first, interval.second)
        val delayMillis = delayMin * 60 * 1000L
        Toast.makeText(this, "연결됨: ${delayMin}분 후 끊기 예약", Toast.LENGTH_LONG).show()
        handler.postDelayed(forceHangupRunnable, delayMillis)
    }

    fun cancelHangup() {
        handler.removeCallbacks(connectionMonitorRunnable)
        handler.removeCallbacks(forceHangupRunnable)
    }

    private fun isScreenShowingTimer(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findTimerNode(root)
    }

    private fun findTimerNode(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        if (text.contains(Regex("\\d:\\d\\d"))) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findTimerNode(child)) return true
        }
        return false
    }

    private fun performHangup() {
        clickTargetButtons(listOf("통화 종료", "종료", "End call", "End"))
    }
    
    private fun clickTargetButtons(targets: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (text in targets) {
            val list = root.findAccessibilityNodeInfosByText(text)
            if (!list.isNullOrEmpty()) {
                for (node in list) {
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    if (node.parent?.isClickable == true && node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
            }
        }
        return findAndClickByDescription(root, targets)
    }
    
    private fun findAndClickByDescription(node: AccessibilityNodeInfo, targets: List<String>): Boolean {
        node.contentDescription?.let { desc ->
            for (target in targets) {
                if (desc.toString().contains(target, ignoreCase = true)) {
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    if (node.parent?.isClickable == true && node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findAndClickByDescription(child, targets)) return true
        }
        return false
    }
}
