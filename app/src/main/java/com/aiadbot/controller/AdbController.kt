package com.aiadbot.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AdbController(private val host: String) {
    private fun exec(cmd: String): Process {
        return Runtime.getRuntime().exec(cmd.split(" ").toTypedArray())
    }

    private fun execAndWait(cmd: String): String {
        return try {
            val process = exec(cmd)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            output.ifEmpty { error }
        } catch (e: Exception) { e.message ?: "错误" }
    }

    private fun adbCmd(subCmd: String) = execAndWait("adb -s $host $subCmd")

    suspend fun tap(x: Int, y: Int) = withContext(Dispatchers.IO) { adbCmd("shell input tap $x $y") }
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int) = withContext(Dispatchers.IO) {
        adbCmd("shell input swipe $x1 $y1 $x2 $y2 500")
    }
    suspend fun startApp(pkg: String) = withContext(Dispatchers.IO) {
        adbCmd("shell monkey -p $pkg 1")
    }
    suspend fun pressBack() = withContext(Dispatchers.IO) { adbCmd("shell input keyevent KEYCODE_BACK") }
    suspend fun dumpUI(): String = withContext(Dispatchers.IO) {
        adbCmd("shell uiautomator dump /dev/stdout")
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        adbCmd("shell echo ok").contains("ok")
    }

    suspend fun installLocalAppToVmWithError(localPackage: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val pathResult = execAndWait("adb shell pm path $localPackage")
            val paths = pathResult.lines().mapNotNull { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }
            if (paths.isEmpty()) return@withContext Pair(false, "未找到应用路径，请确认本机已安装该应用")

            val tmpDir = File.createTempFile("apk_", "_dir").apply { delete(); mkdirs() }
            val pulledFiles = mutableListOf<File>()
            for (apkPath in paths) {
                val destFile = File(tmpDir, apkPath.substringAfterLast("/"))
                execAndWait("adb pull $apkPath ${destFile.absolutePath}")
                if (!destFile.exists()) return@withContext Pair(false, "pull 失败: $apkPath")
                pulledFiles.add(destFile)
            }

            val vmTmpDir = "/data/local/tmp/install_$localPackage"
            adbCmd("shell mkdir -p $vmTmpDir")
            for (file in pulledFiles) {
                execAndWait("adb -s $host push ${file.absolutePath} $vmTmpDir/${file.name}")
            }

            if (pulledFiles.size == 1) {
                val result = adbCmd("shell pm install -r $vmTmpDir/${pulledFiles[0].name}")
                tmpDir.deleteRecursively()
                if (result.contains("Success")) Pair(true, "安装成功") else Pair(false, result)
            } else {
                val createResult = adbCmd("shell pm install-create -r")
                val sessionId = Regex("\\[(\\d+)\\]").find(createResult)?.groupValues?.get(1) ?: return@withContext Pair(false, "创建会话失败")
                for (file in pulledFiles) {
                    adbCmd("shell pm install-write -S ${file.length()} $sessionId ${file.name} $vmTmpDir/${file.name}")
                }
                val commitResult = adbCmd("shell pm install-commit $sessionId")
                tmpDir.deleteRecursively()
                if (commitResult.contains("Success")) Pair(true, "安装成功") else Pair(false, commitResult)
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "未知错误")
        }
    }
}
