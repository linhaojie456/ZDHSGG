package com.aiadbot

import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.ln
import kotlin.math.sqrt

class AdLearner {
    private val actionPool = listOf(
        "CLICK:关闭", "CLICK:跳过", "CLICK:×", "WAIT:3",
        "CLICK:看视频", "CLICK:浏览得金币", "CLICK:领奖励",
        "SWIPE_UP", "BACK"
    )
    private val stats = mutableMapOf<String, Pair<Int, Double>>()
    private var totalSelections = 0

    fun selectAction(root: AccessibilityNodeInfo, appPkg: String): String {
        val state = hashState(root, appPkg)
        val available = actionPool.filter { action ->
            if (action.startsWith("CLICK:")) {
                val text = action.removePrefix("CLICK:")
                root.findAccessibilityNodeInfosByText(text).isNotEmpty()
            } else true
        }
        if (available.isEmpty()) return "WAIT:5"

        return available.maxByOrNull { action ->
            val key = "$state-$action"
            val (count, reward) = stats.getOrDefault(key, Pair(0, 0.0))
            val mean = if (count > 0) reward / count else 1.0
            val exploration = sqrt(2.0 * ln(totalSelections + 1.0) / (count + 1))
            mean + exploration
        } ?: available.first()
    }

    fun updateReward(state: String, action: String, reward: Double) {
        val key = "$state-$action"
        val (count, total) = stats.getOrDefault(key, Pair(0, 0.0))
        stats[key] = Pair(count + 1, total + reward)
        totalSelections++
    }

    private fun hashState(root: AccessibilityNodeInfo, appPkg: String): String {
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        return appPkg + "_" + texts.sorted().joinToString("|").hashCode().toString()
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, texts) }
        }
    }
}
