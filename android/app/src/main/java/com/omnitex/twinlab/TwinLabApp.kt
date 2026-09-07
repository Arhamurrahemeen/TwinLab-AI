package com.omnitex.twinlab

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.omnitex.twinlab.data.SettingsRepository
import com.omnitex.twinlab.ui.settings.SettingsViewModel

/**
 * Manual DI — no Hilt. Holds process-scoped singletons and a ViewModel factory.
 * Task 7 adds `api`; Tasks 7/8 add the asset-list / detail / alerts ViewModels
 * to [factory].
 */
class AppContainer(app: Application) {
    val settings = SettingsRepository(app)

    val factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            when (modelClass) {
                SettingsViewModel::class.java -> SettingsViewModel(settings)
                else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
            } as T
    }
}

class TwinLabApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
