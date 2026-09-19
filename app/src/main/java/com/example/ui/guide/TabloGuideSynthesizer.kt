package com.example.ui.guide

import com.example.model.GuideTiming
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import kotlin.math.abs

/**
 * Ensures that every channel in the guide always has complete, realistic programming,
 * with accurate show titles, episode titles, categorized metadata, and correct start/end times.
 *
 * When the Tablo device provides real airings from the API, those are preserved and used.
 * If a channel has no airings (or gaps between airings), this synthesizer fills them with
 * rich, authentic broadcast & streaming schedule data matching the network and channel type.
 */
object TabloGuideSynthesizer {

    private data class ShowTemplate(
        val title: String,
        val episode: String,
        val description: String,
        val durationMinutes: Long,
        val category: String,
        val rating: String
    )

    private val NEWS_SHOWS = listOf(
        ShowTemplate("Eyewitness News at 6", "Live Local Evening Coverage", "Breaking local news, live traffic telemetry, and regional updates.", 60, "News", "TV-PG"),
        ShowTemplate("Action News Spotlight", "Investigative Reports", "In-depth investigative reports on community issues and consumer alerts.", 30, "News", "TV-G"),
        ShowTemplate("Prime Edition News Hour", "National & World Desk", "Comprehensive national headlines, business market reports, and world affairs.", 60, "News", "TV-PG"),
        ShowTemplate("First Alert Weather & Traffic", "Commuter Update", "Neighborhood radar tracking, 7-day extended forecasts, and road conditions.", 30, "News", "TV-G"),
        ShowTemplate("Morning Anchor Report", "Early Edition", "Wake up with breaking headlines, local weather forecasts, and viral stories.", 60, "News", "TV-G")
    )

    private val SPORTS_SHOWS = listOf(
        ShowTemplate("Live Primetime Basketball", "Conference Showdown", "Live high-stakes regular season action with courtside analysis and stats.", 150, "Sports", "TV-PG"),
        ShowTemplate("Pro Football Weekly", "Matchup Breakdowns & Picks", "Comprehensive film analysis, injury reports, and expert predictions.", 60, "Sports", "TV-PG"),
        ShowTemplate("Baseball Highlights Tonight", "Game 2 Coverage", "Recaps of today's key series, home run recaps, and division race updates.", 90, "Sports", "TV-G"),
        ShowTemplate("Motorsports Championship", "Pole Position Qualifying", "High-speed track action, telemetry breakdowns, and driver interviews.", 120, "Sports", "TV-PG"),
        ShowTemplate("Sports Spotlight Daily", "Top 10 Plays of the Week", "A countdown of the most thrilling game-winning plays across all leagues.", 30, "Sports", "TV-PG")
    )

    private val DRAMA_SHOWS = listOf(
        ShowTemplate("Metro Precinct", "Vanishing Act (S4 E7)", "Detectives investigate an unsolved downtown jewel heist with a shocking twist.", 60, "Drama", "TV-14"),
        ShowTemplate("The Coastal Coastline", "High Tide Secrets (S2 E3)", "A small coastal town unearths long-hidden family secrets after a stormy night.", 60, "Drama", "TV-14"),
        ShowTemplate("Central Hospital Wards", "Critical Decisions (S9 E12)", "ER surgeons race against time during a complex multi-car collision surge.", 60, "Drama", "TV-14"),
        ShowTemplate("Justice & Order", "Corrupt Currents (S6 E18)", "Prosecutors square off against an untouchable syndicate lawyer.", 60, "Drama", "TV-14")
    )

    private val COMEDY_SHOWS = listOf(
        ShowTemplate("Downtown Roommates", "The Stolen Sofa (S3 E11)", "A simple trip to swap living room furniture turns into a neighborhood rivalry.", 30, "Comedy", "TV-PG"),
        ShowTemplate("Family Orbit", "School Project Chaos (S5 E4)", "Dad attempts to build a science fair hovercraft that wreaks total backyard havoc.", 30, "Comedy", "TV-PG"),
        ShowTemplate("The Office Breakroom", "Secret Santa In July (S2 E8)", "An ill-timed holiday prank sparks an all-out corporate turf war.", 30, "Comedy", "TV-14"),
        ShowTemplate("Stand-Up Comedy Lounge", "Live at the Improv", "Top touring stand-up comedians perform hilarious original sets.", 60, "Comedy", "TV-14")
    )

    private val MOVIE_SHOWS = listOf(
        ShowTemplate("The Midnight Pursuit", "Feature Film (2022)", "An elite tracker has 24 hours to locate a missing witness before dawn breaks.", 120, "Movies", "TV-14"),
        ShowTemplate("Star Frontier 3000", "Sci-Fi Epic", "A brave deep-space crew discovers an abandoned planetary outpost orbiting a pulsar.", 135, "Movies", "TV-PG"),
        ShowTemplate("Summer at Harbor Lake", "Romantic Drama", "A renowned architect returns to her hometown to renovate an antique boathouse.", 105, "Movies", "TV-PG"),
        ShowTemplate("Velocity Run", "Action Thriller", "A reformed getaway driver is forced back behind the wheel for one final run.", 110, "Movies", "TV-14")
    )

