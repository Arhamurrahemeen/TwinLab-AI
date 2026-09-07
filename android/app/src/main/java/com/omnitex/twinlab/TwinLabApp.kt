package com.omnitex.twinlab

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.omnitex.twinlab.data.SettingsRepository
import com.omnitex.twinlab.data.TwinLabApi
import com.omnitex.twinlab.ui.alerts.AlertsViewModel
import com.omnitex.twinlab.ui.assets.AssetListViewModel
import com.omnitex.twinlab.ui.settings.SettingsViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Manual DI — no Hilt. Process-scoped singletons + a ViewModel factory.
 * `api` is rebuilt whenever the Settings base URL changes; it is null until
 * a URL is set.
 */
class AppContainer(app: Application) {
    private val scope = MainScope()

    val settings = SettingsRepository(app)

    val api: StateFlow<TwinLabApi?> = settings.baseUrl
        .map { url -> url?.let { TwinLabApi(it) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            when (modelClass) {
                SettingsViewModel::class.java -> SettingsViewModel(settings)
                AssetListViewModel::class.java -> AssetListViewModel(api)
                AlertsViewModel::class.java -> AlertsViewModel(api)
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
