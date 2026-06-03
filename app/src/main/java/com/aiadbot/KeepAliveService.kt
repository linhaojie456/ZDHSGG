package com.aiadbot
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
class KeepAliveService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("AD_BOT","广告助手", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(1, NotificationCompat.Builder(this,"AD_BOT")
            .setContentTitle("AI 广告助手运行中")
            .setContentText("正在后台自动完成任务")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW).build())
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
