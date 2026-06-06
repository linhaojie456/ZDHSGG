package com.aiadbot.service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aiadbot.ui.MainActivity

class KeepAliveService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("KEEP", "保活", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(1, NotificationCompat.Builder(this, "KEEP")
            .setContentTitle("AI广告助手")
            .setContentText("后台运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi).build())
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
