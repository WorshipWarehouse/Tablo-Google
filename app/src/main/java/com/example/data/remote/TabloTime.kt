package com.example.data.remote

import java.util.Date

/**
 * Lightweight ISO-8601 datetime parser that works on all supported API levels
 * without core library desugaring. Supports the formats returned by the Tablo
 * API, e.g. "2023-01-03T01:00Z" and "2023-01-03T01:00:00-05:00".
 */
object TabloTime {

    private val pattern = Regex(
        """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(Z|[+-]\d{2}:?\d{2})?"""
    )

    fun parseIso8601(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val match = pattern.matchEntire(value.trim()) ?: return null
        val groups = match.groupValues
        if (groups.size < 6) return null

        val year = groups[1].toInt()
        val month = groups[2].toInt()
        val day = groups[3].toInt()
        val hour = groups[4].toInt()
        val minute = groups[5].toInt()
        val second = groups.getOrNull(6).orEmpty().ifEmpty { "0" }.toInt()

        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 59) return null

        // Date.UTC treats months as 0-indexed and returns milliseconds since the epoch.
        val utcMillis = Date.UTC(year - 1900, month - 1, day, hour, minute, second)

        val zone = groups.getOrNull(7).orEmpty()
        if (zone.isEmpty() || zone == "Z") return utcMillis

        val negative = zone.startsWith("-")
        val body = zone.drop(1).replace(":", "")
        val offsetHours = body.slice(0..1).toInt()
        val offsetMinutes = body.drop(2).take(2).toIntOrNull() ?: 0
        val offsetMillis = (offsetHours * 60 + offsetMinutes) * 60_000L
        return if (negative) utcMillis + offsetMillis else utcMillis - offsetMillis
    }
}