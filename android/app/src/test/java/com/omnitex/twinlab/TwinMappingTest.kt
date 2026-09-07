package com.omnitex.twinlab

import com.omnitex.twinlab.data.Bounds
import com.omnitex.twinlab.domain.TwinMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwinMappingTest {
    private val thr = mapOf(
        "temperature" to Bounds(null, 40.0),
        "load_current" to Bounds(0.0, 30.0),
    )

    @Test fun coolTemp_isGreenish() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 10.0), thr, 0)
        val (r, g, _) = TwinMapping.channels(s.bodyColorArgb)
        assertTrue("expected green>red, got r=$r g=$g", g > r)
    }

    @Test fun hotTemp_isReddish() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 60.0), thr, 0)
        val (r, g, _) = TwinMapping.channels(s.bodyColorArgb)
        assertTrue("expected red>green, got r=$r g=$g", r > g)
    }

    @Test fun highVibration_shakesButClamped() {
        val s = TwinMapping.stateFrom(mapOf("vibration" to 999.0), thr, 0)
        assertEquals(TwinMapping.SHAKE_MAX, s.shakeAmplitude, 0.0001f)
    }

    @Test fun noVibration_noShake() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 20.0), thr, 0)
        assertEquals(0f, s.shakeAmplitude, 0.0001f)
    }

    @Test fun runningWhenLoadCurrentHigh() {
        val s = TwinMapping.stateFrom(mapOf("load_current" to 18.0), thr, 0)
        assertEquals(TwinMapping.RUNNING_RPM, s.rotorRpm, 0.01f)
    }

    @Test fun idleWhenNothingMoving() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 20.0), thr, 0)
        assertEquals(TwinMapping.IDLE_RPM, s.rotorRpm, 0.01f)
    }

    @Test fun staleWhenOld() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 20.0), thr, TwinMapping.STALE_MS + 1)
        assertTrue(s.stale)
    }

    @Test fun freshWhenRecent() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 20.0), thr, 0)
        assertTrue(!s.stale)
    }
}
