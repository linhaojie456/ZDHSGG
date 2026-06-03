package com.aiadbot.ai

import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.ln
import kotlin.math.sqrt

class AdLearner {
    private val pool = mutableListOf(
        "CLICK:关闭", "CLICK:跳过", "CLICK:×", "WAIT:3",
        "CLICK:看视频", "CLICK:浏览得金币", "CLICK:领奖励",
        "SWIPE_UP", "BACK"
    )
    private val stats = mutableMapOf<String, Pair<Int, Double>>()
    private var total = 0

    // 添加从记录中学习到的动作
    fun addLearnedAction(stateHash: String, action: String) {
        if (action !in pool && action.startsWith("CLICK:")) {
            pool.add(action)
        }
        // 直接赋予高权重
        val key = "$stateHash-$action"
        val (n, r) = stats.getOrDefault(key, 0 to 0.0)
        stats[key] = n + 5 to r + 5.0  // 虚拟高奖励
        total += 5
    }

    fun hashState(root: AccessibilityNodeInfo, app: String): String {
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        return app + "_" + texts.sorted().joinToString("|").hashCode().toString()
    }

    fun selectAction(root: AccessibilityNodeInfo, app: String): String {
        val state = hashState(root, app)
        val avail = pool.filter {
            if (it.startsWith("CLICK:")) root.findAccessibilityNodeInfosByText(it.removePrefix("CLICK:")).isNotEmpty() else true
        }
        if (avail.isEmpty()) return "WAIT:5"
        return avail.maxByOrNull { a ->
            val (n, r) = stats.getOrDefault("$state-$a", 0 to 0.0)
            val mean = if (n > 0) r / n else 1.0
            mean + sqrt(2.0 * ln(total + 1.0) / (n + 1))
        } ?: avail.first()
    }

    fun updateReward(state: String, action: String, reward: Double) {
        val key = "$state-$action"
        val (n, r) = stats.getOrDefault(key, 0 to 0.0)
        stats[key] = n + 1 to r + reward
        total++
    }

    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) list.add(it) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectTexts(it, list) }
    }
}
