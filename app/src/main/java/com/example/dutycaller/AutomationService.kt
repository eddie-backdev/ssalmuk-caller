package com.example.dutycaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AutomationService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_DATA_UPDATE = "com.example.dutycaller.ACTION_DATA_UPDATE"
        private const val CHANNEL_ID = "DutyCallerDataChannel"
        private const val NOTIFICATION_ID = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val dataConsumptionRunnable = object : Runnable {
        override fun run() {
            if (isRunning && Prefs.isAutoDataEnabled(this@AutomationService)) {
                checkAndConsumeData()
                handler.postDelayed(this, 10 * 1000) // 10초마다 체크로 단축
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    isRunning = true
                    createNotificationChannel()
                    startForeground(NOTIFICATION_ID, createNotification())
                    handler.post(dataConsumptionRunnable)
                }
            }
            ACTION_STOP -> {
                isRunning = false
                handler.removeCallbacksAndMessages(null)
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun checkAndConsumeData() {
        val goalMb = Prefs.getGoalData(this)
        val currentMb = Prefs.getDataUsage(this)
        
        if (goalMb > 0 && currentMb >= goalMb) {
            Log.d("AutomationService", "Data goal reached: $currentMb MB. Stopping.")
            return
        }

        if (!Utils.isMobileDataConnected(this)) {
            Log.d("AutomationService", "Not on mobile data. Skipping consumption.")
            return
        }

        // 백그라운드 스레드에서 데이터 다운로드
        thread {
            try {
                var intervalTotalBytes = 0L
                val isTurbo = Prefs.isDataTurboEnabled(this)
                val repeatCount = if (isTurbo) 30 else 1 // 터보 모드 시 30배 속도
                
                for (i in 1..repeatCount) {
                    if (!isRunning) break
                    
                    val url = URL("https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png?t=${System.currentTimeMillis()}_$i")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.useCaches = false
                    connection.connectTimeout = 3000
                    connection.connect()
                    
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream = connection.inputStream
                        val buffer = ByteArray(8192)
                        var read = inputStream.read(buffer)
                        while (read != -1) {
                            intervalTotalBytes += read
                            read = inputStream.read(buffer)
                        }
                    }
                    connection.disconnect()
                }
                
                if (intervalTotalBytes > 0) {
                    val mbUsed = intervalTotalBytes.toFloat() / (1024 * 1024)
                    Prefs.addDataUsage(this, mbUsed)
                    Log.d("AutomationService", "Consumed $mbUsed MB in this batch")
                    
                    // UI 업데이트 알림
                    sendBroadcast(Intent(ACTION_DATA_UPDATE).setPackage(packageName))
                }
            } catch (e: Exception) {
                Log.e("AutomationService", "Data consumption error: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "데이터 소모 서비스", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("의무 데이터 사용 중")
            .setContentText("백그라운드에서 데이터를 소모하고 있습니다.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
    }
}
