package com.omnitex.twinlab.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitex.twinlab.data.Alert
import com.omnitex.twinlab.ui.common.relativeTime
import com.omnitex.twinlab.ui.common.severityColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    vm: AlertsViewModel,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        when (val s = state) {
            AlertsViewModel.UiState.Loading -> Box(
                Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is AlertsViewModel.UiState.Error -> Box(
                Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center,
            ) { Text("Could not load alerts:\n${s.msg}", color = MaterialTheme.colorScheme.error) }

            is AlertsViewModel.UiState.Content ->
                if (s.alerts.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center,
                    ) { Text("No alerts.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                        items(s.alerts, key = { it.device_id + it.ts + it.sensor }) { a ->
                            AlertCard(a) { onOpen(a.device_id) }
                        }
                    }
                }
        }
    }
}

@Composable
private fun AlertCard(a: Alert, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(severityColor(a.severity)),
        )
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    "${a.device_id} · ${a.severity.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = severityColor(a.severity),
                )
                if (a.push_sent) Text("  📲", style = MaterialTheme.typography.labelMedium)
            }
            Text(a.message_en.ifBlank { a.detail }, style = MaterialTheme.typography.bodyMedium)
            if (a.message_ur.isNotBlank()) {
                Text(
                    a.message_ur,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                relativeTime(a.created_at.ifBlank { a.ts.toString() }),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
