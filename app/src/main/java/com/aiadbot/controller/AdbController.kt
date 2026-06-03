package com.aiadbot.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class AdbController(private val host: String) {
    private fun exec(cmd: String): String {
        val process = Runtime.getRuntime().exec(cmd.split(" ").toTypedArray())
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readText()
        process.waitFor()
        reader.close()
        return output
    }

    private fun adbCmd(subCmd: String) = exec("adb -s $host $subCmd")

    suspend fun tap(x: Int, y: Int) = withContext(Dispatchers.IO) { adbCmd("shell input tap $x $y") }
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int) = withContext(Dispatchers.IO) {
        adbCmd("shell input swipe $x1 $y1 $x2 $y2 500")
    }
    suspend fun startApp(pkg: String) = withContext(Dispatchers.IO) {
        adbCmd("shell monkey -p $pkg -c android.intent.category.LAUNCHER 1")
    }
    suspend fun pressBack() = withContext(Dispatchers.IO) { adbCmd("shell input keyevent KEYCODE_BACK") }
    suspend fun dumpUI(): String = withContext(Dispatchers.IO) {
        adbCmd("shell uiautomator dump /dev/stdout") // 部分版本可能不支持，备用
    }
    suspend fun takeScreenshot(): String = withContext(Dispatchers.IO) {
        adbCmd("shell screencap -p /sdcard/screen.png && shell cat /sdcard/screen.png")
    }
}
