package com.aiadbot.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiadbot.controller.AdbAutoService
import com.aiadbot.controller.AdbController
import com.aiadbot.data.AppDatabase
import com.aiadbot.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var serviceIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        viewModel = ViewModelProvider(this, MainViewModelFactory(db, application))[MainViewModel::class.java]

        val vmAdapter = VmAdapter { vm -> viewModel.toggleVm(vm) }
        binding.recyclerVMs.layoutManager = LinearLayoutManager(this)
        binding.recyclerVMs.adapter = vmAdapter

        viewModel.allVms.observe(this) { vmAdapter.submitList(it) }

        // 自动添加默认虚拟机
        lifecycleScope.launch {
            if (viewModel.allVms.value.isNullOrEmpty()) {
                viewModel.addVm("127.0.0.1:5666")
                Toast.makeText(this@MainActivity, "已自动添加默认虚拟机 VMOS (127.0.0.1:5666)", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddVm.setOnClickListener {
            val host = binding.etVmHost.text.toString().trim()
            if (host.isNotEmpty()) {
                viewModel.addVm(host)
                binding.etVmHost.text?.clear()
            }
        }

        binding.btnManageApps.setOnClickListener { selectVmAndInstallApps() }
        binding.btnTransplantWechat.setOnClickListener { selectVmAndTransplantWechat() }
        binding.btnOpenWechat.setOnClickListener { openWechatOnVm() }
        binding.btnStart.setOnClickListener { startService() }
        binding.btnStop.setOnClickListener { stopService() }
        binding.btnContinue.setOnClickListener { resumeService() }
    }

    private fun selectVmAndInstallApps() {
        val vms = viewModel.allVms.value
        if (vms.isNullOrEmpty()) {
            Toast.makeText(this, "没有可用的虚拟机", Toast.LENGTH_SHORT).show()
            return
        }
        val vmList = vms.map { it.host }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择目标虚拟机")
            .setItems(vmList) { _, which ->
                val selectedHost = vmList[which]
                showLocalAppSelection(selectedHost)
            }
            .show()
    }

    private fun showLocalAppSelection(targetVmHost: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            pm.getLaunchIntentForPackage(it.packageName) != null
        }.map { it.packageName to it.loadLabel(pm).toString() }

        val checked = BooleanArray(apps.size) { false }
        AlertDialog.Builder(this)
            .setTitle("选择本机应用（将移植到虚拟机）")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, b -> checked[i] = b }
            .setPositiveButton("移植到虚拟机") { _, _ ->
                lifecycleScope.launch {
                    val adb = AdbController(targetVmHost)
                    apps.forEachIndexed { i, (pkg, _) ->
                        if (checked[i]) {
                            val success = withContext(Dispatchers.IO) { adb.installLocalAppToVm(pkg) }
                            val msg = if (success) "移植成功: $pkg" else "移植失败: $pkg"
                            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
                            if (success) viewModel.addTargetApp(pkg, apps[i].second)
                        }
                    }
                }
            }
            .show()
    }

    private fun selectVmAndTransplantWechat() {
        val vms = viewModel.allVms.value
        if (vms.isNullOrEmpty()) {
            Toast.makeText(this, "没有可用的虚拟机", Toast.LENGTH_SHORT).show()
            return
        }
        val vmList = vms.map { it.host }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择目标虚拟机（移植微信）")
            .setItems(vmList) { _, which ->
                val selectedHost = vmList[which]
                lifecycleScope.launch {
                    val adb = AdbController(selectedHost)
                    val success = withContext(Dispatchers.IO) { adb.installLocalAppToVm("com.tencent.mm") }
                    val msg = if (success) "微信移植成功，请登录" else "微信移植失败，请检查ADB权限"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    if (success) viewModel.addTargetApp("com.tencent.mm", "微信")
                }
            }
            .show()
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
        stopService()
        startService()
        Toast.makeText(this, "已继续自动化", Toast.LENGTH_SHORT).show()
    }

    private fun openWechatOnVm() {
        lifecycleScope.launch {
            val vms = viewModel.allVms.value
            if (vms.isNullOrEmpty()) {
                Toast.makeText(this@MainActivity, "请先添加虚拟机", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val adb = AdbController(vms[0].host)
            adb.startApp("com.tencent.mm")
        }
    }
}
