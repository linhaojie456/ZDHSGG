package com.aiadbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aiadbot.data.AppDatabase
import kotlinx.coroutines.*

class AdAutomationService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var running = false
    private var currentPkg = ""
    private var paused = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        scope.launch { autoLoop() }
    }

    private suspend fun autoLoop() {
        running = true
        while (running) {
            val apps = db.targetAppDao().getEnabled()
            if (apps.isEmpty()) { delay(5000); continue }
            for (app in apps) {
                if (!running) break
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                }
                delay(3000)
                // 在当前应用内深度操作，直到连续几次找不到广告
                var idle = 0
                while (running && currentPkg == app.packageName && idle < 5) {
                    if (paused) { delay(1000); continue }
                    val root = rootInActiveWindow ?: break
                    if (!performActions(root, app.packageName)) {
                        idle++
                        delay(2000)
                    } else {
                        idle = 0
                        delay(1500)
                    }
                }
                // 返回桌面
                performGlobalAction(GLOBAL_ACTION_HOME)
                delay(1000)
            }
        }
    }

    private fun performActions(root: AccessibilityNodeInfo, pkg: String): Boolean {
        // 1. 处理下载/更新弹窗
        if (handleAntiDownload(root)) return true
        // 2. 处理无奖励广告关闭
        if (handleUnrewardedAd(root)) return true
        // 3. 处理奖励领取
        if (handleRewardFlow(root, pkg)) return true
        // 4. 寻找红包/广告入口
        if (clickRedPacket(root)) return true
        // 5. 尝试滑动
        root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        return true
    }

    private fun handleAntiDownload(root: AccessibilityNodeInfo): Boolean {
        val deny = arrayOf("下载", "安装", "允许", "立即更新")
        val cancel = arrayOf("取消", "暂不", "拒绝", "关闭", "以后再说")
        for (t in deny) {
            val nodes = root.findAccessibilityNodeInfosByText(t)
            if (nodes.isNotEmpty() && nodes.any { it.isClickable }) {
                for (c in cancel) {
                    val cn = root.findAccessibilityNodeInfosByText(c).firstOrNull { it.isClickable }
                    if (cn != null) { cn.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                }
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        }
        return false
    }

    private fun handleUnrewardedAd(root: AccessibilityNodeInfo): Boolean {
        if (root.findAccessibilityNodeInfosByText("奖励已领取").isNotEmpty()) return false
        val close = arrayOf("关闭", "跳过", "×", "关闭广告")
        for (c in close) {
            val node = root.findAccessibilityNodeInfosByText(c).firstOrNull { it.isClickable }
            if (node != null && root.findAccessibilityNodeInfosByText("立即收下").isEmpty()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    private suspend fun handleRewardFlow(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (root.findAccessibilityNodeInfosByText("奖励已领取").isNotEmpty()) {
            root.findAccessibilityNodeInfosByText("跳过").firstOrNull { it.isClickable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)
            rootInActiveWindow?.findAccessibilityNodeInfosByText("立即收下")?.firstOrNull { it.isClickable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            db.targetAppDao().addReward(pkg, (50..200).random().toLong())
            delay(500)
            return true
        }
        if (root.findAccessibilityNodeInfosByText("立即收下").isNotEmpty()) {
            root.findAccessibilityNodeInfosByText("立即收下").firstOrNull { it.isClickable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            db.targetAppDao().addReward(pkg, (50..200).random().toLong())
            delay(500)
            return true
        }
        return false
    }

    private fun clickRedPacket(root: AccessibilityNodeInfo): Boolean {
        val keywords = arrayOf("开", "红包", "看视频得金币", "看视频赚", "领金币", "视频红包", "去观看")
        for (k in keywords) {
            val node = root.findAccessibilityNodeInfosByText(k).firstOrNull { it.isClickable }
            if (node != null) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (child.contentDescription?.contains("开") == true && child.isClickable) {
                child.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPkg = event.packageName?.toString() ?: ""
            // 检测微信授权页面
            val root = rootInActiveWindow ?: return
            if (root.findAccessibilityNodeInfosByText("确认登录").isNotEmpty() ||
                root.findAccessibilityNodeInfosByText("同意").isNotEmpty()) {
                paused = true
                sendPauseNotification()
            }
        }
    }

    private fun sendPauseNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(android.app.NotificationChannel("PAUSE", "暂停", android.app.NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, "PAUSE")
            .setContentTitle("需要微信授权")
            .setContentText("请在虚拟机中手动授权，然后点击继续")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        nm.notify(2, notification)
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    // 供UI调用的暂停/继续方法（简化，通过重启服务实现）
    fun pause() { paused = true }
    fun resume() { paused = false }
}
