package com.example

import com.example.data.remote.TabloTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TabloTimeTest {

    @Test
    fun parseIso8601_utcTimestamp_parsesToCorrectUtcEpoch() {
        val isoString = "2024-06-15T14:30:00Z"
        val millis = TabloTime.parseIso8601(isoString)
        assertNotNull("Parsed milliseconds should not be null", millis)

        // 2024-06-15T14:30:00Z in UTC is 1718461800000L
        assertEquals(1718461800000L, millis)

        // Verify formatting in Eastern Daylight Time (UTC-4)
        val sdfEdt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }
        val formattedEdt = sdfEdt.format(Date(millis!!))
        assertEquals("2024-06-15 10:30:00", formattedEdt)

        // Verify formatting in Pacific Daylight Time (UTC-7)
        val sdfPdt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Los_Angeles")
        }
        val formattedPdt = sdfPdt.format(Date(millis))
        assertEquals("2024-06-15 07:30:00", formattedPdt)
    }

    @Test
    fun parseIso8601_offsetTimestamp_parsesToCorrectUtcEpoch() {
        val isoWithOffset = "2024-06-15T10:30:00-04:00"
        val millis = TabloTime.parseIso8601(isoWithOffset)
        assertNotNull(millis)
        assertEquals(1718461800000L, millis)
    }

    @Test
    fun parseIso8601_numericEpoch_parsesSecondsAndMillis() {
        val secondsStr = "1718461800"
        val millisStr = "1718461800000"

        assertEquals(1718461800000L, TabloTime.parseIso8601(secondsStr))
        assertEquals(1718461800000L, TabloTime.parseIso8601(millisStr))
    }
}
