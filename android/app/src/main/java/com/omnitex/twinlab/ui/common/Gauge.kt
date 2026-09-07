package com.omnitex.twinlab.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnitex.twinlab.data.Bounds
import com.omnitex.twinlab.domain.Health

/** Labelled numeric readout with a colored fill bar scaled to the sensor's bounds. */
@Composable
fun Gauge(
    label: String,
    value: Double,
    unit: String,
    health: Health,
    bounds: Bounds? = null,
    modifier: Modifier = Modifier,
) {
    val frac = fillFraction(value, bounds)
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label.replace('_', ' '), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${"%.2f".format(value)} $unit",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth(frac.toFloat())
                    .height(6.dp)
                    .background(health.color()),
            )
        }
    }
}

private fun fillFraction(value: Double, bounds: Bounds?): Double {
    val lo = bounds?.min ?: 0.0
    val hi = bounds?.max ?: (if (value == 0.0) 1.0 else value * 1.25)
    if (hi <= lo) return 0.5
    return ((value - lo) / (hi - lo)).coerceIn(0.0, 1.0)
}
