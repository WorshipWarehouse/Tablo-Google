package com.example.ui.guide

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GuideTiming
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.example.ui.theme.*
import com.example.ui.util.safeRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val CHANNEL_COLUMN_WIDTH = 190f
private const val SLOT_WIDTH = 180f
private const val SLOT_MINUTES = GuideTiming.SLOT_MINUTES
private const val PX_PER_MINUTE = SLOT_WIDTH / SLOT_MINUTES.toFloat()
private const val ROW_HEIGHT = 70f

enum class GuideViewFormat(val label: String) {
    GRID("Timeline Grid"),
    SCHEDULE("Upcoming Schedule"),
    LIST("Detailed Cards"),
    COMPACT("Channel List")
}

enum class ChannelFilterCategory(val label: String) {
    ALL("All Channels"),
    FAVORITES("Favorites"),
    OTA("OTA Antenna"),
    FAST("FAST Streaming"),
    SPORTS("Sports"),
    MOVIES("Movies"),
    NEWS("News"),
    SERIES("Series")
}

fun getGenreColor(category: String): Color {
    val cat = category.uppercase(Locale.getDefault())
    return when {
        cat.contains("NEWS") || cat.contains("WEATHER") -> GenreNews
        cat.contains("SPORT") || cat.contains("FOOTBALL") || cat.contains("BASKETBALL") -> GenreSports
        cat.contains("MOVIE") || cat.contains("CINEMA") || cat.contains("FILM") -> GenreMovies
        cat.contains("DRAMA") || cat.contains("CRIME") -> GenreDrama
        cat.contains("COMEDY") || cat.contains("SITCOM") -> GenreComedy
        cat.contains("KID") || cat.contains("ANIMATION") || cat.contains("FAMILY") -> GenreKids
        cat.contains("DOC") || cat.contains("NATURE") || cat.contains("SCIENCE") -> GenreDoc
        else -> GenreDefault
    }
}

/**
 * Tablo4U Clean, High-Performance TV Guide.
 * - Removed obsolete remnant inspector banner above channel grid.
 * - Removed redundant Live button, NOW button, 4-tuner button, and manual refresh button.
 * - Automated 60-second background soft refresh.
 * - Seamless D-pad navigation between Top Nav, Category chips, and Channel grid.
 * - Pre-computed memoized airings to ensure fluid 60fps scrolling on TV hardware.
 * - Guide Time Indicator with real-time red vertical line and header marker.
 */
