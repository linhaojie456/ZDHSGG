package com.aiadbot.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aiadbot.R
import com.aiadbot.data.AppDatabase
import com.aiadbot.ui.MainActivity
import kotlinx.coroutines.*

class AutoService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "ADB_CHANNEL")
            .setContentTitle("AI广告助手")
            .setContentText("通过ADB控制虚拟机自动浏览广告")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)

        // 启动自动化循环
        scope.launch { autoTask() }
        return START_STICKY
    }

    private suspend fun autoTask() {
        while (isActive) {
            val vms = db.vmDao().getAll().value ?: continue
            val apps = db.targetAppDao().getEnabled()
            if (apps.isEmpty()) { delay(10000); continue }

            for (vm in vms) {
                if (!vm.enabled) continue
                val adb = AdbController(vm.host)
                for (app in apps) {
                    // 启动应用
                    adb.startApp(app.packageName)
                    delay(3000)
                    // 执行操作（简化版：随机点击常见位置）
                    repeat(10) {
                        // 这里应接入你的广告操作逻辑，例如通过UI自动化解析
                        adb.tap(500, 500) // 示例点击
                        delay(2000)
                    }
                    // 返回桌面
                    adb.pressBack()
                    delay(2000)
                }
            }
            delay(10000)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ADB_CHANNEL", "ADB控制", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
