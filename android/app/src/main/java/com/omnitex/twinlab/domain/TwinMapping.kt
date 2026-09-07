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

    private fun lerpColor(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val r = (c * 255).toInt()
        val g = ((1 - c) * 200 + 55).toInt()
        val b = 0x30
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

        val ring = when (healthStatus(readings, thresholds)) {
            Health.CRITICAL -> 0xFFE53935.toInt()
            Health.WARNING -> 0xFFFFB300.toInt()
            Health.OK -> 0xFF43A047.toInt()
            Health.UNKNOWN -> 0xFF9E9E9E.toInt()
        }

        return TwinState(bodyColor, shake, rpm, ring, lastMsgAgeMs > STALE_MS)
    }
}
