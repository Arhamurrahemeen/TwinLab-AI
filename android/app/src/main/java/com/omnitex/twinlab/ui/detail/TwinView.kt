package com.omnitex.twinlab.ui.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.omnitex.twinlab.domain.TwinState
import kotlin.math.cos
import kotlin.math.sin

/**
 * The per-asset digital twin. An isometric-shaded unit rendered with Compose Canvas —
 * three shaded faces track temperature (bodyColorArgb, lightest on top/darkest on the
 * right — a fixed light-from-top-left model), a neon rim + fan hub track health
 * (statusRingArgb), the fan spins at rotorRpm, and the whole unit jitters with
 * shakeAmplitude.
 *
 * All geometry below is expressed in one fixed 340x260 unit space (matching the design
 * mockup) and scaled to the actual canvas by `k` — locking the aspect ratio to the same
 * 340:260 keeps that scaling uniform in both axes.
 *
 * ponytail: 2D isometric canvas, not a real GLTF scene. A SceneView/Filament twin with
 * `app/src/main/assets/twin_rig.glb` is the upgrade path — see
 * `android/app/src/main/assets/README-twin-model.md`. This version needs no model
 * asset, no Filament init, and always renders.
 *
 * Redesigned 2026-09-13 from a Claude Design mockup (flat rounded-rect + oversized fan
 * -> isometric shading + a fan properly inset in its grille). The in-graphic
 * "SIGNAL LOST" text was dropped — AssetDetailScreen already prints that same text
 * right below this composable; showing it twice was a pre-existing duplication.
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

    val ringColor = if (state.stale) Color(0xFF9AA7AF) else Color(state.statusRingArgb)
    val bodyArgb = state.bodyColorArgb

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(UNIT_W / UNIT_H)
            .padding(vertical = 12.dp)
            .alpha(if (state.stale) 0.6f else 1f),
    ) {
        val k = size.width / UNIT_W
        fun pt(x: Float, y: Float) = Offset(x * k, y * k)

        val jitter = state.shakeAmplitude * 1200f
        val dx = cos(shakePhase) * jitter + sin(shakePhase * 2.3f) * jitter * 0.3f
        val dy = sin(shakePhase * 1.7f) * jitter

        translate(left = dx, top = dy) {
            // ambient status glow + ground shadow
            val glowCenter = pt(170f, 140f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = 0.42f), ringColor.copy(alpha = 0f)),
                    center = glowCenter,
                    radius = 112f * k,
                ),
                radius = 112f * k,
                center = glowCenter,
            )
            drawOval(
                color = Color(0xFF08131F).copy(alpha = 0.28f),
                topLeft = pt(74f, 209f),
                size = Size(192f * k, 26f * k),
            )

            // three isometric faces, shaded from a single fixed top-left light source
            val topFace = quad(::pt, 170f to 53f, 255f to 95f, 170f to 137f, 85f to 95f)
            val leftFace = quad(::pt, 85f to 95f, 170f to 137f, 170f to 212f, 85f to 170f)
            val rightFace = quad(::pt, 170f to 137f, 255f to 95f, 255f to 170f, 170f to 212f)

            drawPath(rightFace, brush = faceBrush(bodyArgb, 0.52f, 0.36f, pt(170f, 137f).y, pt(170f, 212f).y))
            drawPath(leftFace, brush = faceBrush(bodyArgb, 0.80f, 0.62f, pt(85f, 95f).y, pt(85f, 170f).y))
            drawPath(topFace, brush = faceBrush(bodyArgb, 1.15f, 0.95f, pt(170f, 53f).y, pt(170f, 137f).y))

            // seam + top-edge highlight for depth
            drawLine(Color(0xFF08131F).copy(alpha = 0.35f), pt(170f, 137f), pt(170f, 212f), strokeWidth = 1.5f * k)
            drawPath(
                Path().apply {
                    moveTo(pt(85f, 95f).x, pt(85f, 95f).y)
                    lineTo(pt(170f, 53f).x, pt(170f, 53f).y)
                    lineTo(pt(255f, 95f).x, pt(255f, 95f).y)
                },
                color = Color.White.copy(alpha = 0.20f),
                style = Stroke(width = 1.5f * k),
            )

            // neon status rim on the top face — layered strokes fake a soft glow
            drawPath(topFace, color = ringColor.copy(alpha = 0.12f), style = Stroke(width = 10f * k))
            drawPath(topFace, color = ringColor.copy(alpha = 0.22f), style = Stroke(width = 6f * k))
            drawPath(topFace, color = ringColor, style = Stroke(width = 2.25f * k))

            // vent slats + rivets on the left face
            drawLine(Color.White.copy(alpha = 0.10f), pt(102f, 130f), pt(153f, 155f), strokeWidth = 2f * k)
            drawLine(Color.White.copy(alpha = 0.10f), pt(102f, 141f), pt(153f, 166f), strokeWidth = 2f * k)
            drawLine(Color.White.copy(alpha = 0.10f), pt(102f, 152f), pt(153f, 177f), strokeWidth = 2f * k)
            drawCircle(Color(0xFF08131F).copy(alpha = 0.4f), radius = 2.4f * k, center = pt(100f, 112f))
            drawCircle(Color(0xFF08131F).copy(alpha = 0.4f), radius = 2.4f * k, center = pt(155f, 152f))

            // fan grille, inset into the top face
            val fanCenter = pt(170f, 95f)
            drawOval(
                color = Color(0xFF0B1B2B),
                topLeft = fanCenter - Offset(43f * k, 21f * k),
                size = Size(86f * k, 42f * k),
            )
            drawOval(
                color = Color(0xFF4A6C88).copy(alpha = 0.5f),
                topLeft = fanCenter - Offset(40f * k, 19.5f * k),
                size = Size(80f * k, 39f * k),
                style = Stroke(width = 1.5f * k),
            )

            translate(left = fanCenter.x, top = fanCenter.y) {
                scale(scaleX = 1f, scaleY = 0.465f, pivot = Offset.Zero) {
                    rotate(degrees = rotorAngle, pivot = Offset.Zero) {
                        val blade = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(-5f * k, -34f * k)
                            quadraticTo(0f, -40f * k, 5f * k, -34f * k)
                            close()
                        }
                        repeat(5) { i ->
                            rotate(degrees = i * 72f, pivot = Offset.Zero) {
                                drawPath(blade, color = Color(0xFFE8EDF2), alpha = 0.92f)
                            }
                        }
                        drawCircle(color = ringColor, radius = 9f * k, center = Offset.Zero)
                    }
                }
            }
        }
    }
}

private const val UNIT_W = 340f
private const val UNIT_H = 260f

private fun quad(pt: (Float, Float) -> Offset, a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>, d: Pair<Float, Float>) =
    Path().apply {
        val pa = pt(a.first, a.second); moveTo(pa.x, pa.y)
        val pb = pt(b.first, b.second); lineTo(pb.x, pb.y)
        val pc = pt(c.first, c.second); lineTo(pc.x, pc.y)
        val pd = pt(d.first, d.second); lineTo(pd.x, pd.y)
        close()
    }

/** A face's 2-stop gradient: two shades of the same temperature-driven body color. */
private fun faceBrush(bodyArgb: Int, lightFactor: Float, darkFactor: Float, startY: Float, endY: Float): Brush {
    val base = Color(bodyArgb)
    fun shade(f: Float) = Color(
        red = (base.red * f).coerceIn(0f, 1f),
        green = (base.green * f).coerceIn(0f, 1f),
        blue = (base.blue * f).coerceIn(0f, 1f),
        alpha = 1f,
    )
    return Brush.verticalGradient(colors = listOf(shade(lightFactor), shade(darkFactor)), startY = startY, endY = endY)
}
