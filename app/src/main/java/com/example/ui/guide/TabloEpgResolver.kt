package com.example.ui.guide

import com.example.model.TabloAiring
import com.example.model.TabloChannel

/**
 * Resolves EPG airings using strictly REAL data from the Tablo Gen 4 API.
 * If a time slot has no real airing data, it inserts a neutral "No guide data" placeholder.
 * It NEVER invents titles, episodes, or categories.
 */
object TabloEpgResolver {

    fun resolveAiringsForChannel(
        channel: TabloChannel,
        realAirings: List<TabloAiring>,
        windowStart: Long,
        windowEnd: Long,
        now: Long = System.currentTimeMillis()
    ): List<TabloAiring> {
        val channelReal = realAirings
            .filter { it.channelId == channel.channelId }
            .sortedBy { it.startTimeMillis }

        if (channelReal.isEmpty()) {
            return listOf(
                createNoDataAiring(channel.channelId, windowStart, windowEnd, now)
            )
        }

        val result = mutableListOf<TabloAiring>()
        var pointer = windowStart

        for (airing in channelReal) {
            // Fill gap before real airing
            if (airing.startTimeMillis > pointer + 60_000L) {
                result.add(createNoDataAiring(channel.channelId, pointer, airing.startTimeMillis, now))
            }
            result.add(airing)
            pointer = maxOf(pointer, airing.endTimeMillis)
        }

        // Fill remaining tail window gap
        if (pointer < windowEnd - 60_000L) {
            result.add(createNoDataAiring(channel.channelId, pointer, windowEnd, now))
        }

        return result
    }

    private fun createNoDataAiring(
        channelId: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        now: Long
    ): TabloAiring {
        val durationSec = maxOf(60L, (endTimeMillis - startTimeMillis) / 1000L)
        return TabloAiring(
            airingId = "nodata-$channelId-$startTimeMillis",
            channelId = channelId,
            title = "No guide data",
            episodeTitle = null,
            description = "No program schedule information provided by the broadcaster.",
            startTimeMillis = startTimeMillis,
            durationSeconds = durationSec,
            category = "General",
            rating = "",
            isLive = now in startTimeMillis until endTimeMillis
        )
    }
}
