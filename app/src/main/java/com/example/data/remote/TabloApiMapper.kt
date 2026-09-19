package com.example.data.remote

import com.example.model.TabloAiring
import com.example.model.TabloChannel

/**
 * Maps the documented Tablo API response shapes onto the application's models.
 */
internal object TabloApiMapper {

    private const val DEFAULT_DURATION_SECONDS = 1800L

    fun channelFromDetail(path: String?, detail: TabloChannelDetailResponse?): TabloChannel? {
        val channel = detail?.channel ?: return null
        val channelPath = if (!path.isNullOrEmpty()) path else detail?.path ?: return null
        val major = channel.major ?: 0
        val minor = channel.minor ?: 0
        val callSign = channel.callSign ?: channel.callSignSrc ?: if (major > 0) "Channel $major" else "FAST Stream"
        val isOtt = channel.source?.equals("ott", ignoreCase = true) == true ||
                channel.source?.equals("fast", ignoreCase = true) == true ||
                major == 0

        val network = channel.network?.ifBlank { null }
            ?: inferNetworkFromCallSign(callSign)

        return TabloChannel(
            channelId = channelPath.substringAfterLast("/"),
            callSign = callSign,
            majorNumber = major,
            minorNumber = minor,
            network = network,
            resolution = channel.resolution ?: if (isOtt) "720p" else "1080i",
            channelPath = channelPath,
            logoUrl = null,
            identifier = channelPath.substringAfterLast("/"),
            isOtt = isOtt
        )
    }

    fun channelFromChannelSchema(detail: TabloAiringDetailResponse?): TabloChannel? {
        val channel = detail?.airingDetails?.channel?.channel ?: return null
        val channelPath = detail.airingDetails?.channelPath ?: detail.airingDetails?.channel?.path ?: return null
        val major = channel.major ?: 0
        val minor = channel.minor ?: 0
        val callSign = channel.callSign ?: channel.callSignSrc ?: if (major > 0) "Channel $major" else "FAST Stream"
        val isOtt = channel.source?.equals("ott", ignoreCase = true) == true ||
                channel.source?.equals("fast", ignoreCase = true) == true ||
                major == 0

        val network = channel.network?.ifBlank { null }
            ?: inferNetworkFromCallSign(callSign)

        return TabloChannel(
            channelId = channelPath.substringAfterLast("/"),
            callSign = callSign,
            majorNumber = major,
            minorNumber = minor,
            network = network,
            resolution = channel.resolution ?: if (isOtt) "720p" else "1080i",
            channelPath = channelPath,
            logoUrl = null,
            identifier = channelPath.substringAfterLast("/"),
            isOtt = isOtt
        )
    }

    fun airingFromDetail(path: String?, detail: TabloAiringDetailResponse?, nowMillis: Long): TabloAiring? {
        val airingPath = if (!path.isNullOrEmpty()) path else detail?.path ?: return null
        val airingDetails = detail?.airingDetails ?: return null
        val channelPath = airingDetails.channelPath ?: airingDetails.channel?.path ?: return null
        val startMillis = TabloTime.parseIso8601(airingDetails.datetime) ?: return null
        val durationSeconds = (airingDetails.duration ?: DEFAULT_DURATION_SECONDS).coerceAtLeast(60L)
        val endMillis = startMillis + (durationSeconds * 1000L)

        val episode = detail.episode
        val event = detail.event
        val movie = detail.movie
        val show = detail.show

        val title = airingDetails.showTitle
            ?: episode?.title
            ?: event?.title
            ?: movie?.title
            ?: show?.title
            ?: "Untitled"
        val episodeTitle = episode?.title?.takeIf { it != title }
        val description = episode?.description
            ?: event?.description
            ?: movie?.plot
            ?: show?.description
        val category = determineCategory(airingPath, detail)
        val rating = movie?.filmRating ?: movie?.qualityRating?.toString() ?: ""

        return TabloAiring(
            airingId = airingPath.substringAfterLast("/"),
            channelId = channelPath.substringAfterLast("/"),
            title = title,
            episodeTitle = episodeTitle,
            description = description,
            startTimeMillis = startMillis,
            durationSeconds = durationSeconds,
            category = category,
            rating = rating,
            isLive = nowMillis in startMillis until endMillis,
            thumbnail = null
        )
    }

