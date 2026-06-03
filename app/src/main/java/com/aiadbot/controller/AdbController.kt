package com.aiadbot.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class AdbController(private val host: String) {
    private fun exec(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(cmd.split(" ").toTypedArray())
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            reader.close()
            output
        } catch (e: Exception) {
            ""
        }
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
        adbCmd("shell uiautomator dump /dev/stdout")
    }

    // 提取本机APK并安装到虚拟机
    suspend fun installLocalAppToVm(localPackage: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取本机 APK 路径
            val pathResult = exec("adb shell pm path $localPackage")
            val apkPath = pathResult.replace("package:", "").trim()
            if (apkPath.isEmpty()) return@withContext false

            val tmpFile = File.createTempFile("apk_", ".apk")
            exec("adb pull $apkPath ${tmpFile.absolutePath}")

            if (tmpFile.exists()) {
                val installResult = adbCmd("install -r ${tmpFile.absolutePath}")
                tmpFile.delete()
                installResult.contains("Success")
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
