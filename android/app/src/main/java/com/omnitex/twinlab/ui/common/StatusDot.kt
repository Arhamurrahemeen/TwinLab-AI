package com.omnitex.twinlab.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.omnitex.twinlab.domain.Health

fun Health.color(): Color = when (this) {
    Health.OK -> Color(0xFF43A047)
    Health.WARNING -> Color(0xFFFFB300)
    Health.CRITICAL -> Color(0xFFE53935)
    Health.UNKNOWN -> Color(0xFF9E9E9E)
}

fun Health.label(): String = when (this) {
    Health.OK -> "OK"
    Health.WARNING -> "WARNING"
    Health.CRITICAL -> "CRITICAL"
    Health.UNKNOWN -> "NO DATA"
}

@Composable
fun StatusDot(health: Health, modifier: Modifier = Modifier, size: Int = 12) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(size.dp)
            .background(health.color(), CircleShape),
    )
}

/** Severity strip / text color for an alert severity string. */
@Composable
fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "critical" -> Color(0xFFE53935)
    "warning" -> Color(0xFFFFB300)
    else -> MaterialTheme.colorScheme.outline
}
