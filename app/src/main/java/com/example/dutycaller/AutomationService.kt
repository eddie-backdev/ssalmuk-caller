package com.example.dutycaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Random

class AutomationService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_CALL_ENDED = "ACTION_CALL_ENDED"
        private const val CHANNEL_ID = "DutyCallerChannel"
        private const val NOTIFICATION_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val random = Random()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    if (!isRunning) {
                        isRunning = true
                        createNotificationChannel()
                        startForeground(NOTIFICATION_ID, createNotification())
                        
                        showToast("서비스 시작됨. 1초 후 첫 발신 시도.")
                        scheduleNextCall(1000)
                    }
                }
                ACTION_STOP -> {
                    isRunning = false
                    handler.removeCallbacksAndMessages(null)
                    stopForeground(true)
                    stopSelf()
                    showToast("서비스 중지됨.")
                }
                ACTION_CALL_ENDED -> {
                    if (isRunning) {
                        showToast("통화 종료 감지됨. 다음 통화 대기 중.")
                        scheduleNextCall()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AutomationService", "Error in onStartCommand", e)
            showToast("오류 발생: ${e.message}")
        }
        return START_STICKY
    }

    private fun scheduleNextCall(delayOverride: Long? = null) {
        if (!isRunning) return
        
        val interval = Prefs.getCallInterval(this)
        val delaySec = Utils.getRandomDelay(interval.first, interval.second)
        val delayMillis = delayOverride ?: (delaySec * 1000)

        Log.d("AutomationService", "Next call in ${delayMillis}ms")

        handler.postDelayed({
            if (isRunning) {
                makeCall()
            }
        }, delayMillis)
    }

    private fun makeCall() {
        if (!isRunning) return

        val numbers = Prefs.getPhoneNumbers(this)
        if (numbers.isEmpty()) {
            showToast("전화번호 목록이 비어있어 종료합니다.")
            stopSelf()
            return
        }

        val number = numbers[random.nextInt(numbers.size)]
        
        try {
            Log.d("AutomationService", "Calling: $number")
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$number")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) 
            startActivity(intent)
            
            showToast("발신 시도: $number")
            
            Prefs.incrementCallCount(this)
        } catch (e: SecurityException) {
            Log.e("AutomationService", "Permission denied", e)
            showToast("권한 오류: 전화 걸기 권한을 확인해주세요.")
            stopSelf()
        } catch (e: Exception) {
            Log.e("AutomationService", "Error making call", e)
            showToast("발신 오류: ${e.message}")
            stopSelf()
        }
    }
    
    private fun showToast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Duty Caller Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Duty Caller 실행 중")
            .setContentText("자동 발신 서비스가 백그라운드에서 실행 중입니다.")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()
    }
}
