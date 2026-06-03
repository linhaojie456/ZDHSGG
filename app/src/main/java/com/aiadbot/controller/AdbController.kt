package com.aiadbot.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdbController(private val host: String) {
    suspend fun runShell(command: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", host, "shell", command))
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output
            } catch (e: Exception) {
                ""
            }
        }
    }

    suspend fun tap(x: Int, y: Int) {
        runShell("input tap $x $y")
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 300) {
        runShell("input swipe $x1 $y1 $x2 $y2 $duration")
    }

    suspend fun startApp(packageName: String) {
        runShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    suspend fun pressBack() {
        runShell("input keyevent KEYCODE_BACK")
    }
}