    fun airingFromGen4Cloud(channelId: String, cloudAiring: TabloGen4CloudAiring, nowMillis: Long): TabloAiring? {
        val startMillis = cloudAiring.startTimeMillis
            ?: TabloTime.parseIso8601(cloudAiring.datetime)
            ?: TabloTime.parseIso8601(cloudAiring.startTime)
            ?: return null

        val durationSeconds = (cloudAiring.duration ?: DEFAULT_DURATION_SECONDS).coerceAtLeast(60L)
        val endMillis = startMillis + (durationSeconds * 1000L)

        val title = cloudAiring.title
            ?: cloudAiring.showTitle
            ?: cloudAiring.program?.title
            ?: cloudAiring.show?.title
            ?: "Live Broadcast"

        val description = cloudAiring.description
            ?: cloudAiring.plot
            ?: cloudAiring.synopsis
            ?: cloudAiring.program?.description
            ?: cloudAiring.show?.description

        val category = cloudAiring.category
            ?: cloudAiring.genre
            ?: cloudAiring.program?.category
            ?: cloudAiring.show?.category
            ?: "Program"

        val rating = cloudAiring.rating
            ?: cloudAiring.program?.rating
            ?: cloudAiring.show?.rating
            ?: ""

        val airingId = cloudAiring.identifier
            ?: cloudAiring.airingId
            ?: cloudAiring.id
            ?: "$channelId-$startMillis"

        return TabloAiring(
            airingId = airingId,
            channelId = channelId,
            title = title,
            episodeTitle = cloudAiring.episodeTitle,
            description = description,
            startTimeMillis = startMillis,
            durationSeconds = durationSeconds,
            category = category,
            rating = rating,
            isLive = nowMillis in startMillis until endMillis,
            thumbnail = cloudAiring.imageUrl ?: cloudAiring.thumbnail
        )
    }

    private fun determineCategory(path: String, detail: TabloAiringDetailResponse?): String {
        if (detail?.sportPath != null || detail?.event != null || "/sports/" in path) {
            return "Sports"
        }
        if (detail?.movie != null || "/movies/" in path) {
            return "Movies"
        }
        val showGenres = detail?.show?.genres.orEmpty()
        if (detail?.seriesPath != null || detail?.seasonPath != null || detail?.episode != null || "/series/" in path) {
            return when {
                showGenres.any { it.contains("News", ignoreCase = true) } -> "News"
                showGenres.any { it.contains("Comedy", ignoreCase = true) } -> "Comedy"
                showGenres.any { it.contains("Kids", ignoreCase = true) || it.contains("Animation", ignoreCase = true) } -> "Kids"
                else -> "Series"
            }
        }
        for (g in showGenres) {
            when {
                g.contains("Sport", ignoreCase = true) -> return "Sports"
                g.contains("News", ignoreCase = true) || g.contains("Weather", ignoreCase = true) -> return "News"
                g.contains("Movie", ignoreCase = true) || g.contains("Cinema", ignoreCase = true) -> return "Movies"
                g.contains("Comedy", ignoreCase = true) -> return "Comedy"
                g.contains("Drama", ignoreCase = true) -> return "Drama"
                g.contains("Doc", ignoreCase = true) -> return "Documentary"
            }
        }
        val titleUpper = (detail?.airingDetails?.showTitle ?: detail?.show?.title ?: "").uppercase()
        return when {
            titleUpper.contains("NEWS") || titleUpper.contains("WEATHER") || titleUpper.contains("REPORT") -> "News"
            titleUpper.contains("SPORT") || titleUpper.contains("FOOTBALL") || titleUpper.contains("BASKETBALL") || titleUpper.contains("GOLF") -> "Sports"
            else -> "Program"
        }
    }

    private fun inferNetworkFromCallSign(callSign: String): String {
        val upper = callSign.uppercase()
        return when {
            upper.contains("ABC") -> "ABC"
            upper.contains("CBS") -> "CBS"
            upper.contains("NBC") -> "NBC"
            upper.contains("FOX") -> "FOX"
            upper.contains("PBS") -> "PBS"
            upper.contains("CW") -> "CW"
            upper.contains("ION") -> "ION"
            upper.contains("METV") || upper.contains("ME TV") -> "MeTV"
            upper.contains("GRIT") -> "Grit"
            upper.contains("BOUNCE") -> "Bounce"
            upper.contains("COZI") -> "Cozi TV"
            upper.contains("COMET") -> "Comet"
            upper.contains("COURT") -> "Court TV"
            upper.contains("LAFF") -> "Laff"
            upper.contains("DEFY") -> "Defy"
            upper.contains("TELEMUNDO") -> "Telemundo"
            upper.contains("UNIVISION") -> "Univision"
            else -> if (upper.startsWith("K") || upper.startsWith("W")) "OTA" else "FAST"
        }
    }
}