package com.aiadbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aiadbot.ai.AdLearner
import com.aiadbot.data.AppDatabase
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class AdAutomationService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentPkg = ""
    private val learner = AdLearner()
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var running = false
    private var currentAppIndex = 0
    private var appList: List<com.aiadbot.data.TargetApp> = listOf()
    private var currentAppStartTime = 0L
    private var maxAppTime = TimeUnit.SECONDS.toMillis(30) // 每个应用最多停留30秒

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        // 服务启动后立即开始循环
        scope.launch { startFullAutomation() }
    }

    private suspend fun startFullAutomation() {
        running = true
        while (running) {
            // 获取启用应用列表
            appList = db.targetAppDao().getEnabled()
            if (appList.isEmpty()) {
                delay(5000)
                continue
            }
            // 按顺序循环
            for (i in appList.indices) {
                currentAppIndex = i
                val app = appList[i]
                // 启动目标应用
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launchIntent)
                }
                currentAppStartTime = System.currentTimeMillis()
                // 等待应用进入前台
                delay(2000)
                // 执行广告操作，直到超时或应用退出
                while (running && currentPkg == app.packageName &&
                    (System.currentTimeMillis() - currentAppStartTime) < maxAppTime) {
                    val root = rootInActiveWindow ?: continue
                    if (!performAdOperations(root, app.packageName)) {
                        // 如果已经没有可操作的目标，提前退出
                        delay(3000)
                    }
                    delay(1000)
                }
                // 返回桌面
                performGlobalAction(GLOBAL_ACTION_HOME)
                delay(2000)
            }
        }
    }

    // 执行一系列广告操作，返回是否还有操作空间
    private suspend fun performAdOperations(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (handleAntiDownload(root)) return true
        if (handleUnrewardedAd(root)) return true
        if (handleRewardFlow(root, pkg)) return true
        if (clickRedPacketOrAdEntry(root)) return true
        val action = learner.selectAction(root, pkg)
        executeAction(action, root)
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPkg = event.packageName?.toString() ?: ""
        }
    }

    private fun handleAntiDownload(root: AccessibilityNodeInfo): Boolean {
        val deny = arrayOf("下载", "安装", "允许", "立即更新", "去应用商店")
        val cancel = arrayOf("取消", "暂不", "拒绝", "关闭", "以后再说", "忽略")
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
        val closeKeywords = arrayOf("关闭", "跳过", "×", "关闭广告", "不感兴趣")
        for (keyword in closeKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            val clickable = nodes.firstOrNull { it.isClickable }
            if (clickable != null) {
                if (root.findAccessibilityNodeInfosByText("立即收下").isEmpty() &&
                    root.findAccessibilityNodeInfosByText("收下").isEmpty()) {
                    clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }
        return false
    }

    private suspend fun handleRewardFlow(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val rewardNodes = root.findAccessibilityNodeInfosByText("奖励已领取")
        if (rewardNodes.isNotEmpty()) {
            val skipNodes = root.findAccessibilityNodeInfosByText("跳过")
            val clickableSkip = skipNodes.firstOrNull { it.isClickable }
            clickableSkip?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(800)
            val newRoot = rootInActiveWindow ?: return true
            val collectNodes = newRoot.findAccessibilityNodeInfosByText("立即收下")
            val clickableCollect = collectNodes.firstOrNull { it.isClickable }
            clickableCollect?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            db.targetAppDao().addReward(pkg, (50..200).random().toLong())
            delay(1000)
            return true
        }
        if (root.findAccessibilityNodeInfosByText("立即收下").isNotEmpty()) {
            val collect = root.findAccessibilityNodeInfosByText("立即收下").firstOrNull { it.isClickable }
            collect?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            db.targetAppDao().addReward(pkg, (50..200).random().toLong())
            delay(1000)
            return true
        }
        return false
    }

    private fun clickRedPacketOrAdEntry(root: AccessibilityNodeInfo): Boolean {
        val entryKeywords = arrayOf("开", "红包", "看视频得金币", "看视频赚", "领金币", "视频红包", "去观看")
        for (key in entryKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(key)
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
                a == "SWIPE_UP" -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    true
                }
                a == "BACK" -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    true
                }
                else -> false
            }
        } catch (e: Exception) { false }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        scope.cancel()
    }

    override fun onInterrupt() {}
}
