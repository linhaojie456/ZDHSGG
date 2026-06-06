package com.aiadbot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiadbot.data.AppDatabase
import com.aiadbot.data.TargetApp
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).targetAppDao()
    val allApps: LiveData<List<TargetApp>> = dao.getAll()

    fun addApp(pkg: String, name: String) = viewModelScope.launch { dao.insert(TargetApp(pkg, name)) }
    fun toggleApp(app: TargetApp) = viewModelScope.launch { dao.update(app.copy(enabled = !app.enabled)) }
}

class MainViewModelFactory(private val db: AppDatabase, private val app: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(app) as T
    }
}
