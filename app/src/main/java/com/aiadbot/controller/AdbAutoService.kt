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
    @Volatile var paused = false
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(1, NotificationCompat.Builder(this, "ADB_CHANNEL")
            .setContentTitle("AI广告助手运行中").setContentText("ADB控制虚拟机").setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pi).build())

        if (intent?.getBooleanExtra("stop", false) == true) { job?.cancel(); stopSelf(); return START_NOT_STICKY }
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
                        var idle = 0
                        while (idle < 3 && coroutineContext.isActive) {
                            if (paused) { delay(1000); continue }
                            val xml = adb.dumpUI()
                            if (xml.isBlank()) { delay(2000); idle++; continue }
                            val actions = parseUI(xml)
                            if (actions.isEmpty()) { adb.swipe(500,1200,500,400); idle++; delay(2000) }
                            else { actions.forEach { when(it) {
                                is UIAction.Tap -> adb.tap(it.x, it.y)
                                is UIAction.Back -> adb.pressBack()
                                is UIAction.Wait -> delay(it.ms)
                            } }; delay(1000); idle = 0 }
                        }
                        adb.pressBack(); delay(2000)
                    }
                }
                delay(5000)
            }
        }
        return START_STICKY
    }

    private fun parseUI(xml: String): List<UIAction> = try {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(StringReader(xml)) }
        val actions = mutableListOf<UIAction>()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                val text = parser.getAttributeValue(null, "text") ?: ""
                val desc = parser.getAttributeValue(null, "content-desc") ?: ""
                val bounds = parser.getAttributeValue(null, "bounds") ?: ""
                if (parser.getAttributeValue(null, "clickable") != "true") { event = parser.next(); continue }
                val coords = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]").find(bounds)?.let {
                    val (x1,y1,x2,y2) = it.destructured; Pair((x1.toInt()+x2.toInt())/2, (y1.toInt()+y2.toInt())/2) }
                if (coords != null) {
                    val lower = (text + desc).lowercase()
                    when {
                        lower.contains("确认登录") || lower.contains("同意") -> { paused = true; actions.add(UIAction.Wait(5000)) }
                        lower.contains("跳过") || lower.contains("关闭") -> actions.add(UIAction.Tap(coords.first, coords.second))
                        lower.contains("看视频") || lower.contains("红包") || lower.contains("开") -> actions.add(UIAction.Tap(coords.first, coords.second))
                        lower.contains("立即收下") -> actions.add(UIAction.Tap(coords.first, coords.second))
                    }
                }
            }
            event = parser.next()
        }
        actions
    } catch (e: Exception) { emptyList() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("ADB_CHANNEL","ADB控制", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { job?.cancel(); scope.cancel(); super.onDestroy() }
}
sealed class UIAction { data class Tap(val x:Int,val y:Int):UIAction(); object Back:UIAction(); data class Wait(val ms:Long):UIAction() }
