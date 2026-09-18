package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TabloServerInfoResponse(
    @Json(name = "server_id") val serverId: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "local_address") val localAddress: String? = null,
    @Json(name = "setup_completed") val setupCompleted: Boolean? = null,
    @Json(name = "model") val model: TabloModelInfo? = null,
    @Json(name = "availability") val availability: String? = null,
    @Json(name = "product") val product: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloModelInfo(
    @Json(name = "wifi") val wifi: Boolean? = null,
    @Json(name = "tuners") val tuners: Int? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloTunerResponse(
    @Json(name = "in_use") val inUse: Boolean? = null,
    @Json(name = "channel") val channel: String? = null,
    @Json(name = "recording") val recording: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloChannelDetailResponse(
    @Json(name = "object_id") val objectId: Long? = null,
    @Json(name = "path") val path: String? = null,
    @Json(name = "channel") val channel: TabloChannelInner? = null
)

@JsonClass(generateAdapter = true)
data class TabloChannelInner(
    @Json(name = "call_sign") val callSign: String? = null,
    @Json(name = "call_sign_src") val callSignSrc: String? = null,
    @Json(name = "major") val major: Int? = null,
    @Json(name = "minor") val minor: Int? = null,
    @Json(name = "network") val network: String? = null,
    @Json(name = "resolution") val resolution: String? = null,
    @Json(name = "favourite") val favourite: Boolean? = null,
    @Json(name = "source") val source: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloAiringDetailResponse(
    @Json(name = "path") val path: String? = null,
    @Json(name = "object_id") val objectId: Long? = null,
    @Json(name = "series_path") val seriesPath: String? = null,
    @Json(name = "season_path") val seasonPath: String? = null,
    @Json(name = "sport_path") val sportPath: String? = null,
    @Json(name = "program_path") val programPath: String? = null,
    @Json(name = "episode") val episode: TabloEpisodeInner? = null,
    @Json(name = "event") val event: TabloEventInner? = null,
    @Json(name = "movie") val movie: TabloMovieInner? = null,
    @Json(name = "show") val show: TabloShowInner? = null,
    @Json(name = "airing_details") val airingDetails: TabloAiringDetailsInner? = null,
    @Json(name = "qualifiers") val qualifiers: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class TabloAiringDetailsInner(
    @Json(name = "datetime") val datetime: String? = null,
    @Json(name = "duration") val duration: Long? = null,
    @Json(name = "channel_path") val channelPath: String? = null,
    @Json(name = "channel") val channel: TabloChannelDetailResponse? = null,
    @Json(name = "show_title") val showTitle: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloShowInner(
    @Json(name = "title") val title: String? = null,
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "thumbnail_image") val thumbnailImage: TabloImageRef? = null,
    @Json(name = "cover_image") val coverImage: TabloImageRef? = null
)

@JsonClass(generateAdapter = true)
data class TabloImageRef(
    @Json(name = "image_id") val imageId: Long? = null,
    @Json(name = "has_title") val hasTitle: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class TabloEpisodeInner(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "number") val number: Int? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "orig_air_date") val origAirDate: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloEventInner(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "season") val season: String? = null,
    @Json(name = "venue") val venue: String? = null,
    @Json(name = "teams") val teams: List<TabloTeamInner>? = null
)

@JsonClass(generateAdapter = true)
data class TabloTeamInner(
    @Json(name = "name") val name: String? = null,
    @Json(name = "team_id") val teamId: Long? = null
)

@JsonClass(generateAdapter = true)
data class TabloMovieInner(
    @Json(name = "title") val title: String? = null,
    @Json(name = "plot") val plot: String? = null,
    @Json(name = "release_year") val releaseYear: Int? = null,
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "quality_rating") val qualityRating: Int? = null,
    @Json(name = "film_rating") val filmRating: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloWatchResponse(
    @Json(name = "token") val token: String? = null,
    @Json(name = "expires") val expires: String? = null,
    @Json(name = "keepalive") val keepalive: Long? = null,
    @Json(name = "playlist_url") val playlistUrl: String? = null,
    @Json(name = "bif_url_sd") val bifUrlSd: String? = null,
    @Json(name = "bif_url_hd") val bifUrlHd: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloAssocInfoResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "cpes") val cpes: List<TabloCPEItem>? = null
)

@JsonClass(generateAdapter = true)
data class TabloCPEItem(
    @Json(name = "serverid") val serverId: String? = null,
    @Json(name = "host") val host: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "board") val board: String? = null,
    @Json(name = "server_version") val serverVersion: String? = null,
    @Json(name = "public_ip") val publicIp: String? = null,
    @Json(name = "private_ip") val privateIp: String? = null,
    @Json(name = "http") val http: Int? = null,
    @Json(name = "ssl") val ssl: Int? = null,
    @Json(name = "last_seen") val lastSeen: String? = null
)

@JsonClass(generateAdapter = true)
data class TabloGuideStatusResponse(
    @Json(name = "guide_seeded") val guideSeeded: Boolean? = null,
    @Json(name = "last_update") val lastUpdate: String? = null,
    @Json(name = "limit") val limit: String? = null
)