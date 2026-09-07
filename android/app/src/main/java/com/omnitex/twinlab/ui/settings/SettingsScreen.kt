package com.omnitex.twinlab.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onSaved: () -> Unit,
    canGoBack: Boolean = false,
    onBack: () -> Unit = {},
) {
    val current by vm.currentUrl.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()

    var text by remember(current) { mutableStateOf(current ?: "") }

    LaunchedEffect(test) {
        if (test is TestResult.Ok) onSaved()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Backend connection") }) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Enter the TwinLab backend address. Use the laptop's LAN IP — " +
                    "not localhost — and start uvicorn with --host 0.0.0.0.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Base URL") },
                placeholder = { Text("http://192.168.1.100:8000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.testAndSave(text) },
                enabled = text.isNotBlank() && test !is TestResult.Testing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test & Save") }

            when (val t = test) {
                TestResult.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Text("  Testing…")
                }
                TestResult.Ok -> Text("✓ Connected", color = MaterialTheme.colorScheme.primary)
                is TestResult.Failed -> Text("✗ ${t.reason}", color = MaterialTheme.colorScheme.error)
                TestResult.Idle -> {}
            }

            if (canGoBack) {
                TextButton(onClick = onBack) { Text("Cancel") }
            }
        }
    }
}
