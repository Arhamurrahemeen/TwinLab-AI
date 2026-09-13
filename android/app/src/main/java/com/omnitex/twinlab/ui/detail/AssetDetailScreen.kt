package com.omnitex.twinlab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitex.twinlab.ui.common.Gauge
import com.omnitex.twinlab.ui.common.Sparkline
import com.omnitex.twinlab.ui.common.TwinLabTopBar
import com.omnitex.twinlab.ui.common.color
import com.omnitex.twinlab.ui.common.label
import com.omnitex.twinlab.ui.common.relativeTime
import com.omnitex.twinlab.ui.common.severityColor

private const val SIGNAL_LOST_MS = 15_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(vm: AssetDetailViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val twin by vm.twin.collectAsStateWithLifecycle()
    val title = s.device?.name ?: "Asset"

    Scaffold(
        topBar = {
            TwinLabTopBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
        ) {
            item { TwinView(twin) }

            item {
                val signalLost = s.lastMsgAgeMs > SIGNAL_LOST_MS
                Text(
                    if (signalLost) "SIGNAL LOST" else s.health.label(),
                    color = if (signalLost) MaterialTheme.colorScheme.error else s.health.color(),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            s.error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
            }

            items(s.readings.entries.sortedBy { it.key }.toList(), key = { "g-${it.key}" }) { (sensor, value) ->
                Gauge(
                    label = sensor,
                    value = value,
                    unit = "",
                    health = s.health,
                    bounds = s.device?.thresholds?.get(sensor),
                )
            }

            items(s.history.entries.filter { it.value.size >= 2 }.toList(), key = { "s-${it.key}" }) { (sensor, pts) ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(sensor.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
                    Sparkline(pts.map { it.value })
                }
            }

            if (s.alerts.isNotEmpty()) {
                item {
                    Text(
                        "Recent alerts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(s.alerts.take(20), key = { "a-${it.ts}-${it.sensor}" }) { a ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "${a.severity.uppercase()} · ${a.sensor.replace('_', ' ')}",
                            color = severityColor(a.severity),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(a.message_en.ifBlank { a.detail }, style = MaterialTheme.typography.bodySmall)
                        Text(
                            relativeTime(a.created_at.ifBlank { a.ts.toString() }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}
