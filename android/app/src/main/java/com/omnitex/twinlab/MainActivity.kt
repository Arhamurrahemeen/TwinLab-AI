package com.omnitex.twinlab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.messaging.FirebaseMessaging
import com.omnitex.twinlab.ui.Routes
import com.omnitex.twinlab.ui.alerts.AlertsScreen
import com.omnitex.twinlab.ui.alerts.AlertsViewModel
import com.omnitex.twinlab.ui.assets.AssetListScreen
import com.omnitex.twinlab.ui.assets.AssetListViewModel
import com.omnitex.twinlab.ui.detail.AssetDetailScreen
import com.omnitex.twinlab.ui.detail.AssetDetailViewModel
import com.omnitex.twinlab.ui.settings.SettingsScreen
import com.omnitex.twinlab.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as TwinLabApp).container

    /** Set from a notification tap (intent extra "deviceId"); consumed by the nav graph. */
    private var deepLinkDevice by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkDevice = intent?.getStringExtra("deviceId")

        setContent {
            MaterialTheme {
                NotificationPermissionGate()

                val baseUrl by container.settings.baseUrl.collectAsStateWithLifecycle(initialValue = null)

                LaunchedEffect(baseUrl) {
                    if (baseUrl != null) registerFcmToken()
                }

                AppRoot(
                    hasBackend = baseUrl != null,
                    container = container,
                    deepLinkDevice = deepLinkDevice,
                    onDeepLinkHandled = { deepLinkDevice = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("deviceId")?.let { deepLinkDevice = it }
    }

    private fun registerFcmToken() {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { container.api.value?.registerPushToken(token) }
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* proceed regardless */ }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AppRoot(
    hasBackend: Boolean,
    container: AppContainer,
    deepLinkDevice: String?,
    onDeepLinkHandled: () -> Unit,
) {
    val nav = rememberNavController()
    val start = if (hasBackend) Routes.ASSETS else Routes.SETTINGS

    LaunchedEffect(deepLinkDevice, hasBackend) {
        val id = deepLinkDevice ?: return@LaunchedEffect
        if (hasBackend) {
            nav.navigate(Routes.detail(id))
        } else {
            nav.navigate(Routes.ALERTS)
        }
        onDeepLinkHandled()
    }

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

        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
        ) { entry ->
            val deviceId = entry.arguments?.getString("deviceId")
            if (deviceId == null) {
                nav.popBackStack()
            } else {
                val vm: AssetDetailViewModel = viewModel(factory = container.detailFactory(deviceId))
                AssetDetailScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
