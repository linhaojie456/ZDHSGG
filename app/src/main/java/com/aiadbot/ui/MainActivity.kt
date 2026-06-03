package com.aiadbot.ui
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiadbot.data.AppDatabase
import com.aiadbot.databinding.ActivityMainBinding
import com.aiadbot.service.KeepAliveService
import rikka.shizuku.Shizuku
import androidx.appcompat.app.AlertDialog
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
        viewModel = ViewModelProvider(this, MainViewModelFactory(database))[MainViewModel::class.java]
        adapter = AppAdapter(onToggle = { viewModel.toggleApp(it) }, onDelete = { viewModel.deleteApp(it) })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        viewModel.allApps.observe(this) {
            adapter.submitList(it)
            binding.tvTotalReward.text = it.sumOf { app -> app.reward }.toString()
        }

        binding.btnShizuku.setOnClickListener {
            if (Shizuku.pingBinder()) {
                if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == 0) {
                    Toast.makeText(this, "Shizuku 已授权", Toast.LENGTH_SHORT).show()
                } else {
                    Shizuku.requestPermission(0)
                }
            } else {
                Toast.makeText(this, "请先启动 Shizuku App", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnAddApp.setOnClickListener { showAddAppDialog() }
        binding.btnStartStop.setOnClickListener {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != 0) {
                Toast.makeText(this, "请先授权 Shizuku", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!serviceStarted) {
                startService(Intent(this, KeepAliveService::class.java).also { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(it) })
                serviceStarted = true
                binding.btnStartStop.text = "停止任务"
            } else {
                stopService(Intent(this, KeepAliveService::class.java))
                serviceStarted = false
                binding.btnStartStop.text = "开始任务"
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") })
            }
        }
    }

    private fun showAddAppDialog() {
        val apps = packageManager.getInstalledApplications(0).filter {
            packageManager.getLaunchIntentForPackage(it.packageName) != null
        }.map { it.packageName to it.loadLabel(packageManager).toString() }
        val checked = BooleanArray(apps.size)
        AlertDialog.Builder(this).setTitle("选择应用")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, b -> checked[i] = b }
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch { apps.forEachIndexed { i, (pkg, name) -> if (checked[i]) viewModel.addApp(pkg, name) } }
            }.show()
    }
}
