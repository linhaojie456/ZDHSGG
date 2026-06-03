package com.aiadbot.ui
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiadbot.data.AppDatabase
import com.aiadbot.databinding.ActivityMainBinding
import com.aiadbot.service.KeepAliveService
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: AppAdapter
    private var serviceStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getDatabase(this)
        viewModel = ViewModelProvider(this, MainViewModelFactory(database, application))[MainViewModel::class.java]

        adapter = AppAdapter(
            onToggle = { app -> viewModel.toggleApp(app) },
            onDelete = { app -> viewModel.deleteApp(app) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.allApps.observe(this) { apps ->
            adapter.submitList(apps)
            var total = 0L
            apps.forEach { total += it.reward }
            binding.tvTotalReward.text = total.toString()
        }

        binding.btnAddApp.setOnClickListener { showAddAppDialog() }
        binding.btnStartStop.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            if (!serviceStarted) {
                val intent = Intent(this, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                serviceStarted = true
                binding.btnStartStop.text = "停止任务"
            } else {
                stopService(Intent(this, KeepAliveService::class.java))
                serviceStarted = false
                binding.btnStartStop.text = "开始任务"
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/.service.AdAutomationService"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabledServices.contains(service)
    }

    private fun showAddAppDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            pm.getLaunchIntentForPackage(it.packageName) != null
        }.map { it.packageName to it.loadLabel(pm).toString() }
        val checked = BooleanArray(apps.size) { false }
        AlertDialog.Builder(this)
            .setTitle("选择应用")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    apps.forEachIndexed { index, (pkg, name) ->
                        if (checked[index]) {
                            viewModel.addApp(pkg, name)
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
