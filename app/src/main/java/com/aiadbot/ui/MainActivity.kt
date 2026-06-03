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
import com.aiadbot.controller.AdbAutoService
import com.aiadbot.data.AppDatabase
import com.aiadbot.databinding.ActivityMainBinding
import com.aiadbot.model.VirtualMachine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var serviceIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        viewModel = ViewModelProvider(this, MainViewModelFactory(db))[MainViewModel::class.java]

        val vmAdapter = VmAdapter { vm -> viewModel.toggleVm(vm) }
        binding.recyclerVMs.layoutManager = LinearLayoutManager(this)
        binding.recyclerVMs.adapter = vmAdapter

        viewModel.allVms.observe(this) { vmAdapter.submitList(it) }

        binding.btnAddVm.setOnClickListener {
            val host = binding.etVmHost.text.toString().trim()
            if (host.isNotEmpty()) {
                viewModel.addVm(host)
                binding.etVmHost.text?.clear()
            }
        }

        binding.btnManageApps.setOnClickListener { showAppDialog() }
        binding.btnOpenWechat.setOnClickListener { openWechatOnVm() }
        binding.btnStart.setOnClickListener { startService() }
        binding.btnStop.setOnClickListener { stopService() }
        binding.btnContinue.setOnClickListener { resumeService() }
    }

    private fun startService() {
        serviceIntent = Intent(this, AdbAutoService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
        binding.tvStatus.text = "状态：运行中"
    }

    private fun stopService() {
        serviceIntent?.let {
            it.putExtra("stop", true)
            startService(it)
        }
        binding.tvStatus.text = "状态：已停止"
    }

    private fun resumeService() {
        // 通过静态方式很难直接与Service通信，这里简单重启service，实际应使用广播或绑定
        stopService()
        startService()
        Toast.makeText(this, "继续自动化", Toast.LENGTH_SHORT).show()
    }

    private fun openWechatOnVm() {
        lifecycleScope.launch {
            val vms = viewModel.allVms.value
            if (vms.isNullOrEmpty()) {
                Toast.makeText(this@MainActivity, "请先添加虚拟机", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // 简单打开第一个虚拟机的微信
            val adb = com.aiadbot.controller.AdbController(vms[0].host)
            adb.startApp("com.tencent.mm")
        }
    }

    private fun showAppDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0).filter {
            pm.getLaunchIntentForPackage(it.packageName) != null
        }.map { it.packageName to it.loadLabel(pm).toString() }
        val checked = BooleanArray(apps.size)
        AlertDialog.Builder(this)
            .setTitle("选择目标应用")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, b -> checked[i] = b }
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    apps.forEachIndexed { i, (pkg, name) -> if (checked[i]) viewModel.addTargetApp(pkg, name) }
                }
            }
            .show()
    }
}
