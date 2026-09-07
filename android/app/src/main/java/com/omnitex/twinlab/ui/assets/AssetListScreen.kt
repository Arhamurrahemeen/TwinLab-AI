package com.omnitex.twinlab.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.omnitex.twinlab.ui.common.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetListScreen(
    vm: AssetListViewModel,
    onOpen: (String) -> Unit,
    onAlerts: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TwinLab") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onAlerts) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { pad ->
        when (val s = state) {
            AssetListViewModel.UiState.Loading -> Center(pad) { CircularProgressIndicator() }
            is AssetListViewModel.UiState.Error -> Center(pad) {
                Text("Could not load assets:\n${s.msg}", color = MaterialTheme.colorScheme.error)
            }
            is AssetListViewModel.UiState.Content -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
            ) {
                s.groups.forEach { (group, rows) ->
                    item(key = "hdr-$group") {
                        Text(
                            group.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                        )
                    }
                    items(rows, key = { it.device.device_id }) { row ->
                        DeviceRow(row) { onOpen(row.device.device_id) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(row: AssetListViewModel.Row, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(row.health)
        Column(Modifier.fillMaxWidth()) {
            Text(row.device.name, style = MaterialTheme.typography.bodyLarge)
            val summary = row.readings.entries
                .sortedBy { it.key }
                .take(3)
                .joinToString("   ") { "${it.key.replace('_', ' ')} ${fmt(it.value)}" }
                .ifBlank { "no readings" }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

@Composable
private fun Center(pad: androidx.compose.foundation.layout.PaddingValues, content: @Composable () -> Unit) =
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .padding(pad)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
