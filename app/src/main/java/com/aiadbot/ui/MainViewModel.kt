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

    fun addApp(packageName: String, appName: String) {
        viewModelScope.launch {
            dao.insert(TargetApp(packageName = packageName, appName = appName))
        }
    }

    fun toggleApp(app: TargetApp) {
        viewModelScope.launch {
            dao.update(app.copy(enabled = !app.enabled))
        }
    }

    fun deleteApp(app: TargetApp) {
        viewModelScope.launch {
            dao.delete(app)
        }
    }
}

class MainViewModelFactory(private val database: AppDatabase, private val app: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(app) as T
    }
}
