package com.example.model

enum class MultiviewLayoutType(val displayName: String, val maxChannels: Int) {
    GRID_2X2("2x2 Grid", 4),
    PRIMARY_1_PLUS_3("1+3 Primary", 4),
    HORIZONTAL_2_UP("2-Up Split", 2),
    SOLO("Solo / Fullscreen", 1)
}

data class TabloDevice(
    val serverId: String,
    val name: String,
    val model: String,
    val host: String,
    val port: Int = 8885,
    val streamingPort: Int = 80,
    val tunerCount: Int = 4,
    val activeTuners: Int = 0,
    val isConnected: Boolean = false,
    val firmware: String = "",
    val lighthouseToken: String? = null,
    val accountToken: String? = null,
    val clientId: String = "",
    val isGen4: Boolean = false
) {
    val localBaseUrl: String
        get() = "http://$host:$port"

    val streamingBaseUrl: String
        get() = "http://$host:$streamingPort"
}

data class TabloChannel(
    val channelId: String,
    val callSign: String,
    val majorNumber: Int,
    val minorNumber: Int,
    val network: String,
    val resolution: String = "",
    val channelPath: String = "/guide/channels/$channelId",
    val logoUrl: String? = null,
    val streamUrl: String = "",
    val identifier: String? = null,
    val isOtt: Boolean = false
) {
    val displayChannel: String
        get() = if (majorNumber == 0 && minorNumber == 0) callSign else "$majorNumber.$minorNumber"

    val displayName: String
        get() = if (majorNumber == 0 && minorNumber == 0) callSign else "$majorNumber.$minorNumber $callSign"
}

data class TabloAiring(
    val airingId: String,
    val channelId: String,
    val title: String,
    val episodeTitle: String? = null,
    val description: String? = null,
    val startTimeMillis: Long,
    val durationSeconds: Long = 1800L,
    val category: String = "Program",
    val rating: String = "",
    val isLive: Boolean = false,
    val thumbnail: String? = null
) {
    val endTimeMillis: Long
        get() = startTimeMillis + (durationSeconds * 1000L)
}

data class MultiviewTileState(
    val tileId: Int,
    val channel: TabloChannel?,
    val currentAiring: TabloAiring? = null,
    val isAudioFocused: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null
)

data class SavedMultiviewItem(
    val id: Long = 0,
    val name: String,
    val layoutType: MultiviewLayoutType,
    val channels: List<TabloChannel>,
    val preferredAudioChannelId: String,
    val createdAt: Long = System.currentTimeMillis()
)

sealed class TabloResult<out T> {
    data class Success<T>(val data: T) : TabloResult<T>()
    data class Error(val message: String) : TabloResult<Nothing>()
}

object GuideTiming {
    const val SLOT_MINUTES = 30L
    const val SLOT_COUNT = 12
    const val WINDOW_MINUTES = SLOT_MINUTES * SLOT_COUNT

    fun windowStartMs(now: Long = System.currentTimeMillis()): Long {
        val slotMs = SLOT_MINUTES * 60_000L
        return (now / slotMs) * slotMs
    }

    fun windowEndMs(now: Long = System.currentTimeMillis()): Long =
        windowStartMs(now) + (WINDOW_MINUTES * 60_000L)

    fun slotTimeMs(windowStart: Long, slotIndex: Int): Long =
        windowStart + (slotIndex * SLOT_MINUTES * 60_000L)
}