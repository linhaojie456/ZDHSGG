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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class AdbAutoService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var paused = false
    private var job: Job? = null
    private var autoTask: (suspend () -> Unit)? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "ADB_CHANNEL")
            .setContentTitle("AI广告助手运行中")
            .setContentText("通过ADB控制虚拟机")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)

        if (intent?.getBooleanExtra("stop", false) == true) {
            job?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }

        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val vms = db.vmDao().getAll().value?.filter { it.enabled } ?: listOf()
                val apps = db.targetAppDao().getEnabled()
                if (apps.isEmpty()) { delay(10000); continue }

                for (vm in vms) {
                    val adb = AdbController(vm.host)
                    for (app in apps) {
                        if (!coroutineContext.isActive) break
                        adb.startApp(app.packageName)
                        delay(5000)
                        var idleCount = 0
                        while (idleCount < 3 && coroutineContext.isActive) {
                            if (paused) {
                                delay(1000); continue
                            }
                            val uiXml = adb.dumpUI()
                            if (uiXml.isBlank()) {
                                delay(2000)
                                idleCount++
                                continue
                            }
                            val actions = parseUI(uiXml)
                            if (actions.isEmpty()) {
                                adb.swipe(500, 1200, 500, 400)
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
                        adb.pressBack()
                        delay(2000)
                    }
                }
                delay(5000)
            }
        }
        return START_STICKY
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    private fun parseUI(xml: String): List<UIAction> {
        val actions = mutableListOf<UIAction>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
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
                            paused = true
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
