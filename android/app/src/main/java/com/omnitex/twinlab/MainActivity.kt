package com.omnitex.twinlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omnitex.twinlab.ui.Routes
import com.omnitex.twinlab.ui.alerts.AlertsScreen
import com.omnitex.twinlab.ui.alerts.AlertsViewModel
import com.omnitex.twinlab.ui.assets.AssetListScreen
import com.omnitex.twinlab.ui.assets.AssetListViewModel
import com.omnitex.twinlab.ui.detail.AssetDetailScreen
import com.omnitex.twinlab.ui.detail.AssetDetailViewModel
import com.omnitex.twinlab.ui.settings.SettingsScreen
import com.omnitex.twinlab.ui.settings.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as TwinLabApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val baseUrl by container.settings.baseUrl.collectAsStateWithLifecycle(initialValue = null)
                AppRoot(hasBackend = baseUrl != null, container = container)
            }
        }
    }
}

@Composable
private fun AppRoot(hasBackend: Boolean, container: AppContainer) {
    val nav = rememberNavController()
    val start = if (hasBackend) Routes.ASSETS else Routes.SETTINGS

    NavHost(navController = nav, startDestination = start) {

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = container.factory)
            SettingsScreen(
                vm = vm,
                onSaved = {
                    nav.navigate(Routes.ASSETS) { popUpTo(Routes.SETTINGS) { inclusive = true } }
                },
                canGoBack = hasBackend,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.ASSETS) {
            val vm: AssetListViewModel = viewModel(factory = container.factory)
            AssetListScreen(
                vm = vm,
                onOpen = { id -> nav.navigate(Routes.detail(id)) },
                onAlerts = { nav.navigate(Routes.ALERTS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.ALERTS) {
            val vm: AlertsViewModel = viewModel(factory = container.factory)
            AlertsScreen(
                vm = vm,
                onOpen = { id -> nav.navigate(Routes.detail(id)) },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.DETAIL) { entry ->
            val deviceId = entry.arguments?.getString("deviceId")
            if (deviceId == null) {
                nav.popBackStack()
            } else {
                val vm: AssetDetailViewModel =
                    viewModel(factory = container.detailFactory(deviceId))
                AssetDetailScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
