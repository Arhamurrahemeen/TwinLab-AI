package com.omnitex.twinlab

import com.omnitex.twinlab.data.Bounds
import com.omnitex.twinlab.domain.Health
import com.omnitex.twinlab.domain.healthStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthStatusTest {
    private val thr = mapOf(
        "temperature" to Bounds(min = null, max = 40.0),
        "load_current" to Bounds(min = 0.0, max = 30.0),
        "humidity" to Bounds(min = 10.0, max = 85.0),
    )

    @Test fun noReadings_isUnknown() =
        assertEquals(Health.UNKNOWN, healthStatus(emptyMap(), thr))

    @Test fun allWithinBounds_isOk() =
        assertEquals(Health.OK, healthStatus(mapOf("temperature" to 35.0, "load_current" to 18.0), thr))

    @Test fun temperatureOverMax_isCritical() =
        assertEquals(Health.CRITICAL, healthStatus(mapOf("temperature" to 96.0), thr))

    @Test fun loadCurrentOverMax_isCritical() =
        assertEquals(Health.CRITICAL, healthStatus(mapOf("load_current" to 55.0), thr))

    @Test fun humidityOverMax_isWarning() =
        assertEquals(Health.WARNING, healthStatus(mapOf("humidity" to 95.0), thr))

    @Test fun humidityBelowMin_isWarning() =
        assertEquals(Health.WARNING, healthStatus(mapOf("humidity" to 3.0), thr))

    @Test fun sensorWithoutThreshold_isIgnored() =
        assertEquals(Health.OK, healthStatus(mapOf("vibration" to 9.9), thr))

    @Test fun criticalWinsOverWarning() =
        assertEquals(
            Health.CRITICAL,
            healthStatus(mapOf("humidity" to 95.0, "temperature" to 96.0), thr),
        )
}
