package com.omnitex.twinlab.domain

import com.omnitex.twinlab.data.Bounds

enum class Health { OK, WARNING, CRITICAL, UNKNOWN }

/** Sensors whose max-breach the backend treats as critical (backend/alerts.py _CRITICAL_MAX). */
private val CRITICAL_SENSORS = setOf("temperature", "load_current")

/**
 * Mirrors backend/alerts.py evaluate(): a reading past max/min on a critical
 * sensor → CRITICAL, on any other thresholded sensor → WARNING; a sensor with
 * no threshold entry is ignored; no readings at all → UNKNOWN.
 */
fun healthStatus(readings: Map<String, Double>, thresholds: Map<String, Bounds>): Health {
    if (readings.isEmpty()) return Health.UNKNOWN
    var worst = Health.OK
    for ((sensor, value) in readings) {
        val b = thresholds[sensor] ?: continue
        val breached = (b.max != null && value > b.max) || (b.min != null && value < b.min)
        if (!breached) continue
        if (sensor in CRITICAL_SENSORS) return Health.CRITICAL
        worst = Health.WARNING
    }
    return worst
}
