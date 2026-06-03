package com.aiadbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != currentPkg) {
                currentPkg = pkg
                scope.launch { checkAndStartLoop(pkg) }
            }
        }
    }

    private suspend fun checkAndStartLoop(pkg: String) {
        val enabledApps = db.targetAppDao().getEnabled()
        if (enabledApps.any { it.packageName == pkg } && !running) {
            running = true
            startLoop(pkg)
        } else {
            running = false
        }
    }

    private suspend fun startLoop(appPkg: String) {
        while (running && currentPkg == appPkg) {
            val root = rootInActiveWindow ?: continue
            handleAntiDownload(root)
            val action = learner.selectAction(root, appPkg)
            val success = executeAction(action, root)
            if (success && action.contains("CLICK:") && root.findAccessibilityNodeInfosByText("获得").isNotEmpty()) {
                db.targetAppDao().addReward(appPkg, (10..100).random().toLong())
            }
            delay(1500)
        }
    }

    private fun handleAntiDownload(root: AccessibilityNodeInfo) {
        val deny = arrayOf("下载", "安装", "允许", "立即更新")
        val cancel = arrayOf("取消", "暂不", "拒绝", "关闭")
        for (t in deny) {
            val nodes = root.findAccessibilityNodeInfosByText(t)
            if (nodes.isNotEmpty() && nodes.any { it.isClickable }) {
                for (c in cancel) {
                    val cancelNodes = root.findAccessibilityNodeInfosByText(c)
                    val clickable = cancelNodes.firstOrNull { it.isClickable }
                    if (clickable != null) {
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }
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

    override fun onInterrupt() {}
}
