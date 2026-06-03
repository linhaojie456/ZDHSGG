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
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class AdbAutoService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var paused = false
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "ADB_CHANNEL")
            .setContentTitle("AI广告助手运行中")
            .setContentText("通过ADB控制虚拟机")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)

        if (intent?.getBooleanExtra("stop", false) == true) {
            job?.cancel()
            stopSelf()
        } else {
            job?.cancel()
            job = scope.launch { autoTask() }
        }
        return START_STICKY
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    private suspend fun autoTask() {
        while (isActive) {
            val vms = db.vmDao().getAll().value?.filter { it.enabled } ?: continue
            val apps = db.targetAppDao().getEnabled()
            if (apps.isEmpty()) { delay(10000); continue }

            for (vm in vms) {
                val adb = AdbController(vm.host)
                for (app in apps) {
                    if (!isActive) break
                    // 启动应用
                    adb.startApp(app.packageName)
                    delay(5000)
                    // 循环操作直到广告穷尽或超时
                    var idleCount = 0
                    while (idleCount < 3 && isActive) {
                        if (paused) {
                            delay(1000); continue
                        }
                        // 尝试获取UI并进行操作
                        val uiXml = adb.dumpUI()
                        if (uiXml.isBlank()) {
                            delay(2000)
                            idleCount++
                            continue
                        }
                        val actions = parseUI(uiXml)
                        if (actions.isEmpty()) {
                            adb.swipe(500, 1200, 500, 400) // 向上滑动
                            idleCount++
                            delay(2000)
                        } else {
                            for (action in actions) {
                                when (action) {
                                    is UIAction.Tap -> adb.tap(action.x, action.y)
                                    is UIAction.Back -> adb.pressBack()
                                    is UIAction.Wait -> delay(action.ms)
                                }
                                delay(1000)
                            }
                            idleCount = 0
                        }
                    }
                    // 返回桌面
                    adb.pressBack()
                    delay(2000)
                }
            }
            delay(5000)
        }
    }

    private fun parseUI(xml: String): List<UIAction> {
        val actions = mutableListOf<UIAction>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParserFactory.newInstance().newPullParser().END_DOCUMENT) {
                if (eventType == 2 /* START_TAG */) {
                    val text = parser.getAttributeValue(null, "text") ?: ""
                    val desc = parser.getAttributeValue(null, "content-desc") ?: ""
                    val bounds = parser.getAttributeValue(null, "bounds") ?: ""
                    val clickable = parser.getAttributeValue(null, "clickable") == "true"
                    if (!clickable) {
                        eventType = parser.next(); continue
                    }
                    val coords = try {
                        val regex = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
                        val match = regex.find(bounds)
                        if (match != null) {
                            val x = (match.groupValues[1].toInt() + match.groupValues[3].toInt()) / 2
                            val y = (match.groupValues[2].toInt() + match.groupValues[4].toInt()) / 2
                            Pair(x, y)
                        } else null
                    } catch (e: Exception) { null }
                    if (coords == null) { eventType = parser.next(); continue }

                    val lower = (text + desc).lowercase()
                    when {
                        lower.contains("确认登录") || lower.contains("同意") || lower.contains("允许") -> {
                            // 微信授权界面，暂停
                            paused = true
                            // 发送通知提示手动操作
                            sendPauseNotification()
                            actions.add(UIAction.Wait(5000))
                        }
                        lower.contains("跳过") || lower.contains("关闭") -> actions.add(UIAction.Tap(coords.first, coords.second))
                        lower.contains("看视频") || lower.contains("领金币") || lower.contains("红包") || lower.contains("开") -> actions.add(UIAction.Tap(coords.first, coords.second))
                        lower.contains("立即收下") -> actions.add(UIAction.Tap(coords.first, coords.second))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) { }
        return actions
    }

    private fun sendPauseNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "ADB_CHANNEL")
            .setContentTitle("需要微信授权")
            .setContentText("请在虚拟机中手动完成微信授权，然后点击继续")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        manager.notify(2, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ADB_CHANNEL", "ADB控制", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}

sealed class UIAction {
    data class Tap(val x: Int, val y: Int) : UIAction()
    object Back : UIAction()
    data class Wait(val ms: Long) : UIAction()
}
