package com.aiadbot.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiadbot.data.AppDatabase
import com.aiadbot.databinding.ActivityMainBinding
import com.aiadbot.service.KeepAliveService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var vm: MainViewModel
    private lateinit var adapter: AppAdapter
    private var serviceStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val db = AppDatabase.getDatabase(this)
        vm = ViewModelProvider(this, MainViewModelFactory(db, application))[MainViewModel::class.java]

        adapter = AppAdapter { app -> vm.toggleApp(app) }
        b.recyclerView.layoutManager = LinearLayoutManager(this)
        b.recyclerView.adapter = adapter

        vm.allApps.observe(this) { apps ->
            adapter.submitList(apps)
            b.tvTotalReward.text = "总收益：${apps.sumOf { it.reward }}"
        }

        b.btnAddApp.setOnClickListener { showAddAppDialog() }
        b.btnStart.setOnClickListener { startTask() }
        b.btnStop.setOnClickListener { stopTask() }
        b.btnContinue.setOnClickListener { resumeTask() }
    }

    private fun showAddAppDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            pm.getLaunchIntentForPackage(it.packageName) != null
        }.map { it.packageName to it.loadLabel(pm).toString() }

        val checked = BooleanArray(apps.size) { false }
        AlertDialog.Builder(this)
            .setTitle("选择应用")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, b -> checked[i] = b }
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    apps.forEachIndexed { i, (pkg, name) -> if (checked[i]) vm.addApp(pkg, name) }
                }
            }
            .show()
    }

    private fun startTask() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        serviceStarted = true
        b.btnStart.text = "任务运行中"
    }

    private fun stopTask() {
        stopService(Intent(this, KeepAliveService::class.java))
        serviceStarted = false
        b.btnStart.text = "开始任务"
    }

    private fun resumeTask() {
        // 重启无障碍服务来取消暂停（简单处理）
        stopTask()
        startTask()
        Toast.makeText(this, "已继续", Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/.service.AdAutomationService"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(service)
    }
}
