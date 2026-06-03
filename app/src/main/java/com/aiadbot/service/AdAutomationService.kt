package com.aiadbot.service
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aiadbot.ai.AdLearner
import com.aiadbot.data.AppDatabase
import com.aiadbot.shizuku.ShellExecutor
import kotlinx.coroutines.*

class AdAutomationService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val learner = AdLearner()
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var running = false
    private lateinit var shell: ShellExecutor

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        shell = ShellExecutor(this)
        scope.launch { startAutomation() }
    }

    private suspend fun startAutomation() {
        if (!shell.isAvailable()) return
        running = true
        while (running) {
            val apps = db.targetAppDao().getEnabled()
            if (apps.isEmpty()) { delay(5000); continue }
            for (app in apps) {
                if (!running) break
                // 启动目标应用
                shell.exec("am start -n ${app.packageName}/$(pm resolve-activity --brief -c android.intent.category.LAUNCHER ${app.packageName} | tail -1)")
                delay(3000)
                // 循环操作最多20秒
                repeat(5) {
                    if (!running) return
                    val root = rootInActiveWindow ?: return@repeat
                    performOperations(root, app.packageName)
                    delay(3000)
                }
                // 返回桌面
                shell.exec("input keyevent KEYCODE_HOME")
                delay(2000)
            }
        }
    }

    private suspend fun performOperations(root: AccessibilityNodeInfo, pkg: String) {
        // 使用无障碍节点坐标 + ADB 点击
        handleAntiDownload(root)
        handleReward(root, pkg)
        clickRedPacket(root)
        val action = learner.selectAction(root, pkg)
        executeAdbAction(action, root)
    }

    private fun handleAntiDownload(root: AccessibilityNodeInfo) {
        val deny = arrayOf("下载", "安装", "允许", "立即更新")
        val cancel = arrayOf("取消", "暂不", "拒绝", "关闭", "以后再说")
        for (t in deny) {
            root.findAccessibilityNodeInfosByText(t).firstOrNull { it.isClickable }?.let { node ->
                for (c in cancel) {
                    root.findAccessibilityNodeInfosByText(c).firstOrNull { it.isClickable }?.let { cancelNode ->
                        clickNode(cancelNode)
                        return
                    }
                }
                shell.exec("input keyevent KEYCODE_BACK")
                return
            }
        }
    }

    private suspend fun handleReward(root: AccessibilityNodeInfo, pkg: String) {
        if (root.findAccessibilityNodeInfosByText("奖励已领取").isNotEmpty()) {
            root.findAccessibilityNodeInfosByText("跳过").firstOrNull { it.isClickable }?.let { clickNode(it) }
            delay(1000)
            rootInActiveWindow?.findAccessibilityNodeInfosByText("立即收下")?.firstOrNull { it.isClickable }?.let { clickNode(it) }
            db.targetAppDao().addReward(pkg, (50..200).random().toLong())
        } else if (root.findAccessibilityNodeInfosByText("立即收下").isNotEmpty()) {
            root.findAccessibilityNodeInfosByText("立即收下").firstOrNull { it.isClickable }?.let { clickNode(it) }
            db.targetAppDao().addReward(pkg, (50..200).random().toLong())
        }
    }

    private fun clickRedPacket(root: AccessibilityNodeInfo) {
        val keywords = arrayOf("开", "红包", "看视频得金币", "领金币")
        for (k in keywords) {
            root.findAccessibilityNodeInfosByText(k).firstOrNull { it.isClickable }?.let { clickNode(it); return }
        }
    }

    private fun executeAdbAction(action: String, root: AccessibilityNodeInfo) {
        when {
            action.startsWith("CLICK:") -> {
                root.findAccessibilityNodeInfosByText(action.removePrefix("CLICK:")).firstOrNull { it.isClickable }?.let { clickNode(it) }
            }
            action.startsWith("WAIT:") -> { /* handled by coroutine */ }
            action == "SWIPE_UP" -> shell.exec("input swipe 500 1500 500 500 300")
            action == "BACK" -> shell.exec("input keyevent KEYCODE_BACK")
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val x = rect.centerX()
        val y = rect.centerY()
        shell.exec("input tap $x $y")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }
}
