package com.aiadbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aiadbot.ai.AdLearner
import com.aiadbot.data.AppDatabase
import com.aiadbot.data.OperationRecord
import kotlinx.coroutines.*

class AdAutomationService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentPkg = ""
    private val learner = AdLearner()
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var running = false
    private var recordMode = false // 是否记录手动操作

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        // 注册广播接收器监听记录模式开关
        val filter = IntentFilter("com.aiadbot.RECORD_MODE")
        registerReceiver(recordModeReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != currentPkg) {
                currentPkg = pkg
                scope.launch {
                    // 如果记录模式开启，且当前应用是目标应用，记录切换界面状态
                    if (recordMode) {
                        recordState(pkg, "WINDOW_CHANGE", rootInActiveWindow)
                    }
                    checkAndStartLoop(pkg)
                }
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && recordMode) {
            // 记录用户点击动作
            val root = rootInActiveWindow
            if (root != null && currentPkg.isNotEmpty()) {
                scope.launch {
                    val node = event.source
                    val text = node?.text?.toString() ?: node?.contentDescription?.toString() ?: ""
                    val action = if (text.isNotEmpty()) "CLICK:$text" else "CLICK:unknown"
                    // 简单判断是否获得奖励（点击后界面出现奖励关键词）
                    delay(500)
                    val newRoot = rootInActiveWindow
                    val rewarded = newRoot?.let {
                        it.findAccessibilityNodeInfosByText("获得").isNotEmpty() ||
                        it.findAccessibilityNodeInfosByText("奖励已领取").isNotEmpty()
                    } ?: false
                    recordState(currentPkg, action, root)
                    if (rewarded) {
                        db.operationRecordDao().insert(
                            OperationRecord(
                                packageName = currentPkg,
                                stateHash = learner.hashState(root, currentPkg),
                                action = action,
                                rewardObtained = true
                            )
                        )
                    } else {
                        db.operationRecordDao().insert(
                            OperationRecord(
                                packageName = currentPkg,
                                stateHash = learner.hashState(root, currentPkg),
                                action = action,
                                rewardObtained = false
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun recordState(pkg: String, action: String, root: AccessibilityNodeInfo?) {
        root ?: return
        db.operationRecordDao().insert(
            OperationRecord(
                packageName = pkg,
                stateHash = learner.hashState(root, pkg),
                action = action,
                rewardObtained = false
            )
        )
    }

    private suspend fun checkAndStartLoop(pkg: String) {
        val enabledApps = db.targetAppDao().getEnabled()
        if (enabledApps.any { it.packageName == pkg } && !running) {
            running = true
            // 加载历史成功记录到学习器
            loadSuccessfulRecords(pkg)
            startLoop(pkg)
        } else {
            running = false
        }
    }

    private suspend fun loadSuccessfulRecords(pkg: String) {
        // 将数据库中成功的操作模式注入学习器
        val records = db.operationRecordDao().getByPackage(pkg)
        records.collect { list ->
            list.filter { it.rewardObtained }.forEach { record ->
                learner.addLearnedAction(record.stateHash, record.action)
            }
        }
    }

    private suspend fun startLoop(appPkg: String) {
        while (running && currentPkg == appPkg) {
            val root = rootInActiveWindow ?: continue

            if (handleAntiDownload(root)) {
                delay(500)
                continue
            }

            if (handleUnrewardedAd(root)) {
                delay(500)
                continue
            }

            if (handleRewardFlow(root, appPkg)) {
                delay(500)
                continue
            }

            if (clickRedPacketOrAdEntry(root)) {
                delay(1000)
                continue
            }

            val action = learner.selectAction(root, appPkg)
            executeAction(action, root)
            delay(1500)
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

    private val recordModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            recordMode = intent?.getBooleanExtra("enabled", false) ?: false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(recordModeReceiver)
    }

    override fun onInterrupt() {}
}
