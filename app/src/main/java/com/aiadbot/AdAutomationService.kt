package com.aiadbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class AdAutomationService : AccessibilityService() {
    private val targetPackages = mutableSetOf(
        "com.ss.android.ugc.aweme.lite",
        "com.kuaishou.nebula"
    )
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastPackage = ""
    private val learner = AdLearner()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPkg = event.packageName?.toString() ?: return
            if (currentPkg in targetPackages) {
                if (currentPkg != lastPackage) {
                    lastPackage = currentPkg
                    startAutoLoop()
                }
            } else if (lastPackage in targetPackages) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(200)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun startAutoLoop() {
        scope.launch {
            while (isActive) {
                val root = rootInActiveWindow ?: continue
                handleAntiDownload(root)
                val action = learner.selectAction(root, lastPackage)
                executeAction(action, root)
                delay(1500)
            }
        }
    }

    private fun handleAntiDownload(root: AccessibilityNodeInfo) {
        val denyTexts = arrayOf("下载", "安装", "允许", "立即更新")
        val cancelTexts = arrayOf("取消", "暂不", "拒绝", "关闭")
        for (text in denyTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty() && nodes.any { it.isClickable }) {
                val cancel = root.findAccessibilityNodeInfosByText(*cancelTexts)
                    .firstOrNull { it.isClickable }
                cancel?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ?: performGlobalAction(GLOBAL_ACTION_BACK)
                break
            }
        }
    }

    private fun executeAction(action: String, root: AccessibilityNodeInfo) {
        when {
            action.startsWith("CLICK:") -> {
                val text = action.removePrefix("CLICK:")
                root.findAccessibilityNodeInfosByText(text)
                    .firstOrNull { it.isClickable }
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            action.startsWith("WAIT:") -> {
                val sec = action.removePrefix("WAIT:").toIntOrNull() ?: 5
                delay(sec * 1000L)
            }
            action == "SWIPE_UP" -> performGlobalAction(GLOBAL_ACTION_BACK)
            action == "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {}
}
