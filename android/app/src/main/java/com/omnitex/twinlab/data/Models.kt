package com.omnitex.twinlab.data

import kotlinx.serialization.Serializable

/** Bounds arrives from the backend as {"min": x|null, "max": y|null}; both optional. */
@Serializable
data class Bounds(val min: Double? = null, val max: Double? = null)

@Serializable
data class Device(
    val device_id: String,
    val name: String,
    val location: String = "",
    val plant: String? = null,
    val sensors: List<String> = emptyList(),
    val thresholds: Map<String, Bounds> = emptyMap(),
    val source: String = "simulator",
    val status: String = "active",
    val asset_type: String? = null,
    val criticality: String = "medium",
) {
    /** Grouping key for the asset list. */
    val group: String get() = plant?.takeIf { it.isNotBlank() } ?: location.ifBlank { "Unassigned" }
}

@Serializable
data class Reading(
    val device_id: String = "",
    val sensor: String = "",
    val value: Double,
    val unit: String = "",
    val ts: Long = 0,
)

@Serializable
data class Alert(
    val device_id: String,
    val sensor: String = "",
    val alert_type: String = "threshold",
    val severity: String = "warning",
    val value: Double = 0.0,
    val unit: String = "",
    val detail: String = "",
    val message_en: String = "",
    val message_ur: String = "",
    val ts: Long = 0,
    val created_at: String = "",
    val push_sent: Boolean = false,
)
