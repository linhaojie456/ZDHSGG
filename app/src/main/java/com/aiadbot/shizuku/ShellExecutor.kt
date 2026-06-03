package com.aiadbot.shizuku
import android.content.Context
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShellExecutor(context: Context) {
    private var process: Shizuku.RemoteProcess? = null
    private val available: Boolean

    init {
        available = Shizuku.pingBinder()
    }

    fun isAvailable(): Boolean = available

    fun exec(command: String): String {
        if (!available) return ""
        try {
            process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            val output = reader.readText()
            process?.waitFor()
            return output
        } catch (e: Exception) {
            return ""
        }
    }
}
