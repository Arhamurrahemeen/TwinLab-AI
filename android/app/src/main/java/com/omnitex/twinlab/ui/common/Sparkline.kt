package com.omnitex.twinlab.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/** Minimal polyline chart — no chart library. Expects points in chronological order. */
@Composable
fun Sparkline(
    points: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF0B5FFF),
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val dx = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * dx
            val y = size.height - ((v - min) / span).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        // last-point dot
        val lastX = (points.size - 1) * dx
        val lastY = size.height - ((points.last() - min) / span).toFloat() * size.height
        drawCircle(color, radius = 3f, center = Offset(lastX, lastY))
    }
}
