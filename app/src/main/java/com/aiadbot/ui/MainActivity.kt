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
    private lateinit var b: ActivityMainBinding
    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        val db = AppDatabase.getDatabase(this)
        vm = ViewModelProvider(this, MainViewModelFactory(db, application))[MainViewModel::class.java]

        val adapter = VmAdapter { vm.toggleVm(it) }
        b.recyclerVMs.layoutManager = LinearLayoutManager(this); b.recyclerVMs.adapter = adapter
        vm.allVms.observe(this) { adapter.submitList(it) }

        lifecycleScope.launch { if (vm.allVms.value.isNullOrEmpty()) { vm.addVm("127.0.0.1:5666"); Toast.makeText(this@MainActivity, "已添加默认虚拟机", Toast.LENGTH_SHORT).show() } }

        b.btnAddVm.setOnClickListener { b.etVmHost.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addVm(it); b.etVmHost.text?.clear() } }
        b.btnTestAdb.setOnClickListener { testAdb() }
        b.btnManageApps.setOnClickListener { selectVmAndInstallApps() }
        b.btnTransplantWechat.setOnClickListener { selectVmAndTransplantWechat() }
        b.btnOpenWechat.setOnClickListener { openWechat() }
        b.btnStart.setOnClickListener { startService(Intent(this, AdbAutoService::class.java).also { if (Build.VERSION.SDK_INT >= 26) startForegroundService(it) else startService(it) }); b.tvStatus.text = "运行中" }
        b.btnStop.setOnClickListener { startService(Intent(this, AdbAutoService::class.java).putExtra("stop", true)); b.tvStatus.text = "已停止" }
        b.btnContinue.setOnClickListener { b.btnStop.performClick(); b.btnStart.performClick() }
    }

    private fun testAdb() = lifecycleScope.launch {
        val vms = vm.allVms.value ?: listOf()
        val sb = StringBuilder()
        for (v in vms) {
            val adb = AdbController(v.host)
            sb.append("${v.host}: ${if (adb.testConnection()) "已连接" else "无法连接"}\n")
        }
        sb.append("本机ADB: ${if (AdbController("").testConnection()) "正常" else "异常"}")
        AlertDialog.Builder(this@MainActivity).setTitle("ADB状态").setMessage(sb.toString()).show()
    }

    private fun selectVmAndInstallApps() {
        val vms = vm.allVms.value?.map { it.host }?.toTypedArray() ?: run { Toast.makeText(this, "无虚拟机", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("选择虚拟机").setItems(vms) { _, i -> showLocalApps(vms[i]) }.show()
    }
    private fun showLocalApps(host: String) {
        val apps = packageManager.getInstalledApplications(0).filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }.map { it.packageName to it.loadLabel(packageManager).toString() }
        val checked = BooleanArray(apps.size)
        AlertDialog.Builder(this).setTitle("选择应用移植").setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, b -> checked[i] = b }
            .setPositiveButton("移植") { _, _ -> lifecycleScope.launch {
                val adb = AdbController(host)
                apps.forEachIndexed { i, (pkg, name) -> if (checked[i]) {
                    val (ok, err) = adb.installLocalAppToVmWithError(pkg)
                    Toast.makeText(this@MainActivity, if (ok) "$name 成功" else "$name 失败: $err", Toast.LENGTH_LONG).show()
                    if (ok) vm.addTargetApp(pkg, name)
                } }
            } }.show()
    }

    private fun selectVmAndTransplantWechat() {
        val vms = vm.allVms.value?.map { it.host }?.toTypedArray() ?: run { Toast.makeText(this, "无虚拟机", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("移植微信到").setItems(vms) { _, i -> lifecycleScope.launch {
            val (ok, err) = AdbController(vms[i]).installLocalAppToVmWithError("com.tencent.mm")
            Toast.makeText(this@MainActivity, if (ok) "微信移植成功" else "失败: $err", Toast.LENGTH_LONG).show()
            if (ok) vm.addTargetApp("com.tencent.mm", "微信")
        } }.show()
    }

    private fun openWechat() = lifecycleScope.launch {
        vm.allVms.value?.firstOrNull()?.let { AdbController(it.host).startApp("com.tencent.mm") } ?: Toast.makeText(this@MainActivity, "无虚拟机", Toast.LENGTH_SHORT).show()
    }
}
