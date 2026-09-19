package com.example.data.remote

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Robust ISO-8601 and timestamp parser that works across all Tablo firmware
 * and cloud API variations, supporting formats like:
 * - "2023-01-03T01:00Z"
 * - "2023-01-03T01:00:00Z"
 * - "2023-01-03T01:00:00.000Z"
 * - "2023-01-03T01:00:00-05:00"
 * - "2023-01-03 01:00:00"
 * - Unix epoch timestamp (seconds or milliseconds)
 */
object TabloTime {

    private val pattern = Regex(
        """(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?"""
    )

    fun parseIso8601(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()

        // 1. Check if numeric timestamp (seconds or milliseconds)
        val numeric = trimmed.toLongOrNull()
        if (numeric != null) {
            return if (numeric < 100_000_000_000L) numeric * 1000L else numeric
        }

        // 2. Regex ISO-8601 parsing
        val match = pattern.matchEntire(trimmed)
        if (match != null) {
            val groups = match.groupValues
            val year = groups[1].toInt()
            val month = groups[2].toInt()
            val day = groups[3].toInt()
            val hour = groups[4].toInt()
            val minute = groups[5].toInt()
            val second = groups.getOrNull(6).orEmpty().ifEmpty { "0" }.toInt()
            val frac = groups.getOrNull(7).orEmpty().take(3).padEnd(3, '0').toIntOrNull() ?: 0

            if (month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59 && second in 0..59) {
                var utcMillis = Date.UTC(year - 1900, month - 1, day, hour, minute, second) + frac
                val zone = groups.getOrNull(8).orEmpty()
                if (zone.isNotEmpty() && zone != "Z") {
                    val negative = zone.startsWith("-")
                    val body = zone.drop(1).replace(":", "")
                    val offsetHours = body.take(2).toIntOrNull() ?: 0
                    val offsetMinutes = body.drop(2).take(2).toIntOrNull() ?: 0
                    val offsetMillis = (offsetHours * 60 + offsetMinutes) * 60_000L
                    utcMillis = if (negative) utcMillis + offsetMillis else utcMillis - offsetMillis
                }
                return utcMillis
            }
        }

        // 3. Fallback SimpleDateFormat parsing
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mmX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }

        return null
    }
}
