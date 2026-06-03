package com.aiadbot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiadbot.data.AppDatabase
import com.aiadbot.model.TargetApp
import com.aiadbot.model.VirtualMachine
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val allVms: LiveData<List<VirtualMachine>> = db.vmDao().getAll()

    fun addVm(host: String) = viewModelScope.launch { db.vmDao().insert(VirtualMachine(host = host)) }
    fun toggleVm(vm: VirtualMachine) = viewModelScope.launch { db.vmDao().update(vm.copy(enabled = !vm.enabled)) }
    fun addTargetApp(pkg: String, name: String) = viewModelScope.launch {
        db.targetAppDao().insert(TargetApp(packageName = pkg, appName = name))
    }
}

class MainViewModelFactory(private val db: AppDatabase, private val app: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(app) as T
    }
}
