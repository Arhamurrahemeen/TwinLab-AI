package com.omnitex.twinlab.ui.common

/** "just now" / "3m ago" / "2h ago" / "5d ago" from an ISO-8601 or epoch-ms string. */
fun relativeTime(isoOrEpoch: String, nowMs: Long = System.currentTimeMillis()): String {
    val thenMs = parseMillis(isoOrEpoch) ?: return isoOrEpoch
    val d = (nowMs - thenMs).coerceAtLeast(0)
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000}m ago"
        d < 86_400_000 -> "${d / 3_600_000}h ago"
        else -> "${d / 86_400_000}d ago"
    }
}

private fun parseMillis(s: String): Long? {
    s.toLongOrNull()?.let { return it }
    return try {
        // java.time is available from API 26; minSdk 24 → guard with desugaring OR fallback.
        java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        } catch (e2: Exception) {
            null
        }
    }
}
