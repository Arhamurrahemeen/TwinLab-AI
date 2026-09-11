package com.omnitex.twinlab.domain

import com.omnitex.twinlab.data.Bounds

/**
 * Visual state for the 3D twin, derived purely from the latest readings.
 * `bodyColorArgb` / `statusRingArgb` are packed 0xAARRGGBB ints.
 */
data class TwinState(
    val bodyColorArgb: Int,
    val shakeAmplitude: Float,   // metres of jitter applied to the model node
    val rotorRpm: Float,
    val statusRingArgb: Int,
    val stale: Boolean,
)

object TwinMapping {
    // --- tunable knobs (guesses until real sensor magnitudes are measured) ---
    const val SHAKE_GAIN = 0.01f
    const val SHAKE_MAX = 0.05f
    const val TEMP_COLOR_MIN = 20.0     // fallback band when temperature has no threshold
    const val TEMP_COLOR_MAX = 80.0
    const val RUNNING_RPM = 900f
    const val IDLE_RPM = 60f
    const val RUN_EPS = 0.02            // vibration above this ⇒ "running"
    const val LOAD_ON_AMPS = 2.0        // load_current above this ⇒ "running"
    const val STALE_MS = 15_000L

    /** RGB channels of a packed ARGB int — used by tests instead of android.graphics.Color. */
    fun channels(argb: Int): Triple<Int, Int, Int> =
        Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    // Cool/OK -> hot/critical, interpolated between the same muted brand status colors
    // used everywhere else in the app (see ui/theme/Color.kt: StatusOk, StatusCritical).
    private const val COOL_R = 0x3F; private const val COOL_G = 0xA7; private const val COOL_B = 0x6B
    private const val HOT_R = 0xD9; private const val HOT_G = 0x53; private const val HOT_B = 0x4F

    private fun lerpColor(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val r = (COOL_R + (HOT_R - COOL_R) * c).toInt()
        val g = (COOL_G + (HOT_G - COOL_G) * c).toInt()
        val b = (COOL_B + (HOT_B - COOL_B) * c).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun stateFrom(
        readings: Map<String, Double>,
        thresholds: Map<String, Bounds>,
        lastMsgAgeMs: Long,
    ): TwinState {
        val temp = readings["temperature"]
        val band = thresholds["temperature"]
        val lo = band?.min ?: TEMP_COLOR_MIN
        val hi = band?.max ?: TEMP_COLOR_MAX
        val bodyColor =
            if (temp == null) 0xFF888888.toInt()
            else lerpColor(((temp - lo) / (hi - lo)).toFloat())

        val vib = readings["vibration"] ?: 0.0
        val shake = (vib * SHAKE_GAIN).toFloat().coerceIn(0f, SHAKE_MAX)

        val rpm = when {
            (readings["load_current"] ?: 0.0) > LOAD_ON_AMPS -> RUNNING_RPM
            vib > RUN_EPS -> RUNNING_RPM
            else -> IDLE_RPM
        }

        // Mirrors ui/theme/Color.kt: StatusCritical/StatusWarning/StatusOk/StatusUnknown.
        val ring = when (healthStatus(readings, thresholds)) {
            Health.CRITICAL -> 0xFFD9534F.toInt()
            Health.WARNING -> 0xFFD9A441.toInt()
            Health.OK -> 0xFF3FA76B.toInt()
            Health.UNKNOWN -> 0xFF9AA7AF.toInt()
        }

        return TwinState(bodyColor, shake, rpm, ring, lastMsgAgeMs > STALE_MS)
    }
}