@Composable
fun GuideScreen(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    onWatchChannel: (TabloChannel) -> Unit,
    onAssignToTile: (TabloChannel, Int) -> Unit,
    onBack: () -> Unit,
    tabloDevice: TabloDevice? = null,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    onRequestTopNav: () -> Unit = {},
    onNavigateLeftPage: () -> Unit = {},
    onNavigateRightPage: () -> Unit = {},
    focusedEpgTimeMs: Long = System.currentTimeMillis(),
    onUpdateFocusedEpgTime: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val windowStart = remember { GuideTiming.windowStartMs() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // Soft-refresh the guide time & airings every 60 seconds automatically
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
            onRefresh()
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    var selectedFormat by remember { mutableStateOf(GuideViewFormat.GRID) }
    var selectedCategory by remember { mutableStateOf(ChannelFilterCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var favoriteChannelIds by remember { mutableStateOf(setOf<String>()) }
    var scheduledRecordingIds by remember { mutableStateOf(setOf<String>()) }
    var programDetailsDialog by remember { mutableStateOf<Pair<TabloChannel, TabloAiring>?>(null) }

    val filterFocusRequesters = remember {
        ChannelFilterCategory.values().associateWith { FocusRequester() }
    }
    val firstContentFocusRequester = remember { FocusRequester() }
    val gridScrollState = rememberScrollState()

    // Pre-calculate and memoize real EPG airings across all channels for the 24-hour window
    val windowEnd = remember(now) { GuideTiming.windowEndMs(now) }
    val allChannelAiringsMap = remember(channels, airings, windowStart, windowEnd, now) {
        channels.associate { channel ->
            channel.channelId to TabloEpgResolver.resolveAiringsForChannel(
                channel = channel,
                realAirings = airings,
                windowStart = windowStart,
                windowEnd = windowEnd,
                now = now
            )
        }
    }

    // Filter channels based on search query, favorite tags, and category chips (searching channels AND future programs)
    val filteredChannels = remember(channels, allChannelAiringsMap, selectedCategory, searchQuery, favoriteChannelIds) {
        channels.filter { ch ->
            val chAirings = allChannelAiringsMap[ch.channelId].orEmpty()
            val matchesSearch = searchQuery.isBlank() ||
                    ch.callSign.contains(searchQuery, ignoreCase = true) ||
                    ch.network.contains(searchQuery, ignoreCase = true) ||
                    ch.displayChannel.contains(searchQuery, ignoreCase = true) ||
                    chAirings.any { airing ->
                        airing.title.contains(searchQuery, ignoreCase = true) ||
                        airing.episodeTitle?.contains(searchQuery, ignoreCase = true) == true ||
                        airing.description?.contains(searchQuery, ignoreCase = true) == true ||
                        airing.category.contains(searchQuery, ignoreCase = true)
                    }

            if (!matchesSearch) return@filter false

            when (selectedCategory) {
                ChannelFilterCategory.ALL -> true
                ChannelFilterCategory.FAVORITES -> favoriteChannelIds.contains(ch.channelId)
                ChannelFilterCategory.OTA -> !ch.isOtt
                ChannelFilterCategory.FAST -> ch.isOtt
                ChannelFilterCategory.SPORTS -> {
                    ch.callSign.contains("SPORT", ignoreCase = true) ||
                    ch.network.contains("SPORT", ignoreCase = true) ||
                    ch.network.contains("STADIUM", ignoreCase = true) ||
                    ch.network.contains("GOLF", ignoreCase = true) ||
                    ch.network.contains("ESPN", ignoreCase = true) ||
                    chAirings.any {
                        it.category.equals("Sports", ignoreCase = true) ||
                        it.title.contains("Sport", ignoreCase = true) ||
                        it.title.contains("Football", ignoreCase = true) ||
                        it.title.contains("Basketball", ignoreCase = true) ||
                        it.title.contains("Baseball", ignoreCase = true) ||
                        it.title.contains("Soccer", ignoreCase = true) ||
                        it.title.contains("PGA", ignoreCase = true) ||
                        it.title.contains("Championship", ignoreCase = true)
                    }
                }
                ChannelFilterCategory.NEWS -> {
                    ch.callSign.contains("NEWS", ignoreCase = true) ||
                    ch.network.contains("NEWS", ignoreCase = true) ||
                    ch.network.contains("WEATHER", ignoreCase = true) ||
                    chAirings.any {
                        it.category.equals("News", ignoreCase = true) ||
                        it.title.contains("News", ignoreCase = true) ||
                        it.title.contains("Weather", ignoreCase = true)
                    }
                }
                ChannelFilterCategory.MOVIES -> {
                    ch.callSign.contains("MOVIE", ignoreCase = true) ||
                    ch.network.contains("CINEMA", ignoreCase = true) ||
                    ch.network.contains("FILM", ignoreCase = true) ||
                    chAirings.any {
                        it.category.equals("Movies", ignoreCase = true) ||
                        it.title.contains("Movie", ignoreCase = true) ||
                        it.title.contains("Film", ignoreCase = true)
                    }
                }
                ChannelFilterCategory.SERIES -> {
                    ch.callSign.contains("DRAMA", ignoreCase = true) ||
                    ch.callSign.contains("COMEDY", ignoreCase = true) ||
                    chAirings.any {
                        it.category.equals("Drama", ignoreCase = true) ||
                        it.category.equals("Comedy", ignoreCase = true) ||
                        it.category.equals("Series", ignoreCase = true) ||
                        it.category.equals("Entertainment", ignoreCase = true)
                    }
                }
            }
        }
    }

    val channelAiringsMap = remember(filteredChannels, allChannelAiringsMap) {
        filteredChannels.associate { it.channelId to (allChannelAiringsMap[it.channelId] ?: emptyList()) }
    }

    // Chronologically sorted list of all upcoming airings for Future Schedule search/topic browsing
    val upcomingScheduleItems = remember(filteredChannels, allChannelAiringsMap, searchQuery, selectedCategory) {
        val items = mutableListOf<Pair<TabloChannel, TabloAiring>>()
        for (channel in filteredChannels) {
            val channelAirings = allChannelAiringsMap[channel.channelId].orEmpty()
            for (airing in channelAirings) {
                val matchesSearch = searchQuery.isBlank() ||
                        channel.callSign.contains(searchQuery, ignoreCase = true) ||
                        channel.network.contains(searchQuery, ignoreCase = true) ||
                        channel.displayChannel.contains(searchQuery, ignoreCase = true) ||
                        airing.title.contains(searchQuery, ignoreCase = true) ||
                        airing.episodeTitle?.contains(searchQuery, ignoreCase = true) == true ||
                        airing.description?.contains(searchQuery, ignoreCase = true) == true ||
                        airing.category.contains(searchQuery, ignoreCase = true)

                val matchesCategory = when (selectedCategory) {
                    ChannelFilterCategory.ALL, ChannelFilterCategory.FAVORITES, ChannelFilterCategory.OTA, ChannelFilterCategory.FAST -> true
                    ChannelFilterCategory.SPORTS -> {
                        airing.category.equals("Sports", ignoreCase = true) ||
                        airing.title.contains("Sport", ignoreCase = true) ||
                        airing.title.contains("Football", ignoreCase = true) ||
                        airing.title.contains("Basketball", ignoreCase = true) ||
                        airing.title.contains("Baseball", ignoreCase = true) ||
                        airing.title.contains("Soccer", ignoreCase = true) ||
                        channel.callSign.contains("SPORT", ignoreCase = true)
                    }
                    ChannelFilterCategory.NEWS -> {
                        airing.category.equals("News", ignoreCase = true) ||
                        airing.title.contains("News", ignoreCase = true) ||
                        airing.title.contains("Weather", ignoreCase = true) ||
                        channel.callSign.contains("NEWS", ignoreCase = true)
                    }
                    ChannelFilterCategory.MOVIES -> {
                        airing.category.equals("Movies", ignoreCase = true) ||
                        airing.title.contains("Movie", ignoreCase = true) ||
                        channel.callSign.contains("MOVIE", ignoreCase = true)
                    }
                    ChannelFilterCategory.SERIES -> {
                        airing.category.equals("Drama", ignoreCase = true) ||
                        airing.category.equals("Comedy", ignoreCase = true) ||
                        airing.category.equals("Series", ignoreCase = true)
                    }
                }

                if (matchesSearch && matchesCategory) {
                    items.add(Pair(channel, airing))
                }
            }
        }
        items.sortedWith(compareBy<Pair<TabloChannel, TabloAiring>> { it.second.startTimeMillis }.thenBy { it.first.majorNumber })
    }

    // Auto-focus category bar when navigating down from top quick bar
    LaunchedEffect(focusRequester) {
        if (focusRequester != null) {
            filterFocusRequesters[selectedCategory]?.safeRequest()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Clean Header: Title + Search field + View Format toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TV GUIDE",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "(${filteredChannels.size} Channels)",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Controls: Search + Format Toggle + Settings Gear
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Tablo4USearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onRequestTopNav = onRequestTopNav
                    )

                    Tablo4UFormatButton(
                        viewFormat = selectedFormat,
                        onClick = {
                            selectedFormat = when (selectedFormat) {
                                GuideViewFormat.GRID -> GuideViewFormat.SCHEDULE
                                GuideViewFormat.SCHEDULE -> GuideViewFormat.LIST
                                GuideViewFormat.LIST -> GuideViewFormat.COMPACT
                                GuideViewFormat.COMPACT -> GuideViewFormat.GRID
                            }
                        },
                        onRequestTopNav = onRequestTopNav
                    )

                    Tablo4USettingsButton(
                        onClick = onOpenSettings,
                        onRequestTopNav = onRequestTopNav
                    )
                }
            }

            // Category Filter Chips
            Tablo4UCategoryFilterBar(
                selectedCategory = selectedCategory,
                categories = ChannelFilterCategory.values().toList(),
                onSelectCategory = { selectedCategory = it },
                filterFocusRequesters = filterFocusRequesters,
                onRequestTopNav = onRequestTopNav,
                onNavigateDownToContent = {
                    firstContentFocusRequester.safeRequest()
                }
            )

            // Main Channel Guide Area (Starts directly below category bar with zero clutter)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading && channels.isEmpty()) {
                    GuideLoadingState()
                } else if (filteredChannels.isEmpty()) {
                    Tablo4UEmptyGuideState(
                        searchQuery = searchQuery,
                        onClearSearch = { searchQuery = "" },
                        onRetry = onRefresh,
                        onRequestTopNav = onRequestTopNav
                    )
                } else {
                    when (selectedFormat) {
                        GuideViewFormat.GRID -> {
                            Tablo4UTimelineGrid(
                                channels = filteredChannels,
                                channelAiringsMap = channelAiringsMap,
                                windowStart = windowStart,
                                now = now,
                                timeFormat = timeFormat,
                                scrollState = gridScrollState,
                                favoriteChannelIds = favoriteChannelIds,
                                scheduledRecordingIds = scheduledRecordingIds,
                                firstContentFocusRequester = firstContentFocusRequester,
                                focusedEpgTimeMs = focusedEpgTimeMs,
                                onUpdateFocusedEpgTime = onUpdateFocusedEpgTime,
                                onWatchChannel = onWatchChannel,
                                onShowDetails = { ch, airing ->
                                    programDetailsDialog = Pair(ch, airing)
                                },
                                onToggleFavorite = { ch ->
                                    favoriteChannelIds = if (favoriteChannelIds.contains(ch.channelId)) {
                                        favoriteChannelIds - ch.channelId
                                    } else {
                                        favoriteChannelIds + ch.channelId
                                    }
                                },
                                onRequestCategoryNav = {
                                    filterFocusRequesters[selectedCategory]?.safeRequest()
                                },
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        GuideViewFormat.SCHEDULE -> {
                            Tablo4UFutureScheduleView(
                                scheduleItems = upcomingScheduleItems,
                                now = now,
                                timeFormat = timeFormat,
                                favoriteChannelIds = favoriteChannelIds,
                                scheduledRecordingIds = scheduledRecordingIds,
                                firstContentFocusRequester = firstContentFocusRequester,
                                onWatchChannel = onWatchChannel,
                                onShowDetails = { ch, airing ->
                                    programDetailsDialog = Pair(ch, airing)
                                },
                                onToggleRecord = { airing ->
                                    scheduledRecordingIds = if (scheduledRecordingIds.contains(airing.airingId)) {
                                        scheduledRecordingIds - airing.airingId
                                    } else {
                                        scheduledRecordingIds + airing.airingId
                                    }
                                },
                                onRequestCategoryNav = {
                                    filterFocusRequesters[selectedCategory]?.safeRequest()
                                },
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        GuideViewFormat.LIST -> {
                            Tablo4UCardListView(
                                channels = filteredChannels,
                                channelAiringsMap = channelAiringsMap,
                                now = now,
                                timeFormat = timeFormat,
                                favoriteChannelIds = favoriteChannelIds,
                                firstItemFocusRequester = firstContentFocusRequester,
                                onWatchChannel = onWatchChannel,
                                onShowDetails = { ch, airing ->
                                    programDetailsDialog = Pair(ch, airing)
                                },
                                onToggleFavorite = { ch ->
                                    favoriteChannelIds = if (favoriteChannelIds.contains(ch.channelId)) {
                                        favoriteChannelIds - ch.channelId
                                    } else {
                                        favoriteChannelIds + ch.channelId
                                    }
                                },
                                onRequestCategoryNav = {
                                    filterFocusRequesters[selectedCategory]?.safeRequest()
                                },
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        GuideViewFormat.COMPACT -> {
                            Tablo4UCompactListView(
                                channels = filteredChannels,
                                channelAiringsMap = channelAiringsMap,
                                favoriteChannelIds = favoriteChannelIds,
                                firstItemFocusRequester = firstContentFocusRequester,
                                onWatchChannel = onWatchChannel,
                                onToggleFavorite = { ch ->
                                    favoriteChannelIds = if (favoriteChannelIds.contains(ch.channelId)) {
                                        favoriteChannelIds - ch.channelId
                                    } else {
                                        favoriteChannelIds + ch.channelId
                                    }
                                },
                                onRequestCategoryNav = {
                                    filterFocusRequesters[selectedCategory]?.safeRequest()
                                },
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Program Details Dialog
        programDetailsDialog?.let { (channel, airing) ->
            Tablo4UProgramDetailsDialog(
                channel = channel,
                airing = airing,
                timeFormat = timeFormat,
                isRecorded = scheduledRecordingIds.contains(airing.airingId),
                onToggleRecord = {
                    scheduledRecordingIds = if (scheduledRecordingIds.contains(airing.airingId)) {
                        scheduledRecordingIds - airing.airingId
                    } else {
                        scheduledRecordingIds + airing.airingId
                    }
                },
                onWatch = {
                    onWatchChannel(channel)
                    programDetailsDialog = null
                },
                onAssignTile = { tile ->
                    onAssignToTile(channel, tile)
                    programDetailsDialog = null
                },
                onDismiss = { programDetailsDialog = null }
            )
        }
    }
}

@Composable
private fun Tablo4USearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onRequestTopNav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(160.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TvSurfaceElevated else TvSurface)
            .border(
                BorderStroke(1.dp, if (isFocused) TvFocusHighlight else TvBorder),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (isFocused) TabloTeal else TextMuted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(TabloTeal),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            onRequestTopNav()
                            true
                        } else false
                    } else false
                },
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text("Filter channels...", color = TextMuted, fontSize = 11.sp)
                }
                innerTextField()
            }
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun Tablo4UFormatButton(
    viewFormat: GuideViewFormat,
    onClick: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) TabloTeal else TvSurfaceElevated,
        border = BorderStroke(1.dp, if (isFocused) TvFocusHighlight else TvBorder),
        modifier = Modifier
            .testTag("btn_guide_format_toggle")
            .height(32.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        onRequestTopNav()
                        true
                    } else false
                } else false
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = when (viewFormat) {
                    GuideViewFormat.GRID -> Icons.Default.GridView
                    GuideViewFormat.SCHEDULE -> Icons.Default.CalendarMonth
                    GuideViewFormat.LIST -> Icons.Default.ViewList
                    GuideViewFormat.COMPACT -> Icons.Default.LiveTv
                },
                contentDescription = null,
                tint = if (isFocused) Color.Black else TextPrimary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = viewFormat.label,
                color = if (isFocused) Color.Black else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Settings Gear Action Button in the TV Guide Header
 */
@Composable
private fun Tablo4USettingsButton(
    onClick: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .height(32.dp)
            .testTag("btn_settings_gear")
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TabloTeal else TvSurfaceElevated)
            .border(
                if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        onRequestTopNav()
                        true
                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    ) {
                        onClick()
                        true
                    } else false
                } else false
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings & Multiview Hub",
                tint = if (isFocused) Color.Black else TabloTeal,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Settings",
                color = if (isFocused) Color.Black else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Category Filter Bar with fluid D-pad integration
 */
@Composable
private fun Tablo4UCategoryFilterBar(
    selectedCategory: ChannelFilterCategory,
    categories: List<ChannelFilterCategory>,
    onSelectCategory: (ChannelFilterCategory) -> Unit,
    filterFocusRequesters: Map<ChannelFilterCategory, FocusRequester>,
    onRequestTopNav: () -> Unit,
    onNavigateDownToContent: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { cat ->
            val isSelected = cat == selectedCategory
            val req = filterFocusRequesters[cat]
            Tablo4UFilterChip(
                category = cat,
                isSelected = isSelected,
                focusRequester = req,
                onClick = { onSelectCategory(cat) },
                onRequestTopNav = onRequestTopNav,
                onNavigateDown = onNavigateDownToContent
            )
        }
    }
}

@Composable
private fun Tablo4UFilterChip(
    category: ChannelFilterCategory,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onRequestTopNav: () -> Unit,
    onNavigateDown: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> TabloTeal
        isSelected -> Color(0x3300D2B4)
        else -> TvSurfaceElevated
    }
    val contentColor = when {
        isFocused -> Color.Black
        isSelected -> TabloTeal
        else -> TextSecondary
    }
    val border = when {
        isFocused -> BorderStroke(2.dp, TvFocusHighlight)
        isSelected -> BorderStroke(1.dp, TabloTeal.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, TvBorder)
    }

    var mod = Modifier
        .clip(RoundedCornerShape(14.dp))
        .background(bg)
        .border(border, RoundedCornerShape(14.dp))
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .focusable(interactionSource = interactionSource)
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        onRequestTopNav()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onNavigateDown()
                        true
                    }
                    else -> false
                }
            } else false
        }
        .padding(horizontal = 12.dp, vertical = 5.dp)

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    Box(modifier = mod) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (category == ChannelFilterCategory.FAVORITES) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = category.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Tablo4U Signature Timeline Grid.
 * Includes Guide Time Indicator (laser red line & header time beacon).
 */
