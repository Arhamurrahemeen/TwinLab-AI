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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omnitex.twinlab.ui.Routes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { AppRoot() }
        }
    }
}

/** Task 5 scaffold — placeholder routes. Task 6 replaces this with the
 *  Settings-URL gate and Task 7+ wires the real screens. */
@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    Scaffold { pad ->
        NavHost(
            navController = nav,
            startDestination = Routes.ASSETS,
            modifier = Modifier.padding(pad),
        ) {
            composable(Routes.ASSETS) { Placeholder("assets") }
            composable(Routes.ALERTS) { Placeholder("alerts") }
            composable(Routes.SETTINGS) { Placeholder("settings") }
            composable(Routes.DETAIL) { Placeholder("detail") }
        }
    }
}

@Composable
private fun Placeholder(route: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(route, style = MaterialTheme.typography.headlineMedium)
    }
}
