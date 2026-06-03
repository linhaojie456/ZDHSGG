package com.aiadbot.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Socket

/**
 * 极简 ADB 客户端，通过 TCP 连接 ADB 服务端执行命令
 */
object AdbClient {
    private const val DEFAULT_PORT = 5555
    private val client = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(3))
        .readTimeout(java.time.Duration.ofSeconds(10))
        .build()

    suspend fun exec(host: String, command: String): String {
        return withContext(Dispatchers.IO) {
            val (ip, port) = parseHost(host)
            // 使用原始 Socket 连接 ADB 服务端（简单处理）
            try {
                val socket = Socket(ip, port)
                socket.soTimeout = 10000
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                // 发送 ADB 协议请求（简化版，仅支持 shell 命令）
                val request = buildAdbShellRequest(command)
                output.write(request)
                output.flush()
                val response = input.readBytes()
                socket.close()
                String(response)
            } catch (e: Exception) {
                e.printStackTrace()
                "error: ${e.message}"
            }
        }
    }

    private fun parseHost(host: String): Pair<String, Int> {
        val parts = host.split(":")
        return parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT)
    }

    // 构建 ADB shell 命令请求（实际需完整ADB协议，此处用最简方法，不推荐生产环境）
    // 作为演示，我们直接返回空，你需要集成完整 ADB 库（如 ddmlib）或使用 termux 的 adb 命令行
    private fun buildAdbShellRequest(command: String): ByteArray {
        // 此处省略协议实现，建议改用命令行调用方式
        return "".toByteArray()
    }
}
