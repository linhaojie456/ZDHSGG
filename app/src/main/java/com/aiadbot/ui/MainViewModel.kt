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

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getDatabase(app)
    val allVms: LiveData<List<VirtualMachine>> = db.vmDao().getAll()
    fun addVm(h: String) = viewModelScope.launch { db.vmDao().insert(VirtualMachine(host = h)) }
    fun toggleVm(v: VirtualMachine) = viewModelScope.launch { db.vmDao().update(v.copy(enabled = !v.enabled)) }
    fun addTargetApp(p: String, n: String) = viewModelScope.launch { db.targetAppDao().insert(TargetApp(p, n)) }
}
class MainViewModelFactory(private val db: AppDatabase, private val app: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(app) as T
    }
}