@Composable
private fun Tablo4UTimelineGrid(
    channels: List<TabloChannel>,
    channelAiringsMap: Map<String, List<TabloAiring>>,
    windowStart: Long,
    now: Long,
    timeFormat: SimpleDateFormat,
    scrollState: androidx.compose.foundation.ScrollState,
    favoriteChannelIds: Set<String>,
    scheduledRecordingIds: Set<String>,
    firstContentFocusRequester: FocusRequester,
    focusedEpgTimeMs: Long,
    onUpdateFocusedEpgTime: (Long) -> Unit,
    onWatchChannel: (TabloChannel) -> Unit,
    onShowDetails: (TabloChannel, TabloAiring) -> Unit,
    onToggleFavorite: (TabloChannel) -> Unit,
    onRequestCategoryNav: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timelineWidth = (GuideTiming.SLOT_COUNT * SLOT_WIDTH).toInt()
    val listState = rememberLazyListState()

    val timeSlots = remember(windowStart) {
        (0 until GuideTiming.SLOT_COUNT).map { GuideTiming.slotTimeMs(windowStart, it) }
    }
    val contentWidth = (timelineWidth + CHANNEL_COLUMN_WIDTH.toInt()).dp

    val airingFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    fun getAiringFocusRequester(channelId: String, airingId: String): FocusRequester {
        val key = "${channelId}_${airingId}"
        return airingFocusRequesters.getOrPut(key) { FocusRequester() }
    }

    fun navigateUpDown(currentChannelIndex: Int, isUp: Boolean) {
        val targetIndex = if (isUp) currentChannelIndex - 1 else currentChannelIndex + 1
        if (targetIndex in channels.indices) {
            val targetChannel = channels[targetIndex]
            val targetAirings = channelAiringsMap[targetChannel.channelId].orEmpty()
            val targetAiring = targetAirings.find {
                val endMs = it.startTimeMillis + (it.durationSeconds * 1000L)
                it.startTimeMillis <= focusedEpgTimeMs && endMs > focusedEpgTimeMs
            } ?: targetAirings.minByOrNull { Math.abs(it.startTimeMillis - focusedEpgTimeMs) }

            if (targetAiring != null) {
                getAiringFocusRequester(targetChannel.channelId, targetAiring.airingId).safeRequest()
            }
        } else if (isUp && currentChannelIndex == 0) {
            onRequestCategoryNav()
        }
    }

    // Calculate Guide Time Indicator Position
    val nowOffsetMinutes = ((now - windowStart) / 60_000L).toFloat()
    val nowLineX = (nowOffsetMinutes * PX_PER_MINUTE).coerceAtLeast(0f)

    Column(modifier = modifier) {
        // Horizontal Scrollable Timeline Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            Column(modifier = Modifier.width(contentWidth)) {
                // Time Slot Header Row with Guide Time Indicator Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Color(0x800F172A), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Channel Header
                        Box(
                            modifier = Modifier
                                .width(CHANNEL_COLUMN_WIDTH.dp)
                                .fillMaxHeight()
                                .background(TvSurfaceElevated)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "CHANNEL",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        // Time Interval Cells
                        timeSlots.forEach { slotMs ->
                            Box(
                                modifier = Modifier
                                    .width(SLOT_WIDTH.dp)
                                    .fillMaxHeight()
                                    .border(BorderStroke(0.5.dp, Color(0x332E3A4E))),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(TabloTeal.copy(alpha = 0.7f))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = timeFormat.format(Date(slotMs)),
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Guide Time Indicator: Header Flag & Pulsing Dot
                    if (nowLineX in 0f..(GuideTiming.SLOT_COUNT * SLOT_WIDTH)) {
                        Box(
                            modifier = Modifier
                                .offset(x = (CHANNEL_COLUMN_WIDTH + nowLineX - 32f).dp, y = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(GuideTimeLineRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Text(
                                    text = "NOW",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Channel Rows in Timeline format with Guide Time Indicator line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(channels, key = { _, ch -> ch.channelId }) { index, channel ->
                            val channelAirings = channelAiringsMap[channel.channelId] ?: emptyList()

                            Tablo4UTimelineRow(
                                index = index,
                                channel = channel,
                                channelAirings = channelAirings,
                                windowStart = windowStart,
                                contentWidth = contentWidth,
                                isFavorite = favoriteChannelIds.contains(channel.channelId),
                                scheduledRecordingIds = scheduledRecordingIds,
                                focusRequester = if (index == 0) firstContentFocusRequester else null,
                                getAiringFocusRequester = ::getAiringFocusRequester,
                                onAiringFocused = { airing -> onUpdateFocusedEpgTime(airing.startTimeMillis) },
                                onNavigateUpDown = { channelIdx, isUp -> navigateUpDown(channelIdx, isUp) },
                                onTune = { onWatchChannel(channel) },
                                onAiringClick = { airing -> onShowDetails(channel, airing) },
                                onToggleFavorite = { onToggleFavorite(channel) },
                                onRequestCategoryNav = onRequestCategoryNav
                            )
                        }
                    }

                    // GUIDE TIME INDICATOR: Vertical glowing laser line extending through all channel rows
                    if (nowLineX in 0f..(GuideTiming.SLOT_COUNT * SLOT_WIDTH)) {
                        Box(
                            modifier = Modifier
                                .offset(x = (CHANNEL_COLUMN_WIDTH + nowLineX - 2f).dp)
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(GuideTimeLineGlow)
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = (CHANNEL_COLUMN_WIDTH + nowLineX).dp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(GuideTimeLineRed)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single channel row inside the Tablo4U Timeline Grid.
 */
@Composable
private fun Tablo4UTimelineRow(
    index: Int,
    channel: TabloChannel,
    channelAirings: List<TabloAiring>,
    windowStart: Long,
    contentWidth: Dp,
    isFavorite: Boolean,
    scheduledRecordingIds: Set<String>,
    focusRequester: FocusRequester?,
    getAiringFocusRequester: (String, String) -> FocusRequester,
    onAiringFocused: (TabloAiring) -> Unit,
    onNavigateUpDown: (Int, Boolean) -> Unit,
    onTune: () -> Unit,
    onAiringClick: (TabloAiring) -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestCategoryNav: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .border(BorderStroke(0.5.dp, Color(0x332E3A4E)))
    ) {
        Row(modifier = Modifier.width(contentWidth).fillMaxHeight()) {
            // Left Sticky Channel Cell
            Tablo4UChannelCell(
                channel = channel,
                isFavorite = isFavorite,
                focusRequester = focusRequester,
                isTopRow = index == 0,
                onTune = onTune,
                onToggleFavorite = onToggleFavorite,
                onRequestCategoryNav = onRequestCategoryNav,
                modifier = Modifier
                    .width(CHANNEL_COLUMN_WIDTH.dp)
                    .fillMaxHeight()
            )

            // Timeline Airing Blocks
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                channelAirings.forEach { airing ->
                    val blockStartOffsetMs = (airing.startTimeMillis - windowStart).coerceAtLeast(0L)
                    val blockStartMinutes = blockStartOffsetMs / 60_000L
                    val blockLeftPx = blockStartMinutes * PX_PER_MINUTE

                    val blockDurationMs = (airing.durationSeconds * 1000L).coerceAtLeast(60_000L)
                    val blockDurationMinutes = blockDurationMs / 60_000L
                    val blockWidthPx = (blockDurationMinutes * PX_PER_MINUTE).coerceAtLeast(40f)

                    val isRecorded = scheduledRecordingIds.contains(airing.airingId)
                    val aFocusRequester = getAiringFocusRequester(channel.channelId, airing.airingId)

                    Tablo4UAiringBlock(
                        airing = airing,
                        channel = channel,
                        isRecorded = isRecorded,
                        leftPx = blockLeftPx,
                        widthPx = blockWidthPx,
                        focusRequester = aFocusRequester,
                        onFocused = { onAiringFocused(airing) },
                        onClick = { onAiringClick(airing) },
                        onTune = onTune,
                        onNavigateUp = { onNavigateUpDown(index, true) },
                        onNavigateDown = { onNavigateUpDown(index, false) },
                        isTopRow = index == 0,
                        onRequestCategoryNav = onRequestCategoryNav
                    )
                }
            }
        }
    }
}

/**
 * Tablo4U Channel Identifier Cell (Left side of grid row)
 */
@Composable
private fun Tablo4UChannelCell(
    channel: TabloChannel,
    isFavorite: Boolean,
    focusRequester: FocusRequester?,
    isTopRow: Boolean,
    onTune: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestCategoryNav: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    var mod = modifier
        .background(if (isFocused) Color(0xFF1E2D44) else TvSurfaceElevated)
        .border(
            BorderStroke(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) TvFocusHighlight else Color(0x332E3A4E)
            )
        )
        .clickable { onTune() }
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isTopRow) {
                            onRequestCategoryNav()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        onTune()
                        true
                    }
                    else -> false
                }
            } else false
        }
        .padding(horizontal = 8.dp)

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = mod
    ) {
        // Favorite Star
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFBBF24) else TextMuted,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Channel Number Badge
        Box(
            modifier = Modifier
                .background(
                    if (channel.isOtt) Color(0x3300D2B4) else Color(0x3338BDF8),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = channel.displayChannel,
                color = if (channel.isOtt) TabloTeal else TvFocusHighlight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Callsign & Network
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.callSign,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = channel.network,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (channel.isOtt) "FAST" else "HD",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Tablo4U Airing Block inside the timeline grid.
 */
@Composable
private fun Tablo4UAiringBlock(
    airing: TabloAiring,
    channel: TabloChannel,
    isRecorded: Boolean,
    leftPx: Float,
    widthPx: Float,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    onTune: () -> Unit,
    onNavigateUp: () -> Unit = {},
    onNavigateDown: () -> Unit = {},
    isTopRow: Boolean = false,
    onRequestCategoryNav: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val genreColor = getGenreColor(airing.category)

    val bg = if (isFocused) Color(0xFF1E3A5F) else TvSurface
    val border = if (isFocused) {
        BorderStroke(2.dp, TvFocusHighlight)
    } else {
        BorderStroke(0.5.dp, Color(0x442E3A4E))
    }

    var mod = Modifier
        .offset(x = leftPx.dp, y = 3.dp)
        .width((widthPx - 3f).dp)
        .height((ROW_HEIGHT - 6f).dp)
        .clip(RoundedCornerShape(6.dp))
        .background(bg)
        .border(border, RoundedCornerShape(6.dp))
        .onFocusChanged { state ->
            isFocused = state.isFocused
            if (state.isFocused) {
                onFocused()
            }
        }
        .clickable { onClick() }
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        onClick()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        onTune()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        onNavigateUp()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onNavigateDown()
                        true
                    }
                    else -> false
                }
            } else false
        }

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    Box(modifier = mod) {
        // Left Color Bar based on Genre / Category
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(genreColor)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Program Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = airing.title,
                    color = if (isFocused) Color.White else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isRecorded) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "REC",
                        tint = LiveRed,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // Episode Subtitle / Snippet
            val subtitleText = airing.episodeTitle ?: airing.category
            Text(
                text = subtitleText,
                color = if (isFocused) TabloTeal else TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Duration / Rating Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                val durMinutes = (airing.durationSeconds / 60L)
                Text(
                    text = "${durMinutes}m",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )

                if (airing.rating.isNotEmpty() && widthPx > 100f) {
                    Text(
                        text = airing.rating,
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Tablo4U Detailed Cards View Format
 */
@Composable
private fun Tablo4UCardListView(
    channels: List<TabloChannel>,
    channelAiringsMap: Map<String, List<TabloAiring>>,
    now: Long,
    timeFormat: SimpleDateFormat,
    favoriteChannelIds: Set<String>,
    firstItemFocusRequester: FocusRequester,
    onWatchChannel: (TabloChannel) -> Unit,
    onShowDetails: (TabloChannel, TabloAiring) -> Unit,
    onToggleFavorite: (TabloChannel) -> Unit,
    onRequestCategoryNav: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.channelId }) { index, channel ->
            val channelAirings = channelAiringsMap[channel.channelId] ?: emptyList()
            val currentAiring = channelAirings.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
                ?: channelAirings.firstOrNull()
            val upcomingAiring = channelAirings.firstOrNull {
                currentAiring != null && it.startTimeMillis >= currentAiring.endTimeMillis
            }

            if (currentAiring != null) {
                var isCardFocused by remember { mutableStateOf(false) }

                val elapsedMs = (now - currentAiring.startTimeMillis).coerceAtLeast(0L)
                val totalMs = (currentAiring.durationSeconds * 1000L).coerceAtLeast(60_000L)
                val progress = (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0.05f, 0.98f)
                val genreColor = getGenreColor(currentAiring.category)
                val isFavorite = favoriteChannelIds.contains(channel.channelId)

                var mod = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isCardFocused) Color(0xFF1E2D44) else TvSurface)
                    .border(
                        BorderStroke(
                            if (isCardFocused) 2.dp else 1.dp,
                            if (isCardFocused) TvFocusHighlight else TvBorder
                        ),
                        RoundedCornerShape(10.dp)
                    )
                    .onFocusChanged { isCardFocused = it.isFocused }
                    .clickable { onWatchChannel(channel) }
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    onWatchChannel(channel)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (index == 0) {
                                        onRequestCategoryNav()
                                        true
                                    } else false
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    onBack()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .padding(12.dp)

                if (index == 0) {
                    mod = mod.focusRequester(firstItemFocusRequester)
                }

                Column(modifier = mod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = { onToggleFavorite(channel) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (isFavorite) Color(0xFFFBBF24) else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0x3338BDF8), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(channel.displayChannel, color = TvFocusHighlight, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                            Text(channel.callSign, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(channel.network, color = TextSecondary, fontSize = 11.sp)
                        }

                        // Time range
                        Text(
                            text = "${timeFormat.format(Date(currentAiring.startTimeMillis))} – ${timeFormat.format(Date(currentAiring.endTimeMillis))}",
                            color = TabloTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Now Playing Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(currentAiring.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .background(genreColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(currentAiring.category, color = genreColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            currentAiring.episodeTitle?.let { ep ->
                                if (ep.isNotBlank()) {
                                    Text(ep, color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        // Quick Watch & Details
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onWatchChannel(channel) },
                                colors = ButtonDefaults.buttonColors(containerColor = TabloTeal, contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Watch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onShowDetails(channel, currentAiring) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Details", fontSize = 11.sp)
                            }
                        }
                    }

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = TabloTeal,
                        trackColor = Color(0x33FFFFFF)
                    )

                    // Up next snippet
                    if (upcomingAiring != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("UP NEXT:", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${timeFormat.format(Date(upcomingAiring.startTimeMillis))}  ${upcomingAiring.title}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tablo4U Compact Channel List Format
 */
@Composable
private fun Tablo4UCompactListView(
    channels: List<TabloChannel>,
    channelAiringsMap: Map<String, List<TabloAiring>>,
    favoriteChannelIds: Set<String>,
    firstItemFocusRequester: FocusRequester,
    onWatchChannel: (TabloChannel) -> Unit,
    onToggleFavorite: (TabloChannel) -> Unit,
    onRequestCategoryNav: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.channelId }) { index, channel ->
            val channelAirings = channelAiringsMap[channel.channelId] ?: emptyList()
            val currentAiring = channelAirings.firstOrNull()

            var isFocused by remember { mutableStateOf(false) }
            val isFavorite = favoriteChannelIds.contains(channel.channelId)

            var mod = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isFocused) Color(0xFF1E2D44) else TvSurface)
                .border(
                    BorderStroke(if (isFocused) 2.dp else 1.dp, if (isFocused) TvFocusHighlight else TvBorder),
                    RoundedCornerShape(6.dp)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onWatchChannel(channel) }
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                onWatchChannel(channel)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (index == 0) {
                                    onRequestCategoryNav()
                                    true
                                } else false
                            }
                            KeyEvent.KEYCODE_BACK -> {
                                onBack()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .padding(horizontal = 12.dp)

            if (index == 0) {
                mod = mod.focusRequester(firstItemFocusRequester)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = mod
            ) {
                // Channel ID
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onToggleFavorite(channel) }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFFBBF24) else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(channel.displayChannel, color = TvFocusHighlight, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(channel.callSign, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(channel.network, color = TextSecondary, fontSize = 10.sp)
                }

                // Current Airing Title
                currentAiring?.let { airing ->
                    Text(
                        text = airing.title,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )
                }

                // Quick Play Button
                Button(
                    onClick = { onWatchChannel(channel) },
                    colors = ButtonDefaults.buttonColors(containerColor = TabloTeal, contentColor = Color.Black),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("Tune", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Tablo4U Detailed Program Modal Dialog
 */
@Composable
private fun Tablo4UProgramDetailsDialog(
    channel: TabloChannel,
    airing: TabloAiring,
    timeFormat: SimpleDateFormat,
    isRecorded: Boolean,
    onToggleRecord: () -> Unit,
    onWatch: () -> Unit,
    onAssignTile: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val genreColor = getGenreColor(airing.category)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = TvSurfaceElevated,
            border = BorderStroke(1.5.dp, TvFocusHighlight),
            modifier = Modifier
                .width(520.dp)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Channel tag + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(if (channel.isOtt) Color(0x3300D2B4) else Color(0x3338BDF8), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = channel.displayChannel,
                                color = if (channel.isOtt) TabloTeal else TvFocusHighlight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${channel.callSign} (${channel.network})",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Show Title & Category
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = airing.title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )

                    airing.episodeTitle?.let { ep ->
                        if (ep.isNotBlank()) {
                            Text(text = ep, color = TabloTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Tags: Genre, Rating, Air Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(genreColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(airing.category, color = genreColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    if (airing.rating.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(airing.rating, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val timeStr = "${timeFormat.format(Date(airing.startTimeMillis))} – ${timeFormat.format(Date(airing.endTimeMillis))}"
                    Text(timeStr, color = TextSecondary, fontSize = 11.sp)
                }

                // Synopsis
                Text(
                    text = airing.description ?: "Full live broadcast streaming from ${channel.callSign}.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Divider(color = TvBorder, thickness = 1.dp)

                // Action Buttons Row: Watch Live, Record, Multiview Tile 1-4
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onWatch,
                            colors = ButtonDefaults.buttonColors(containerColor = TabloTeal, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Watch Live", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onToggleRecord,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecorded) Color(0x33EF4444) else TvSurface,
                                contentColor = if (isRecorded) LiveRed else TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isRecorded) LiveRed else TvBorder),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = if (isRecorded) LiveRed else TextMuted, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isRecorded) "Cancel Rec" else "Record Series", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Multiview Assignment Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Send to Multiview:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (tile in 0..3) {
                                Button(
                                    onClick = { onAssignTile(tile) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TvSurface, contentColor = TextPrimary),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, TvBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Tile ${tile + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty / Filtered out Guide State
 */
@Composable
private fun Tablo4UEmptyGuideState(
    searchQuery: String,
    onClearSearch: () -> Unit,
    onRetry: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(TvSurface)
                .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(12.dp))
                .padding(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = TabloTeal,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (searchQuery.isNotEmpty()) "No channels match '$searchQuery'" else "No channels in this category",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Adjust your category filter or search query to view listings.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (searchQuery.isNotEmpty()) {
                    Button(
                        onClick = onClearSearch,
                        colors = ButtonDefaults.buttonColors(containerColor = TabloTeal, contentColor = Color.Black)
                    ) {
                        Text("Clear Filter")
                    }
                }
                OutlinedButton(onClick = onRetry) {
                    Text("Refresh Listings")
                }
            }
        }
    }
}

/**
 * Loading state when fetching channels and guide from the Tablo API.
 */
@Composable
private fun GuideLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                color = TabloTeal,
                strokeWidth = 3.dp,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = "Loading Tablo Channel Guide...",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fetching live broadcast schedules and streaming lineups",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Future Programming Schedule View for searching and browsing upcoming topic schedules (e.g. Sports, Movies, News).
 */
@Composable
private fun Tablo4UFutureScheduleView(
    scheduleItems: List<Pair<TabloChannel, TabloAiring>>,
    now: Long,
    timeFormat: SimpleDateFormat,
    favoriteChannelIds: Set<String>,
    scheduledRecordingIds: Set<String>,
    firstContentFocusRequester: FocusRequester,
    onWatchChannel: (TabloChannel) -> Unit,
    onShowDetails: (TabloChannel, TabloAiring) -> Unit,
    onToggleRecord: (TabloAiring) -> Unit,
    onRequestCategoryNav: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val dayFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    if (scheduleItems.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "No upcoming programming found for this filter",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Try clearing search keywords or selecting a broader category.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(scheduleItems) { index, (channel, airing) ->
            val isFirst = index == 0
            val isRecorded = scheduledRecordingIds.contains(airing.airingId)
            val isFavorite = favoriteChannelIds.contains(channel.channelId)

            val airingStartCal = java.util.Calendar.getInstance().apply { timeInMillis = airing.startTimeMillis }
            val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val isToday = airingStartCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR) &&
                    airingStartCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR)

            val timeLabel = when {
                airing.isLive -> "🔴 LIVE NOW"
                isToday -> "TODAY • ${timeFormat.format(Date(airing.startTimeMillis))}"
                else -> "${dayFormat.format(Date(airing.startTimeMillis))} • ${timeFormat.format(Date(airing.startTimeMillis))}"
            }

            Tablo4UFutureScheduleItemCard(
                channel = channel,
                airing = airing,
                timeLabel = timeLabel,
                isRecorded = isRecorded,
                isFavorite = isFavorite,
                focusRequester = if (isFirst) firstContentFocusRequester else null,
                onWatch = { onWatchChannel(channel) },
                onShowDetails = { onShowDetails(channel, airing) },
                onToggleRecord = { onToggleRecord(airing) },
                onRequestCategoryNav = if (isFirst) onRequestCategoryNav else null,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun Tablo4UFutureScheduleItemCard(
    channel: TabloChannel,
    airing: TabloAiring,
    timeLabel: String,
    isRecorded: Boolean,
    isFavorite: Boolean,
    focusRequester: FocusRequester?,
    onWatch: () -> Unit,
    onShowDetails: () -> Unit,
    onToggleRecord: () -> Unit,
    onRequestCategoryNav: (() -> Unit)?,
    onBack: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val genreColor = remember(airing.category) { getGenreColor(airing.category) }

    var cardModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(if (isFocused) TvSurfaceElevated else TvSurface)
        .border(
            if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder),
            RoundedCornerShape(10.dp)
        )
        .clickable(interactionSource = interactionSource, indication = null) {
            onShowDetails()
        }
        .focusable(interactionSource = interactionSource)
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (onRequestCategoryNav != null) {
                            onRequestCategoryNav()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        onBack()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        onShowDetails()
                        true
                    }
                    else -> false
                }
            } else false
        }
        .padding(horizontal = 14.dp, vertical = 10.dp)

    if (focusRequester != null) {
        cardModifier = cardModifier.focusRequester(focusRequester)
    }

    Row(
        modifier = cardModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Time & Channel Badge Column
        Column(
            modifier = Modifier.width(130.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = timeLabel,
                color = if (airing.isLive) LiveRed else TabloTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(if (channel.isOtt) Color(0x3300D2B4) else Color(0x3338BDF8), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (channel.isOtt) "FAST" else "OTA",
                        color = if (channel.isOtt) TabloTeal else TvFocusHighlight,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = channel.displayName,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Program Info Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = airing.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .background(genreColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(airing.category, color = genreColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                if (airing.rating.isNotEmpty()) {
                    Text(
                        text = airing.rating,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!airing.episodeTitle.isNullOrBlank()) {
                Text(
                    text = airing.episodeTitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = airing.description ?: "Live broadcast streaming on ${channel.callSign}.",
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }

        // Quick Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (airing.isLive) {
                Surface(
                    onClick = onWatch,
                    shape = RoundedCornerShape(6.dp),
                    color = TabloTeal,
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                        Text("Watch", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Surface(
                    onClick = onWatch,
                    shape = RoundedCornerShape(6.dp),
                    color = TvSurfaceElevated,
                    border = BorderStroke(1.dp, TvBorder),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                        Text("Tune", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Surface(
                onClick = onToggleRecord,
                shape = RoundedCornerShape(6.dp),
                color = if (isRecorded) Color(0x33EF4444) else TvSurfaceElevated,
                border = BorderStroke(1.dp, if (isRecorded) LiveRed else TvBorder),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = if (isRecorded) LiveRed else TextMuted,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = if (isRecorded) "Rec On" else "Record",
                        color = if (isRecorded) LiveRed else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
