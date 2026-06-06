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

    // 全自动安装应用（支持 Split APK）
    suspend fun installLocalAppToVm(localPackage: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. 获取所有 APK 路径
            val pathResult = exec("adb shell pm path $localPackage")
            val paths = pathResult.lines()
                .mapNotNull { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
            if (paths.isEmpty()) return@withContext false

            // 2. Pull 所有 APK 到临时目录
            val tmpDir = File.createTempFile("apk_", "_dir").apply { delete(); mkdirs() }
            val pulledFiles = mutableListOf<File>()
            for (apkPath in paths) {
                val fileName = apkPath.substringAfterLast("/")
                val destFile = File(tmpDir, fileName)
                exec("adb pull $apkPath ${destFile.absolutePath}")
                if (destFile.exists()) pulledFiles.add(destFile)
            }

            if (pulledFiles.isEmpty()) {
                tmpDir.deleteRecursively()
                return@withContext false
            }

            // 3. 将 APK 推送到虚拟机的 /data/local/tmp
            val vmTmpDir = "/data/local/tmp/install_$localPackage"
            adbCmd("shell mkdir -p $vmTmpDir")
            for (file in pulledFiles) {
                exec("adb -s $host push ${file.absolutePath} $vmTmpDir/${file.name}")
            }

            // 4. 在虚拟机内执行安装
            val installCmd = if (pulledFiles.size == 1) {
                "pm install -r $vmTmpDir/${pulledFiles[0].name}"
            } else {
                "pm install-create -r"
            }
            // 对于多个文件，需要使用 install-create + install-write + install-commit
            if (pulledFiles.size > 1) {
                // 创建安装会话
                val createResult = adbCmd("shell pm install-create -r")
                val sessionIdRegex = Regex("Success: created install session \\[(\\d+)\\]")
                val sessionId = sessionIdRegex.find(createResult)?.groupValues?.get(1)
                if (sessionId == null) {
                    tmpDir.deleteRecursively()
                    return@withContext false
                }
                // 写入每个 split
                for (file in pulledFiles) {
                    val fileName = file.name
                    val size = file.length()
                    adbCmd("shell pm install-write -S $size $sessionId $fileName $vmTmpDir/$fileName")
                }
                // 提交安装
                val commitResult = adbCmd("shell pm install-commit $sessionId")
                tmpDir.deleteRecursively()
                commitResult.contains("Success")
            } else {
                val installResult = adbCmd("shell pm install -r $vmTmpDir/${pulledFiles[0].name}")
                tmpDir.deleteRecursively()
                installResult.contains("Success")
            }
        } catch (e: Exception) {
            false
        }
    }
}
