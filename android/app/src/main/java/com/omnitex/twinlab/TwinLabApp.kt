package com.omnitex.twinlab

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnitex.twinlab.data.SettingsRepository
import com.omnitex.twinlab.data.TwinLabApi
import com.omnitex.twinlab.ui.alerts.AlertsViewModel
import com.omnitex.twinlab.ui.assets.AssetListViewModel
import com.omnitex.twinlab.ui.detail.AssetDetailViewModel
import com.omnitex.twinlab.ui.settings.SettingsViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Manual DI — no Hilt. Process-scoped singletons + ViewModel factories.
 * `api` / `baseUrl` are rebuilt whenever the Settings base URL changes; both are
 * null until a URL is set.
 */
class AppContainer(app: Application) {
    private val scope = MainScope()

    val settings = SettingsRepository(app)

    val baseUrl: StateFlow<String?> =
        settings.baseUrl.stateIn(scope, SharingStarted.Eagerly, null)

    val api: StateFlow<TwinLabApi?> = settings.baseUrl
        .map { url -> url?.let { TwinLabApi(it) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** For screens whose ViewModel needs no runtime args. */
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

    /** Asset detail needs the device id from the nav route. */
    fun detailFactory(deviceId: String): ViewModelProvider.Factory = viewModelFactory {
        initializer { AssetDetailViewModel(deviceId, api, baseUrl) }
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
