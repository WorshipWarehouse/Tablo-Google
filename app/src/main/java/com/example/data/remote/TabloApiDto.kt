package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TabloServerInfoResponse(
    @Json(name = "server_id") val serverId: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "tuners") val tuners: Int? = null,
    @Json(name = "version") val version: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloChannelDetailResponse(
    @Json(name = "channel") val channel: TabloChannelInner? = null
)

@JsonClass(generateAdapter = true)
data class TabloChannelInner(
    @Json(name = "call_sign") val callSign: String? = null,
    @Json(name = "major") val major: Int? = null,
    @Json(name = "minor") val minor: Int? = null,
    @Json(name = "network") val network: String? = null,
    @Json(name = "resolution") val resolution: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloAiringDetailResponse(
    @Json(name = "airing_details") val airingDetails: TabloAiringDetailsInner? = null,
    @Json(name = "show") val show: TabloShowInner? = null,
    @Json(name = "episode") val episode: TabloEpisodeInner? = null,
    @Json(name = "channel_path") val channelPath: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloAiringDetailsInner(
    @Json(name = "duration") val duration: Long? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "start_time") val startTime: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloShowInner(
    @Json(name = "title") val title: String? = null,
    @Json(name = "cover_image") val coverImage: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloEpisodeInner(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloWatchResponse(
    @Json(name = "playlist_url") val playlistUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloAssocServerResponse(
    @Json(name = "cservers") val cservers: List<TabloCServerItem>? = null
)

@JsonClass(generateAdapter = true)
data class TabloCServerItem(
    @Json(name = "server_id") val serverId: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "private_ip") val privateIp: String? = null
)