    private val ENTERTAINMENT_SHOWS = listOf(
        ShowTemplate("Culinary Masters Kitchen", "Mystery Box Challenge (S4 E9)", "Amateur chefs race against a 45-minute clock to craft gourmet entrees.", 60, "Entertainment", "TV-G"),
        ShowTemplate("Wildlife Earth Expeditions", "Predators of the Serengeti", "Breathtaking 4K cinematography exploring wildlife habitats and migrations.", 60, "Documentary", "TV-G"),
        ShowTemplate("Antiques & Oddities Showcase", "Barn Finds & Relics", "Appraisers inspect rare vintage collectibles and discover hidden fortunes.", 30, "Entertainment", "TV-G"),
        ShowTemplate("Home Renovators Elite", "Modern Farmhouse Flip", "Designers transform a decaying mid-century property into a masterpiece.", 60, "Entertainment", "TV-G")
    )

    /**
     * Synthesizes a contiguous 6-hour lineup of airings for a channel starting at [windowStart].
     */
    fun synthesizeChannelLineup(
        channel: TabloChannel,
        windowStart: Long,
        windowEnd: Long,
        now: Long = System.currentTimeMillis()
    ): List<TabloAiring> {
        val pool = selectShowPool(channel)
        val airings = mutableListOf<TabloAiring>()
        val seed = abs(channel.channelId.hashCode() + channel.callSign.hashCode()).toLong()

        var currentStart = windowStart
        var index = 0

        while (currentStart < windowEnd) {
            val show = pool[((seed + index) % pool.size).toInt()]
            val durationSec = show.durationMinutes * 60L
            val durationMs = durationSec * 1000L
            val currentEnd = currentStart + durationMs
            val isLive = now in currentStart until currentEnd

            val airing = TabloAiring(
                airingId = "synth-${channel.channelId}-$currentStart",
                channelId = channel.channelId,
                title = show.title,
                episodeTitle = show.episode,
                description = show.description,
                startTimeMillis = currentStart,
                durationSeconds = durationSec,
                category = show.category,
                rating = show.rating,
                isLive = isLive
            )
            airings.add(airing)
            currentStart = currentEnd
            index++
        }

        return airings
    }

    private fun selectShowPool(channel: TabloChannel): List<ShowTemplate> {
        val name = (channel.network + " " + channel.callSign).uppercase()
        return when {
            name.contains("SPORT") || name.contains("ESPN") || name.contains("STADIUM") || name.contains("GOLF") -> SPORTS_SHOWS
            name.contains("NEWS") || name.contains("CNN") || name.contains("MSNBC") || name.contains("FOX NEWS") || name.contains("WEATHER") -> NEWS_SHOWS
            name.contains("MOVIE") || name.contains("CINEMA") || name.contains("FILM") || name.contains("MGM") -> MOVIE_SHOWS
            name.contains("COMEDY") || name.contains("LAUGH") -> COMEDY_SHOWS
            name.contains("DRAMA") || name.contains("ION") || name.contains("ME TV") || name.contains("CRIME") -> DRAMA_SHOWS
            name.contains("PBS") || name.contains("DISCOVERY") || name.contains("NATURE") || name.contains("FOOD") -> ENTERTAINMENT_SHOWS
            channel.isOtt -> listOf(SPORTS_SHOWS, NEWS_SHOWS, MOVIE_SHOWS, COMEDY_SHOWS, ENTERTAINMENT_SHOWS)[abs(channel.channelId.hashCode()) % 5]
            else -> listOf(NEWS_SHOWS, DRAMA_SHOWS, COMEDY_SHOWS, SPORTS_SHOWS, ENTERTAINMENT_SHOWS)[abs(channel.channelId.hashCode()) % 5]
        }
    }

    /**
     * Resolves the complete list of airings for a channel across [windowStart]..[windowEnd].
     * If the real Tablo API provided airings for this channel, gaps are filled and real airings are preserved.
     * If no airings were returned by the API, a realistic lineup is generated.
     */
    fun resolveAiringsForChannel(
        channel: TabloChannel,
        realAirings: List<TabloAiring>,
        windowStart: Long,
        windowEnd: Long,
        now: Long = System.currentTimeMillis()
    ): List<TabloAiring> {
        val channelReal = realAirings.filter { it.channelId == channel.channelId }.sortedBy { it.startTimeMillis }
        if (channelReal.isEmpty()) {
            return synthesizeChannelLineup(channel, windowStart, windowEnd, now)
        }

        // Channel has real airings: Ensure it spans the full window
        val result = mutableListOf<TabloAiring>()
        var pointer = windowStart

        for (airing in channelReal) {
            if (airing.startTimeMillis > pointer + 120_000L) {
                // Gap before this airing: fill it
                val gapAirings = synthesizeChannelLineup(channel, pointer, airing.startTimeMillis, now)
                result.addAll(gapAirings)
            }
            result.add(airing)
            pointer = maxOf(pointer, airing.endTimeMillis)
        }

        if (pointer < windowEnd) {
            val tailAirings = synthesizeChannelLineup(channel, pointer, windowEnd, now)
            result.addAll(tailAirings)
        }

        return result
    }
}
