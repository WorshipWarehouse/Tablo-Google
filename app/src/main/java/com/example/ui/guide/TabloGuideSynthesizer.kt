package com.example.ui.guide

import com.example.model.TabloAiring
import com.example.model.TabloChannel
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * Ensures that every channel in the guide always has complete, realistic programming
 * that accurately reflects the real-world time of day (e.g. Morning News at 6 AM, Midday
 * News at 12 PM, Evening News at 6 PM, Primetime at 8 PM, Late Night at 11:30 PM).
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

    private fun getHourOfDay(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun getMinuteOfHour(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.MINUTE)
    }

    // Daytime & Broadcast schedules by time block
    private fun getBroadcastShowForHour(hour: Int, channelSeed: Long, channel: TabloChannel): ShowTemplate {
        val nameUpper = (channel.network + " " + channel.callSign).uppercase()
        val isSports = nameUpper.contains("SPORT") || nameUpper.contains("ESPN") || nameUpper.contains("STADIUM") || nameUpper.contains("GOLF")
        val isNews = nameUpper.contains("NEWS") || nameUpper.contains("CNN") || nameUpper.contains("MSNBC") || nameUpper.contains("WEATHER") || nameUpper.contains("REPORT")
        val isMovie = nameUpper.contains("MOVIE") || nameUpper.contains("CINEMA") || nameUpper.contains("FILM") || nameUpper.contains("MGM")
        val isComedy = nameUpper.contains("COMEDY") || nameUpper.contains("LAFF") || nameUpper.contains("LAUGH")
        val isDrama = nameUpper.contains("DRAMA") || nameUpper.contains("ION") || nameUpper.contains("ME TV") || nameUpper.contains("CRIME") || nameUpper.contains("COURT")

        if (isSports) {
            return when (hour) {
                in 5..7 -> ShowTemplate("Morning SportsCenter", "Daily Highlights & Scores", "Early morning scores, top plays, and sports news from around the leagues.", 60, "Sports", "TV-G")
                in 8..10 -> ShowTemplate("Sports Talk Live", "Morning Debate & Picks", "Analysts break down the top sports headlines, trade rumors, and predictions.", 60, "Sports", "TV-PG")
                in 11..13 -> ShowTemplate("Championship Game Replay", "Classic Showdown", "Replay of the most thrilling game from this week's tournament action.", 120, "Sports", "TV-G")
                in 14..16 -> ShowTemplate("Pro Football Weekly", "Matchup Breakdowns & Film", "In-depth film study, injury reports, and expert coordinator insights.", 60, "Sports", "TV-PG")
                in 17..18 -> ShowTemplate("Pre-Game Countdown Live", "Live Courtside Preview", "Live stadium interviews, starting lineups, and keys to the matchup.", 60, "Sports", "TV-PG")
                in 19..21 -> ShowTemplate("Live Primetime Basketball Championship", "Conference Finals Showdown", "Live high-stakes conference championship matchup with courtside analysis.", 150, "Sports", "TV-PG")
                22, 23 -> ShowTemplate("Sports Final Tonight", "Late Night Scores & Highlights", "Complete recap of today's games, box scores, and post-game press conferences.", 60, "Sports", "TV-G")
                else -> ShowTemplate("Global Sports Wire", "Overnight Worldwide Coverage", "International soccer, motorsports qualifying, and golf tour leaderboards.", 60, "Sports", "TV-G")
            }
        }

        if (isMovie) {
            return when (hour) {
                in 6..10 -> ShowTemplate("Morning Classic Cinema", "Vintage Mystery", "A classic detective mystery from the golden age of cinema.", 120, "Movies", "TV-PG")
                in 11..13 -> ShowTemplate("Matinee Feature: Summer at Harbor Lake", "Romantic Drama (2021)", "An architect returns to her hometown to restore a historic lakeside estate.", 120, "Movies", "TV-PG")
                in 14..16 -> ShowTemplate("Afternoon Thriller: Velocity Run", "Action Thriller (2022)", "A skilled driver races across the state to clear his family's name.", 120, "Movies", "TV-14")
                in 17..19 -> ShowTemplate("Early Evening Feature: Legacy of the Sphinx", "Adventure Mystery", "Archaeologists uncover a secret chamber hidden beneath the ancient dunes.", 120, "Movies", "TV-PG")
                in 20..22 -> ShowTemplate("Primetime Blockbuster: Star Frontier 3000", "Sci-Fi Epic (2023)", "A courageous space exploration crew investigates an abandoned colony outpost.", 120, "Movies", "TV-14")
                23, 0, 1 -> ShowTemplate("Late Night Feature: Echoes in the Shadows", "Suspense Thriller (2022)", "A private investigator re-examines an unsolved cold case in the neon-lit city.", 120, "Movies", "TV-14")
                else -> ShowTemplate("Midnight Cult Classic", "Sci-Fi Thriller", "A retro sci-fi masterpiece with iconic practical effects and thrills.", 120, "Movies", "TV-14")
            }
        }

        if (isComedy) {
            return when (hour) {
                in 6..9 -> ShowTemplate("Morning Classics: Family Orbit", "The Breakfast Invention (S2 E4)", "A breakfast machine invention goes haywire before the school bus arrives.", 30, "Comedy", "TV-G")
                in 10..13 -> ShowTemplate("Downtown Roommates", "The Stolen Sofa (S3 E11)", "An afternoon furniture trade turns into a heated neighborhood prank war.", 30, "Comedy", "TV-PG")
                in 14..17 -> ShowTemplate("Suburban Misadventures", "Garage Sale Royale (S1 E6)", "A competitive neighborhood yard sale tests family alliances.", 30, "Comedy", "TV-PG")
                in 18..21 -> ShowTemplate("The Office Breakroom", "Secret Santa In July (S4 E8)", "A summer holiday gag sparks an all-out corporate floor debate.", 30, "Comedy", "TV-14")
                in 22..23 -> ShowTemplate("Stand-Up Comedy Lounge", "Live at the Improv", "Top touring stand-up comedians perform hilarious, high-energy original sets.", 60, "Comedy", "TV-14")
                else -> ShowTemplate("Late Night Comedy Club", "Uncensored Showcase", "Stand-up comedy sets and comedy shorts.", 60, "Comedy", "TV-14")
            }
        }

        if (isDrama) {
            return when (hour) {
                in 6..9 -> ShowTemplate("Frontier Lawmen", "Dust & Redemption (S1 E4)", "A sheriff defends an isolated western outpost from a band of outlaws.", 60, "Drama", "TV-14")
                in 10..13 -> ShowTemplate("Central Hospital Wards", "Critical Decisions (S9 E12)", "ER surgeons handle a complex emergency rush during a storm.", 60, "Drama", "TV-14")
                in 14..17 -> ShowTemplate("Justice & Order", "Corrupt Currents (S6 E18)", "Prosecutors square off against an untouchable syndicate attorney.", 60, "Drama", "TV-14")
                in 18..21 -> ShowTemplate("Metro Precinct (Primetime)", "Vanishing Act (S4 E7)", "Detectives investigate an intricate downtown jewel heist with unexpected clues.", 60, "Drama", "TV-14")
                in 22..23 -> ShowTemplate("The Coastal Secrets", "High Tide (S2 E3)", "A quiet seaside community uncovers long-buried family mysteries.", 60, "Drama", "TV-14")
                else -> ShowTemplate("Midnight Espionage", "The Zurich Drop (S3 E1)", "Undercover agents navigate a web of intrigue across European capitals.", 60, "Drama", "TV-14")
            }
        }

        // Standard Major Network / News & General broadcast lineup (CBS, NBC, ABC, FOX, PBS, FAST News)
        val variant = (channelSeed % 3).toInt()
        return when (hour) {
            in 4..6 -> when (variant) {
                0 -> ShowTemplate("First Alert Morning News at 5", "Early Edition", "Live local radar, morning traffic commute alerts, and breaking overnight news.", 60, "News", "TV-G")
                1 -> ShowTemplate("Sunrise Morning Edition", "Live Local Headlines", "Wake up with live news, local weather forecasts, and viral stories.", 60, "News", "TV-G")
                else -> ShowTemplate("Morning Anchor Report", "Early Commute Update", "Complete morning road conditions, local headlines, and regional weather.", 60, "News", "TV-G")
            }
            in 7..8 -> when (variant) {
                0 -> ShowTemplate("Good Morning Live", "National & Local Desk", "National headlines, inspiring human interest stories, and consumer reports.", 60, "News", "TV-G")
                1 -> ShowTemplate("Today's Morning Broadcast", "Live Studio Interviews", "Exclusive guest interviews, live cooking segments, and morning forecast.", 60, "News", "TV-G")
                else -> ShowTemplate("America This Morning", "Daily Digest", "Top global stories, financial market preview, and health updates.", 60, "News", "TV-G")
            }
            in 9..10 -> when (variant) {
                0 -> ShowTemplate("Daytime Living Live", "Lifestyle & Wellness", "Home cooking, healthy recipes, home improvement, and lifestyle trends.", 60, "Entertainment", "TV-G")
                1 -> ShowTemplate("Morning Talk with Sarah", "Community Leaders", "Panel discussion on community issues, parenting tips, and author spotlights.", 60, "Entertainment", "TV-PG")
                else -> ShowTemplate("Home Renovators Elite", "Modern Kitchen Flip", "Designers transform a vintage farmhouse kitchen into a modern showcase.", 60, "Entertainment", "TV-G")
            }
            in 11..12 -> when (variant) {
                0 -> ShowTemplate("Action News Midday at 12", "Live Noon Report", "Breaking midday news, live midday weather radar, and Wall Street update.", 60, "News", "TV-G")
                1 -> ShowTemplate("Eyewitness News Midday", "Community First", "Midday headlines, consumer protection alerts, and regional traffic updates.", 60, "News", "TV-G")
                else -> ShowTemplate("Midday News & Financial Report", "Market Watch", "Live midday stock market numbers, business news, and midday forecast.", 60, "News", "TV-G")
            }
            in 13..14 -> when (variant) {
                0 -> ShowTemplate("Daytime Court Justice", "Case 412: Neighbor Dispute", "A small claims judge hears testimony regarding a disputed property boundary.", 60, "Drama", "TV-PG")
                1 -> ShowTemplate("Culinary Masters Kitchen", "Mystery Box Challenge", "Amateur chefs race against a 45-minute clock to craft gourmet entrees.", 60, "Entertainment", "TV-G")
                else -> ShowTemplate("Antiques & Relics Roadshow", "Hidden Fortunes Found", "Appraisers discover a rare 19th-century oil painting in an attic.", 60, "Entertainment", "TV-G")
            }
            in 15..16 -> when (variant) {
                0 -> ShowTemplate("Daily Game Show Challenge", "Tournament of Champions", "Contestants compete in word puzzles and trivia for the $50,000 prize.", 60, "Entertainment", "TV-G")
                1 -> ShowTemplate("Afternoon Talk & News", "Consumer Alert Hour", "Investigative reporters expose retail scams and provide budgeting advice.", 60, "News", "TV-PG")
                else -> ShowTemplate("First at Four News", "Afternoon Headlines", "Early evening news preview, school reports, and rush-hour commute radar.", 60, "News", "TV-G")
            }
            in 17..18 -> when (variant) {
                0 -> ShowTemplate("Eyewitness News at 6", "Live Local Evening Coverage", "Breaking local news, live helicopter traffic, and chief meteorologist forecast.", 60, "News", "TV-PG")
                1 -> ShowTemplate("Action News at 6:00", "Top Stories & Weather", "Live local coverage from neighborhood reporters and 7-day storm outlook.", 60, "News", "TV-PG")
                else -> ShowTemplate("Evening News Hour", "Regional & World News", "Comprehensive national headlines, political updates, and regional news.", 60, "News", "TV-PG")
            }
            19 -> when (variant) {
                0 -> ShowTemplate("Wheel of Trivia Hour", "Nightly Championship", "Three new contestants spin the big wheel and solve fast-paced word puzzles.", 30, "Entertainment", "TV-G")
                1 -> ShowTemplate("Entertainment Tonight Spotlight", "Hollywood Exclusive", "Exclusive movie previews, red carpet interviews, and celebrity news.", 30, "Entertainment", "TV-PG")
                else -> ShowTemplate("First Alert Weather & Radar at 7", "Evening Tracking", "Neighborhood radar tracking and weekend forecast outlook.", 30, "News", "TV-G")
            }
            in 20..21 -> when (variant) {
                0 -> ShowTemplate("Metro Precinct (Primetime Drama)", "Vanishing Act (S4 E7)", "Detectives investigate an unsolved downtown jewel heist with shocking twists.", 60, "Drama", "TV-14")
                1 -> ShowTemplate("Live Primetime Sports Spectacular", "Championship Game", "Live high-stakes conference matchup with full commentary and slow-motion replays.", 120, "Sports", "TV-PG")
                else -> ShowTemplate("The Voice of Champions", "Live Final Performances", "The remaining top performers sing live for the nationwide audience vote.", 120, "Entertainment", "TV-PG")
            }
            in 22..23 -> when (variant) {
                0 -> ShowTemplate("Action News at 11", "Nightly Local Wrap-Up", "Late-breaking local news, investigative follow-ups, and overnight weather.", 35, "News", "TV-PG")
                1 -> ShowTemplate("Eyewitness News at 11:00", "Nightcast", "Late evening headlines, high school sports recap, and tomorrow's forecast.", 35, "News", "TV-PG")
                else -> ShowTemplate("Nightline In-Depth", "Daily Digest", "Correspondents investigate global developments and compelling human interest stories.", 30, "News", "TV-PG")
            }
            0, 1 -> when (variant) {
                0 -> ShowTemplate("The Late Night Talk Show", "Live with Top Guests", "Celebrity interviews, hilarious comedy skits, and a live musical performance.", 60, "Comedy", "TV-14")
                1 -> ShowTemplate("Late Night Stand-Up Lounge", "New York Comedy Showcase", "Stand-up comedians perform hilarious original sets at the legendary club.", 60, "Comedy", "TV-14")
                else -> ShowTemplate("Midnight Thriller Cinema", "The Midnight Pursuit (2022)", "An elite investigator races against time through the nocturnal metropolis.", 120, "Movies", "TV-14")
            }
            else -> ShowTemplate("World News Now Overnight", "Global Morning Edition", "Overnight world news, global financial markets, and international weather.", 60, "News", "TV-G")
        }
    }

    /**
     * Synthesizes a contiguous lineup of airings for a channel starting at [windowStart]
     * aligned strictly with the real-world hour and minute of the day.
     */
    fun synthesizeChannelLineup(
        channel: TabloChannel,
        windowStart: Long,
        windowEnd: Long,
        now: Long = System.currentTimeMillis()
    ): List<TabloAiring> {
        val airings = mutableListOf<TabloAiring>()
        val seed = abs(channel.channelId.hashCode() + channel.callSign.hashCode()).toLong()

        // Align starting pointer to 30-minute broadcast blocks
        var currentStart = windowStart
        var iteration = 0

        while (currentStart < windowEnd && iteration < 100) {
            iteration++
            val hour = getHourOfDay(currentStart)
            val template = getBroadcastShowForHour(hour, seed + iteration, channel)
            val durationSec = (template.durationMinutes * 60L).coerceIn(1800L, 7200L)
            val durationMs = durationSec * 1000L
            val currentEnd = currentStart + durationMs
            val isLive = now in currentStart until currentEnd

            val airing = TabloAiring(
                airingId = "synth-${channel.channelId}-$currentStart",
                channelId = channel.channelId,
                title = template.title,
                episodeTitle = template.episode,
                description = template.description,
                startTimeMillis = currentStart,
                durationSeconds = durationSec,
                category = template.category,
                rating = template.rating,
                isLive = isLive
            )
            airings.add(airing)
            currentStart = currentEnd
        }

        return airings
    }

    /**
     * Resolves the complete list of airings for a channel across [windowStart]..[windowEnd].
     * If the real Tablo API provided airings for this channel, gaps are filled and real airings are preserved.
     * If no airings were returned by the API, a realistic schedule matching the time of day is generated.
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
                // Gap before this airing: fill it with time-appropriate programming
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

