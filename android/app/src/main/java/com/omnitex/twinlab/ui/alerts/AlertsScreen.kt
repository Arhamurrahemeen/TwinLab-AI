package com.omnitex.twinlab.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitex.twinlab.data.Alert
import com.omnitex.twinlab.ui.common.TwinLabTopBar
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
            TwinLabTopBar(
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
                    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(vertical = 6.dp)) {
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
    val sev = severityColor(a.severity)
    val isCritical = a.severity.equals("critical", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(sev.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isCritical) Icons.Filled.Error else Icons.Filled.Warning,
                        contentDescription = a.severity,
                        tint = sev,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Row(
                    Modifier.padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(sev.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            a.severity.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sev,
                        )
                    }
                    Text(
                        "  ${a.device_id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                a.message_en.ifBlank { a.detail },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 9.dp),
            )

            if (a.message_ur.isNotBlank()) {
                HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                Row {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("UR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        a.message_ur,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }

            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (a.push_sent) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        " Pushed to device · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
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
}
