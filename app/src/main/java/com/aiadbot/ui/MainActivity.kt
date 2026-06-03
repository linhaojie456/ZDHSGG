package com.aiadbot.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiadbot.controller.AutoService
import com.aiadbot.data.AppDatabase
import com.aiadbot.databinding.ActivityMainBinding
import com.aiadbot.model.VirtualMachine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        viewModel = ViewModelProvider(this, MainViewModelFactory(db))[MainViewModel::class.java]

        val vmAdapter = VmAdapter { vm -> viewModel.toggleVm(vm) }
        binding.recyclerVMs.layoutManager = LinearLayoutManager(this)
        binding.recyclerVMs.adapter = vmAdapter

        viewModel.allVms.observe(this) { vms ->
            vmAdapter.submitList(vms)
        }

        binding.btnAddVm.setOnClickListener {
            val host = binding.etVmHost.text.toString().trim()
            if (host.isNotEmpty()) {
                viewModel.addVm(host)
                binding.etVmHost.text?.clear()
            }
        }

        binding.btnManageApps.setOnClickListener {
            // 弹出应用选择对话框（复用之前逻辑）
            showAppSelectionDialog()
        }

        binding.btnStart.setOnClickListener {
            val intent = Intent(this, AutoService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "自动化任务已启动", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppSelectionDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0).filter {
            pm.getLaunchIntentForPackage(it.packageName) != null
        }.map { it.packageName to it.loadLabel(pm).toString() }
        val checked = BooleanArray(apps.size) { false }
        AlertDialog.Builder(this)
            .setTitle("选择目标应用")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, b -> checked[i] = b }
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    apps.forEachIndexed { i, (pkg, name) ->
                        if (checked[i]) viewModel.addTargetApp(pkg, name)
                    }
                }
            }
            .show()
    }
}
