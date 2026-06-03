package com.aiadbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aiadbot.ai.AdLearner
import com.aiadbot.data.AppDatabase
import kotlinx.coroutines.*

class AdAutomationService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentPkg = ""
    private val learner = AdLearner()
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var running = false
    private var consecutiveIdleCount = 0 // 连续无操作计数，用于判断是否换应用

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        scope.launch { startAutomationLoop() }
    }

    private suspend fun startAutomationLoop() {
        running = true
        while (running) {
            val apps = db.targetAppDao().getEnabled()
            if (apps.isEmpty()) { delay(5000); continue }

            for (app in apps) {
                if (!running) break
                // 启动目标应用
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launchIntent)
                }
                delay(3500) // 等待界面加载

                // 深度操作：只要还能找到新广告，就一直执行
                consecutiveIdleCount = 0
                while (running && currentPkg == app.packageName && consecutiveIdleCount < 5) {
                    val root = rootInActiveWindow
                    if (root == null || currentPkg != app.packageName) break

                    val handled = performAdOperations(root, app.packageName)
                    if (!handled) {
                        // 如果没有处理任何操作，可能是界面无目标，尝试滑动寻找
                        root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        consecutiveIdleCount++
                    } else {
                        consecutiveIdleCount = 0 // 有操作就重置
                    }
                    delay(2000)
                }

                // 该应用没有更多广告，返回桌面
                performGlobalAction(GLOBAL_ACTION_HOME)
                delay(1500)
            }
        }
    }

    // 返回是否处理了任何操作
    private suspend fun performAdOperations(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (handleAntiDownload(root)) return true
        if (handleUnrewardedAd(root)) return true
        if (handleRewardFlow(root, pkg)) return true
        if (clickRedPacketOrAdEntry(root)) return true
        val action = learner.selectAction(root, pkg)
        executeAction(action, root)
        return true // 无论是否成功，都认为进行了尝试
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPkg = event.packageName?.toString() ?: ""
        }
    }

    private fun handleAntiDownload(root: AccessibilityNodeInfo): Boolean {
        val deny = arrayOf("下载", "安装", "允许", "立即更新", "去应用商店")
        val cancel = arrayOf("取消", "暂不", "拒绝", "关闭", "以后再说")
        for (t in deny) {
            val nodes = root.findAccessibilityNodeInfosByText(t)
            if (nodes.isNotEmpty() && nodes.any { it.isClickable }) {
                for (c in cancel) {
                    val cancelNodes = root.findAccessibilityNodeInfosByText(c)
                    val clickable = cancelNodes.firstOrNull { it.isClickable }
                    if (clickable != null) {
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                }
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        }
        return false
    }

    private fun handleUnrewardedAd(root: AccessibilityNodeInfo): Boolean {
        if (root.findAccessibilityNodeInfosByText("奖励已领取").isNotEmpty()) return false
        val closeKeywords = arrayOf("关闭", "跳过", "×", "关闭广告")
        for (key in closeKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(key)
            val clickable = nodes.firstOrNull { it.isClickable }
            if (clickable != null &&
                root.findAccessibilityNodeInfosByText("立即收下").isEmpty() &&
                root.findAccessibilityNodeInfosByText("收下").isEmpty()
            ) {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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

    private fun clickRedPacketOrAdEntry(root: AccessibilityNodeInfo): Boolean {
        val keywords = arrayOf("开", "红包", "看视频得金币", "看视频赚", "领金币", "视频红包", "去观看")
        for (k in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(k)
            val clickable = nodes.firstOrNull { it.isClickable }
            if (clickable != null) {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (child.contentDescription?.contains("开") == true && child.isClickable) {
                child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    private suspend fun executeAction(a: String, root: AccessibilityNodeInfo): Boolean {
        return try {
            when {
                a.startsWith("CLICK:") -> {
                    val text = a.removePrefix("CLICK:")
                    val node = root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isClickable }
                    node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node != null
                }
                a.startsWith("WAIT:") -> {
                    val sec = a.removePrefix("WAIT:").toIntOrNull() ?: 5
                    delay(sec * 1000L)
                    true
                }
                a == "SWIPE_UP" -> { performGlobalAction(GLOBAL_ACTION_BACK); true }
                a == "BACK" -> { performGlobalAction(GLOBAL_ACTION_BACK); true }
                else -> false
            }
        } catch (e: Exception) { false }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }
}
