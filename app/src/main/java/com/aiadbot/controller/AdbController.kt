package com.aiadbot.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class AdbController(private val host: String) {
    // 执行命令并返回输出，失败返回空字符串
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

    // 执行命令并返回错误输出
    private fun execError(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(cmd.split(" ").toTypedArray())
            val reader = BufferedReader(InputStreamReader(process.errorStream))
            val output = reader.readText()
            process.waitFor()
            reader.close()
            output
        } catch (e: Exception) {
            ""
        }
    }

    private fun adbCmd(subCmd: String) = exec("adb -s $host $subCmd")
    private fun adbCmdError(subCmd: String) = execError("adb -s $host $subCmd")

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

    // 测试连接：返回是否可用
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        adbCmd("shell echo connected").contains("connected")
    }

    // 带错误信息的安装方法，返回 Pair<成功?, 错误信息>
    suspend fun installLocalAppToVmWithError(localPackage: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取本机 APK 路径
            val pathResult = exec("adb shell pm path $localPackage")
            if (pathResult.isBlank()) return@withContext Pair(false, "pm path 返回空，请确认本机已安装该应用")
            val paths = pathResult.lines()
                .mapNotNull { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
            if (paths.isEmpty()) return@withContext Pair(false, "未找到 APK 路径")

            // 2. Pull 到临时目录
            val tmpDir = File.createTempFile("apk_", "_dir").apply { delete(); mkdirs() }
            val pulledFiles = mutableListOf<File>()
            for (apkPath in paths) {
                val fileName = apkPath.substringAfterLast("/")
                val destFile = File(tmpDir, fileName)
                val pullOutput = exec("adb pull $apkPath ${destFile.absolutePath}")
                if (!destFile.exists() || destFile.length() == 0L) {
                    tmpDir.deleteRecursively()
                    return@withContext Pair(false, "pull 失败: $apkPath, 输出: $pullOutput")
                }
                pulledFiles.add(destFile)
            }

            // 3. Push 到虚拟机
            val vmTmpDir = "/data/local/tmp/install_$localPackage"
            adbCmd("shell mkdir -p $vmTmpDir")
            for (file in pulledFiles) {
                val pushOutput = exec("adb -s $host push ${file.absolutePath} $vmTmpDir/${file.name}")
                if (!pushOutput.contains("pushed")) {
                    tmpDir.deleteRecursively()
                    return@withContext Pair(false, "push 失败: ${file.name}")
                }
            }

            // 4. 安装
            if (pulledFiles.size == 1) {
                val result = adbCmd("shell pm install -r $vmTmpDir/${pulledFiles[0].name}")
                tmpDir.deleteRecursively()
                if (result.contains("Success")) Pair(true, "安装成功")
                else Pair(false, "安装失败: $result")
            } else {
                // 创建会话
                val createResult = adbCmd("shell pm install-create -r")
                val sessionIdRegex = Regex("Success: created install session \\[(\\d+)\\]")
                val sessionId = sessionIdRegex.find(createResult)?.groupValues?.get(1)
                if (sessionId == null) {
                    tmpDir.deleteRecursively()
                    return@withContext Pair(false, "创建安装会话失败: $createResult")
                }
                // 写入 split
                for (file in pulledFiles) {
                    val fileName = file.name
                    val size = file.length()
                    val writeResult = adbCmd("shell pm install-write -S $size $sessionId $fileName $vmTmpDir/$fileName")
                    if (!writeResult.contains("Success")) {
                        tmpDir.deleteRecursively()
                        return@withContext Pair(false, "写入 $fileName 失败: $writeResult")
                    }
                }
                // 提交
                val commitResult = adbCmd("shell pm install-commit $sessionId")
                tmpDir.deleteRecursively()
                if (commitResult.contains("Success")) Pair(true, "安装成功")
                else Pair(false, "安装提交失败: $commitResult")
            }
        } catch (e: Exception) {
            Pair(false, "异常: ${e.message}")
        }
    }

    // 兼容旧接口
    suspend fun installLocalAppToVm(localPackage: String): Boolean {
        return installLocalAppToVmWithError(localPackage).first
    }
}
