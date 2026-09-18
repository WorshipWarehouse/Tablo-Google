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
        val major = channel.major ?: 1
        val minor = channel.minor ?: 1
        return TabloChannel(
            channelId = channelPath.substringAfterLast("/"),
            callSign = channel.callSign ?: "Channel $major",
            majorNumber = major,
            minorNumber = minor,
            network = channel.network ?: channel.callSign ?: "OTA",
            resolution = channel.resolution ?: "",
            channelPath = channelPath,
            logoUrl = null
        )
    }

    fun channelFromChannelSchema(detail: TabloAiringDetailResponse?): TabloChannel? {
        val channel = detail?.airingDetails?.channel?.channel ?: return null
        val channelPath = detail.airingDetails?.channelPath ?: detail.airingDetails?.channel?.path ?: return null
        val major = channel.major ?: 1
        val minor = channel.minor ?: 1
        return TabloChannel(
            channelId = channelPath.substringAfterLast("/"),
            callSign = channel.callSign ?: "Channel $major",
            majorNumber = major,
            minorNumber = minor,
            network = channel.network ?: channel.callSign ?: "OTA",
            resolution = channel.resolution ?: "",
            channelPath = channelPath,
            logoUrl = null
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
        val category = categoryForPath(airingPath)
        val rating = movie?.filmRating ?: ""

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

    private fun categoryForPath(path: String): String {
        return when {
            "/movies/" in path -> "Movies"
            "/sports/" in path -> "Sports"
            "/series/" in path -> "Series"
            else -> "Program"
        }
    }
}