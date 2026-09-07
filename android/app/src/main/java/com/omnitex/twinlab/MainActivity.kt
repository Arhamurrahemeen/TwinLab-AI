package com.omnitex.twinlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

    Scaffold { pad ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(pad),
        ) {
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = container.factory)
                SettingsScreen(
                    vm = vm,
                    onSaved = {
                        nav.navigate(Routes.ASSETS) {
                            popUpTo(Routes.SETTINGS) { inclusive = true }
                        }
                    },
                    canGoBack = hasBackend,
                    onBack = { nav.popBackStack() },
                )
            }
            // Task 7 replaces this placeholder with AssetListScreen.
            composable(Routes.ASSETS) {
                Placeholder("assets — Task 7", onGoSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Routes.ALERTS) { Placeholder("alerts — Task 7") }
            composable(Routes.DETAIL) { Placeholder("detail — Task 8") }
        }
    }
}

@Composable
private fun Placeholder(label: String, onGoSettings: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (onGoSettings != null) {
            androidx.compose.material3.TextButton(onClick = onGoSettings) { Text("$label · open Settings") }
        } else {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
