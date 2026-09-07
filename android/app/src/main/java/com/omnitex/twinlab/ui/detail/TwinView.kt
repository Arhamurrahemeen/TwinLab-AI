package com.omnitex.twinlab.ui.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.omnitex.twinlab.domain.TwinState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The per-asset digital twin. A stylised generator rendered with Compose Canvas —
 * body colour tracks temperature, the rotor spins at `rotorRpm`, the whole unit
 * jitters with `shakeAmplitude`, and a ground ring shows health.
 *
 * ponytail: 2D canvas, not a real GLTF scene. A SceneView/Filament twin with
 * `app/src/main/assets/twin_rig.glb` is the upgrade path — see phase-14.md.
 * This version needs no model asset, no Filament init, and always renders.
 */
@Composable
fun TwinView(state: TwinState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "twin")

    val rotorAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (60_000f / state.rotorRpm.coerceAtLeast(1f)).toInt().coerceIn(80, 4000),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotor",
    )

    val shakePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Restart),
        label = "shake",
    )

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.4f)) {
            val body = Color(state.bodyColorArgb)
            val ring = Color(state.statusRingArgb)
            val cx = size.width / 2
            val cy = size.height * 0.55f
            val bodyW = size.width * 0.42f
            val bodyH = size.height * 0.34f

            // shake offset (px) — SHAKE amplitude is in "metres"; scale for screen
            val jitter = state.shakeAmplitude * 1200f
            val dx = cos(shakePhase) * jitter + Random(shakePhase.toRawBits()).nextFloat() * jitter * 0.3f
            val dy = sin(shakePhase * 1.7f) * jitter

            // ground health ring
            drawOval(
                color = ring.copy(alpha = 0.35f),
                topLeft = Offset(cx - bodyW * 1.15f, cy + bodyH * 0.35f),
                size = androidx.compose.ui.geometry.Size(bodyW * 2.3f, bodyH * 0.5f),
            )

            rotate(degrees = 0f) {
                // body
                drawRoundRect(
                    color = body,
                    topLeft = Offset(cx - bodyW + dx, cy - bodyH + dy),
                    size = androidx.compose.ui.geometry.Size(bodyW * 2, bodyH * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                )
                // rotor housing
                val rotorR = bodyH * 0.7f
                val rcx = cx + bodyW * 0.55f + dx
                val rcy = cy + dy
                drawCircle(color = body.copy(alpha = 0.6f), radius = rotorR * 1.15f, center = Offset(rcx, rcy))
                // spinning rotor blades
                rotate(degrees = rotorAngle, pivot = Offset(rcx, rcy)) {
                    repeat(4) { i ->
                        rotate(degrees = i * 45f, pivot = Offset(rcx, rcy)) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.85f),
                                topLeft = Offset(rcx - rotorR, rcy - rotorR * 0.12f),
                                size = androidx.compose.ui.geometry.Size(rotorR * 2, rotorR * 0.24f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                            )
                        }
                    }
                    drawCircle(color = body, radius = rotorR * 0.18f, center = Offset(rcx, rcy))
                }
            }
        }

        if (state.stale) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.4f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "SIGNAL LOST",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
