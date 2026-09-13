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
import com.omnitex.twinlab.ui.theme.StatusCritical
import com.omnitex.twinlab.ui.theme.StatusOk
import com.omnitex.twinlab.ui.theme.StatusUnknown
import com.omnitex.twinlab.ui.theme.StatusWarning

fun Health.color(): Color = when (this) {
    Health.OK -> StatusOk
    Health.WARNING -> StatusWarning
    Health.CRITICAL -> StatusCritical
    Health.UNKNOWN -> StatusUnknown
}

fun Health.label(): String = when (this) {
    Health.OK -> "OK"
    Health.WARNING -> "WARNING"
    Health.CRITICAL -> "CRITICAL"
    Health.UNKNOWN -> "NO DATA"
}

/** A status dot with a soft translucent glow ring — echoes the digital twin's status rim. */
@Composable
fun StatusDot(health: Health, modifier: Modifier = Modifier, size: Int = 12) {
    val color = health.color()
    androidx.compose.foundation.layout.Box(
        modifier.size((size * 2.2f).dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size((size * 2.2f).dp)
                .background(color.copy(alpha = 0.16f), CircleShape),
        )
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(size.dp)
                .background(color, CircleShape),
        )
    }
}

/** Severity strip / text color for an alert severity string. */
@Composable
fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "critical" -> StatusCritical
    "warning" -> StatusWarning
    else -> MaterialTheme.colorScheme.outline
}
