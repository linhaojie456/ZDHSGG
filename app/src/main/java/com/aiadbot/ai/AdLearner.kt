package com.aiadbot.ai
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.ln
import kotlin.math.sqrt
class AdLearner {
    private val pool = listOf("CLICK:关闭","CLICK:跳过","CLICK:×","WAIT:3","CLICK:看视频","SWIPE_UP","BACK")
    private val stats = mutableMapOf<String, Pair<Int,Double>>()
    private var total = 0
    fun selectAction(root: AccessibilityNodeInfo, app: String): String {
        val state = hashState(root, app)
        val avail = pool.filter { if (it.startsWith("CLICK:")) root.findAccessibilityNodeInfosByText(it.removePrefix("CLICK:")).isNotEmpty() else true }
        if (avail.isEmpty()) return "WAIT:5"
        return avail.maxByOrNull { a ->
            val (n,r) = stats.getOrDefault("$state-$a", 0 to 0.0)
            val mean = if (n>0) r/n else 1.0
            mean + sqrt(2.0 * ln(total+1.0) / (n+1))
        } ?: avail.first()
    }
    private fun hashState(root: AccessibilityNodeInfo, app: String): String {
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        return app + "_" + texts.sorted().joinToString("|").hashCode().toString()
    }
    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) list.add(it) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectTexts(it, list) }
    }
}
