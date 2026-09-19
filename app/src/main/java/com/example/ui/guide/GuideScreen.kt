package com.example.ui.guide

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch

private const val CHANNEL_COLUMN_WIDTH = 190f
private const val SLOT_WIDTH = 180f
private const val SLOT_MINUTES = GuideTiming.SLOT_MINUTES
private const val PX_PER_MINUTE = SLOT_WIDTH / SLOT_MINUTES.toFloat()
private const val ROW_HEIGHT = 74f

enum class GuideViewFormat(val label: String) {
    GRID("Timeline Grid"),
    LIST("Detailed Cards"),
    COMPACT("Channel List")
}

enum class ChannelFilterCategory(val label: String, val iconName: String) {
    ALL("All Channels", "grid"),
    FAVORITES("Favorites", "star"),
    OTA("OTA Antenna", "antenna"),
    FAST("FAST Streaming", "flash"),
    SPORTS("Sports", "sports"),
    MOVIES("Movies", "movies"),
    NEWS("News", "news"),
    SERIES("Series", "series")
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
 * Tablo4U-Styled TV Guide Screen.
 * Features:
 * - Real channel guide grid with channel logos, numbers, callsigns, OTA/FAST tags, HD resolution badges, and favorite stars.
 * - Guide Time Indicator: Real-time red vertical line with live timestamp flag across the timeline and channel rows.
 * - Rich Program Inspector Banner showing currently selected/focused program details and instant action buttons.
 * - Multiple layout modes (Timeline Grid, Detailed Cards, Compact List).
 * - Instant Search & Realtime Category Filtering.
 * - DVR recording scheduling tags and Multiview 1-4 routing shortcuts.
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
    focusRequester: FocusRequester? = null,
    onRequestTopNav: () -> Unit = {},
    onNavigateLeftPage: () -> Unit = {},
    onNavigateRightPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val windowStart = remember { GuideTiming.windowStartMs() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // Live clock ticker every 15 seconds to update the Guide Time Indicator
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = System.currentTimeMillis()
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }

    var selectedFormat by remember { mutableStateOf(GuideViewFormat.GRID) }
    var selectedCategory by remember { mutableStateOf(ChannelFilterCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var favoriteChannelIds by remember { mutableStateOf(setOf<String>()) }
    var scheduledRecordingIds by remember { mutableStateOf(setOf<String>()) }

    // Currently focused / hovered channel & airing for the rich Tablo4U Inspector Banner
    var focusedChannel by remember { mutableStateOf<TabloChannel?>(channels.firstOrNull()) }
    var focusedAiring by remember { mutableStateOf<TabloAiring?>(null) }
    var programDetailsDialog by remember { mutableStateOf<Pair<TabloChannel, TabloAiring>?>(null) }

    val filterFocusRequesters = remember {
        ChannelFilterCategory.values().associateWith { FocusRequester() }
    }
    val firstItemFocusRequester = remember { FocusRequester() }
    val gridScrollState = rememberScrollState()

    // Filter channels based on search query, favorite tags, and category chips
    val filteredChannels = remember(channels, airings, selectedCategory, searchQuery, favoriteChannelIds) {
        channels.filter { ch ->
            val matchesSearch = searchQuery.isBlank() ||
                    ch.callSign.contains(searchQuery, ignoreCase = true) ||
                    ch.network.contains(searchQuery, ignoreCase = true) ||
                    ch.displayChannel.contains(searchQuery, ignoreCase = true)

            if (!matchesSearch) return@filter false

            when (selectedCategory) {
                ChannelFilterCategory.ALL -> true
                ChannelFilterCategory.FAVORITES -> favoriteChannelIds.contains(ch.channelId)
                ChannelFilterCategory.OTA -> !ch.isOtt
                ChannelFilterCategory.FAST -> ch.isOtt
                ChannelFilterCategory.SPORTS -> {
                    val activeAiring = airings.firstOrNull { it.channelId == ch.channelId && it.isLive }
                    activeAiring?.category.equals("Sports", ignoreCase = true) ||
                            activeAiring?.title?.contains("Sports", ignoreCase = true) == true ||
                            ch.callSign.contains("SPORT", ignoreCase = true)
                }
                ChannelFilterCategory.NEWS -> {
                    val activeAiring = airings.firstOrNull { it.channelId == ch.channelId && it.isLive }
                    activeAiring?.category.equals("News", ignoreCase = true) ||
                            activeAiring?.title?.contains("News", ignoreCase = true) == true ||
                            ch.callSign.contains("NEWS", ignoreCase = true)
                }
                ChannelFilterCategory.MOVIES -> {
                    val activeAiring = airings.firstOrNull { it.channelId == ch.channelId && it.isLive }
                    activeAiring?.category.equals("Movies", ignoreCase = true) ||
                            activeAiring?.title?.contains("Movie", ignoreCase = true) == true ||
                            ch.callSign.contains("MOVIE", ignoreCase = true)
                }
                ChannelFilterCategory.SERIES -> {
                    val activeAiring = airings.firstOrNull { it.channelId == ch.channelId && it.isLive }
                    activeAiring?.category.equals("Drama", ignoreCase = true) ||
                            activeAiring?.category.equals("Comedy", ignoreCase = true)
                }
            }
        }
    }

    // Default focused channel update
    LaunchedEffect(filteredChannels) {
        if (focusedChannel == null || !filteredChannels.contains(focusedChannel)) {
            focusedChannel = filteredChannels.firstOrNull()
        }
    }

    // Auto-focus content when navigating down from top quick bar
    LaunchedEffect(focusRequester) {
        if (focusRequester != null) {
            firstItemFocusRequester.safeRequest()
        }
    }

    // Function to scroll the timeline grid to the current time ("Now" position)
    val scrollToNow: () -> Unit = {
        coroutineScope.launch {
            val offsetMinutes = ((now - windowStart) / 60_000L).coerceAtLeast(0L)
            val targetPx = (offsetMinutes * PX_PER_MINUTE) - 100f
            gridScrollState.animateScrollTo(targetPx.toInt().coerceAtLeast(0))
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
            // Tablo4U Top Header: Brand info, Search, "Jump to Now" button, Format toggle, and Refresh
            Tablo4UTopHeader(
                now = now,
                timeFormat = timeFormat,
                dayFormat = dayFormat,
                channelCount = filteredChannels.size,
                tabloDevice = tabloDevice,
                isLoading = isLoading,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                viewFormat = selectedFormat,
                onToggleFormat = {
                    selectedFormat = when (selectedFormat) {
                        GuideViewFormat.GRID -> GuideViewFormat.LIST
                        GuideViewFormat.LIST -> GuideViewFormat.COMPACT
                        GuideViewFormat.COMPACT -> GuideViewFormat.GRID
                    }
                },
                onScrollToNow = scrollToNow,
                onRefresh = onRefresh,
                onRequestTopNav = onRequestTopNav
            )

            // Category & Genre Filter Bar
            Tablo4UCategoryFilterBar(
                selectedCategory = selectedCategory,
                categories = ChannelFilterCategory.values().toList(),
                onSelectCategory = { selectedCategory = it },
                filterFocusRequesters = filterFocusRequesters,
                onRequestTopNav = onRequestTopNav,
                onNavigateDownToContent = { firstItemFocusRequester.safeRequest() }
            )

            // Tablo4U Live Program Inspector Banner (Details of focused channel/airing)
            val effectiveAiring = focusedAiring ?: focusedChannel?.let { ch ->
                val chAirings = TabloGuideSynthesizer.resolveAiringsForChannel(
                    ch, airings, windowStart, GuideTiming.windowEndMs(now), now
                )
                chAirings.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
                    ?: chAirings.firstOrNull()
            }

            Tablo4UProgramInspectorBanner(
                channel = focusedChannel,
                airing = effectiveAiring,
                now = now,
                timeFormat = timeFormat,
                isFavorite = focusedChannel?.let { favoriteChannelIds.contains(it.channelId) } ?: false,
                isRecorded = effectiveAiring?.let { scheduledRecordingIds.contains(it.airingId) } ?: false,
                onToggleFavorite = { ch ->
                    favoriteChannelIds = if (favoriteChannelIds.contains(ch.channelId)) {
                        favoriteChannelIds - ch.channelId
                    } else {
                        favoriteChannelIds + ch.channelId
                    }
                },
                onToggleRecord = { airing ->
                    scheduledRecordingIds = if (scheduledRecordingIds.contains(airing.airingId)) {
                        scheduledRecordingIds - airing.airingId
                    } else {
                        scheduledRecordingIds + airing.airingId
                    }
                },
                onWatchChannel = { focusedChannel?.let { onWatchChannel(it) } },
                onAssignToTile = { tile -> focusedChannel?.let { onAssignToTile(it, tile) } }
            )

            // Main Guide Body (Timeline Grid with Guide Time Indicator vs Cards vs Compact)
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
                                airings = airings,
                                windowStart = windowStart,
                                now = now,
                                timeFormat = timeFormat,
                                scrollState = gridScrollState,
                                favoriteChannelIds = favoriteChannelIds,
                                scheduledRecordingIds = scheduledRecordingIds,
                                onFocusChange = { ch, airing ->
                                    focusedChannel = ch
                                    focusedAiring = airing
                                },
                                onWatchChannel = onWatchChannel,
                                onAssignToTile = onAssignToTile,
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
                                onRequestTopNav = onRequestTopNav,
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        GuideViewFormat.LIST -> {
                            Tablo4UCardListView(
                                channels = filteredChannels,
                                airings = airings,
                                windowStart = windowStart,
                                now = now,
                                timeFormat = timeFormat,
                                favoriteChannelIds = favoriteChannelIds,
                                scheduledRecordingIds = scheduledRecordingIds,
                                firstItemFocusRequester = firstItemFocusRequester,
                                onFocusChange = { ch, airing ->
                                    focusedChannel = ch
                                    focusedAiring = airing
                                },
                                onWatchChannel = onWatchChannel,
                                onAssignToTile = onAssignToTile,
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
                                onRequestTopNav = onRequestTopNav,
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        GuideViewFormat.COMPACT -> {
                            Tablo4UCompactListView(
                                channels = filteredChannels,
                                airings = airings,
                                windowStart = windowStart,
                                now = now,
                                timeFormat = timeFormat,
                                favoriteChannelIds = favoriteChannelIds,
                                onFocusChange = { ch, airing ->
                                    focusedChannel = ch
                                    focusedAiring = airing
                                },
                                onWatchChannel = onWatchChannel,
                                onAssignToTile = onAssignToTile,
                                onToggleFavorite = { ch ->
                                    favoriteChannelIds = if (favoriteChannelIds.contains(ch.channelId)) {
                                        favoriteChannelIds - ch.channelId
                                    } else {
                                        favoriteChannelIds + ch.channelId
                                    }
                                },
                                onRequestTopNav = onRequestTopNav,
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Program Details Dialog (Full Tablo4U Modal with DVR options & specs)
        programDetailsDialog?.let { (channel, airing) ->
            Tablo4UProgramDetailsDialog(
                channel = channel,
                airing = airing,
                now = now,
                timeFormat = timeFormat,
                isFavorite = favoriteChannelIds.contains(channel.channelId),
                isRecorded = scheduledRecordingIds.contains(airing.airingId),
                onToggleFavorite = {
                    favoriteChannelIds = if (favoriteChannelIds.contains(channel.channelId)) {
                        favoriteChannelIds - channel.channelId
                    } else {
                        favoriteChannelIds + channel.channelId
                    }
                },
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

/**
 * Tablo4U Top Header: Shows device status, Search bar, Jump to Now button, Layout Switcher, and Refresh.
 */
@Composable
private fun Tablo4UTopHeader(
    now: Long,
    timeFormat: SimpleDateFormat,
    dayFormat: SimpleDateFormat,
    channelCount: Int,
    tabloDevice: TabloDevice?,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    viewFormat: GuideViewFormat,
    onToggleFormat: () -> Unit,
    onScrollToNow: () -> Unit,
    onRefresh: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Left: Guide Title + Live Clock + Device Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "TABLO GUIDE",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            // LIVE Badge with pulsing red beacon
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33EF4444))
                    .border(BorderStroke(1.dp, LiveRed.copy(alpha = 0.6f)), RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LiveRed.copy(alpha = pulseAlpha))
                )
                Text(
                    text = "LIVE • ${timeFormat.format(Date(now))}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tablo Device Stats Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TvSurfaceElevated)
                    .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = TabloTeal,
                    modifier = Modifier.size(14.dp)
                )
                val deviceName = tabloDevice?.name ?: "Tablo 4th Gen"
                val tunersText = if (tabloDevice != null) "${tabloDevice.tunerCount} Tuners" else "4 Tuners"
                Text(
                    text = "$deviceName • $tunersText • $channelCount Channels",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Right Controls: Search field, "Jump to Now" button, Layout switcher pill, Refresh button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Input
            Tablo4USearchField(
                query = searchQuery,
                onQueryChange = onSearchChange,
                onRequestTopNav = onRequestTopNav
            )

            // Jump to NOW button
            Tablo4UJumpNowButton(
                onClick = onScrollToNow,
                onRequestTopNav = onRequestTopNav
            )

            // View Format Switcher Pill
            Tablo4UFormatButton(
                viewFormat = viewFormat,
                onClick = onToggleFormat,
                onRequestTopNav = onRequestTopNav
            )

            // Refresh Listings Button
            Tablo4URefreshButton(
                isLoading = isLoading,
                onClick = onRefresh,
                onRequestTopNav = onRequestTopNav
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
            .height(34.dp)
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
            modifier = Modifier.size(15.dp)
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
                modifier = Modifier.size(18.dp)
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
private fun Tablo4UJumpNowButton(
    onClick: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) GuideTimeLineRed else TvSurfaceElevated,
        border = BorderStroke(1.dp, if (isFocused) TvFocusHighlight else TvBorder),
        modifier = Modifier
            .height(34.dp)
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
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = if (isFocused) Color.White else GuideTimeLineRed,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "NOW",
                color = if (isFocused) Color.White else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
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
            .height(34.dp)
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
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = when (viewFormat) {
                    GuideViewFormat.GRID -> Icons.Default.GridView
                    GuideViewFormat.LIST -> Icons.Default.ViewList
                    GuideViewFormat.COMPACT -> Icons.Default.LiveTv
                },
                contentDescription = null,
                tint = if (isFocused) Color.Black else TextPrimary,
                modifier = Modifier.size(14.dp)
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

@Composable
private fun Tablo4URefreshButton(
    isLoading: Boolean,
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
            .testTag("btn_refresh_listings")
            .height(34.dp)
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
            if (isLoading) {
                CircularProgressIndicator(
                    color = if (isFocused) Color.Black else TabloTeal,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = if (isLoading) "Syncing..." else "Refresh",
                color = if (isFocused) Color.Black else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Tablo4U Category & Genre Filter Bar
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
 * Tablo4U Rich Program Inspector Banner.
 * Displays high-resolution details of the focused channel and airing with instant action buttons.
 */
@Composable
private fun Tablo4UProgramInspectorBanner(
    channel: TabloChannel?,
    airing: TabloAiring?,
    now: Long,
    timeFormat: SimpleDateFormat,
    isFavorite: Boolean,
    isRecorded: Boolean,
    onToggleFavorite: (TabloChannel) -> Unit,
    onToggleRecord: (TabloAiring) -> Unit,
    onWatchChannel: () -> Unit,
    onAssignToTile: (Int) -> Unit
) {
    if (channel == null || airing == null) return

    val elapsedMs = (now - airing.startTimeMillis).coerceAtLeast(0L)
    val totalMs = (airing.durationSeconds * 1000L).coerceAtLeast(60_000L)
    val progress = (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val isLiveNow = now in airing.startTimeMillis until airing.endTimeMillis
    val remainingMinutes = ((totalMs - elapsedMs) / 60_000L).coerceAtLeast(1L)
    val genreColor = getGenreColor(airing.category)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TvSurface)
            .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Channel Branding Pillar
            Column(
                modifier = Modifier
                    .width(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvSurfaceElevated)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (channel.isOtt) Color(0x3300D2B4) else Color(0x3338BDF8), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (channel.isOtt) "FAST" else "OTA",
                            color = if (channel.isOtt) TabloTeal else TvFocusHighlight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = channel.displayChannel,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    text = channel.callSign,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = channel.network,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )

                if (isFavorite) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(11.dp)
                        )
                        Text("FAVORITE", color = Color(0xFFFBBF24), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Middle: Airing Metadata & Synopsis
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Program Title + Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = airing.title,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Category Pill
                    Box(
                        modifier = Modifier
                            .background(genreColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, genreColor.copy(alpha = 0.6f)), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = airing.category,
                            color = genreColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Rating Pill
                    if (airing.rating.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(TvSurfaceElevated, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = airing.rating,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // LIVE / REC Badges
                    if (isLiveNow) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x33EF4444), RoundedCornerShape(4.dp))
                                .border(BorderStroke(1.dp, LiveRed.copy(alpha = 0.7f)), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ON NOW • ${remainingMinutes}m left",
                                color = LiveRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isRecorded) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x33EF4444), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = LiveRed, modifier = Modifier.size(8.dp))
                                Text("REC SCHEDULED", color = LiveRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Episode Subtitle & Broadcast Time Range
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val timeRange = "${timeFormat.format(Date(airing.startTimeMillis))} – ${timeFormat.format(Date(airing.endTimeMillis))}"
                    Text(
                        text = timeRange,
                        color = TabloTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    airing.episodeTitle?.let { ep ->
                        if (ep.isNotBlank()) {
                            Text(
                                text = "•  $ep",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Progress Bar (for live airings)
                if (isLiveNow) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = TabloTeal,
                        trackColor = Color(0x33FFFFFF)
                    )
                }

                // Description Synopsis
                Text(
                    text = airing.description ?: "Live broadcast airing on ${channel.displayName}.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Instant Remote Action Buttons (Watch Live, Multiview 1-4, Record, Favorite)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Big Watch Live Button
                Button(
                    onClick = onWatchChannel,
                    colors = ButtonDefaults.buttonColors(containerColor = TabloTeal, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Watch Live", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Quick Multiview Tile Launcher Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tile:", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    for (tileIdx in 0..3) {
                        Surface(
                            onClick = { onAssignToTile(tileIdx) },
                            shape = RoundedCornerShape(4.dp),
                            color = TvSurfaceElevated,
                            border = BorderStroke(1.dp, TvBorder),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${tileIdx + 1}", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Record & Favorite Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onToggleRecord(airing) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isRecorded) Color(0x33EF4444) else TvSurfaceElevated,
                        border = BorderStroke(1.dp, if (isRecorded) LiveRed else TvBorder),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = if (isRecorded) LiveRed else TextSecondary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = if (isRecorded) "Recorded" else "Record",
                                color = if (isRecorded) LiveRed else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        onClick = { onToggleFavorite(channel) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isFavorite) Color(0x33FBBF24) else TvSurfaceElevated,
                        border = BorderStroke(1.dp, if (isFavorite) Color(0xFFFBBF24) else TvBorder),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (isFavorite) Color(0xFFFBBF24) else TextSecondary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = if (isFavorite) "Favorite" else "Star",
                                color = if (isFavorite) Color(0xFFFBBF24) else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tablo4U Signature Timeline Grid.
 * Features:
 * - Fixed left channel column.
 * - Horizontal scrolling 30-min time slots.
 * - Guide Time Indicator: vertical glowing line spanning down all channel rows with 'NOW' flag.
 * - Proportional airing blocks with genre bars and live progress.
 */
@Composable
private fun Tablo4UTimelineGrid(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    windowStart: Long,
    now: Long,
    timeFormat: SimpleDateFormat,
    scrollState: androidx.compose.foundation.ScrollState,
    favoriteChannelIds: Set<String>,
    scheduledRecordingIds: Set<String>,
    onFocusChange: (TabloChannel, TabloAiring) -> Unit,
    onWatchChannel: (TabloChannel) -> Unit,
    onAssignToTile: (TabloChannel, Int) -> Unit,
    onShowDetails: (TabloChannel, TabloAiring) -> Unit,
    onToggleFavorite: (TabloChannel) -> Unit,
    onRequestTopNav: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val singleSlotMs = SLOT_MINUTES * 60_000L
    val timelineWidth = (GuideTiming.SLOT_COUNT * SLOT_WIDTH).toInt()
    val listState = rememberLazyListState()

    val timeSlots = remember(windowStart) {
        (0 until GuideTiming.SLOT_COUNT).map { GuideTiming.slotTimeMs(windowStart, it) }
    }
    val contentWidth = (timelineWidth + CHANNEL_COLUMN_WIDTH.toInt()).dp
    val windowEnd = GuideTiming.windowEndMs(now)

    // Calculate Guide Time Indicator Position (in pixels)
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
                        .height(36.dp)
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
                                .offset(x = (CHANNEL_COLUMN_WIDTH + nowLineX - 38f).dp, y = 2.dp)
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
                            val channelAirings = remember(channel, airings, windowStart, now) {
                                TabloGuideSynthesizer.resolveAiringsForChannel(channel, airings, windowStart, windowEnd, now)
                            }

                            Tablo4UTimelineRow(
                                index = index,
                                channel = channel,
                                channelAirings = channelAirings,
                                windowStart = windowStart,
                                singleSlotMs = singleSlotMs,
                                contentWidth = contentWidth,
                                isFavorite = favoriteChannelIds.contains(channel.channelId),
                                scheduledRecordingIds = scheduledRecordingIds,
                                onFocusChange = { airing -> onFocusChange(channel, airing) },
                                onTune = { onWatchChannel(channel) },
                                onAiringClick = { airing -> onShowDetails(channel, airing) },
                                onToggleFavorite = { onToggleFavorite(channel) },
                                onRequestTopNav = onRequestTopNav
                            )
                        }
                    }

                    // GUIDE TIME INDICATOR: Vertical glowing laser line extending through all channel rows
                    if (nowLineX in 0f..(GuideTiming.SLOT_COUNT * SLOT_WIDTH)) {
                        // Ambient glow line
                        Box(
                            modifier = Modifier
                                .offset(x = (CHANNEL_COLUMN_WIDTH + nowLineX - 2f).dp)
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(GuideTimeLineGlow)
                        )
                        // Core crisp laser line
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
    singleSlotMs: Long,
    contentWidth: Dp,
    isFavorite: Boolean,
    scheduledRecordingIds: Set<String>,
    onFocusChange: (TabloAiring) -> Unit,
    onTune: () -> Unit,
    onAiringClick: (TabloAiring) -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestTopNav: () -> Unit
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
                onTune = onTune,
                onToggleFavorite = onToggleFavorite,
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

                    Tablo4UAiringBlock(
                        airing = airing,
                        channel = channel,
                        isRecorded = isRecorded,
                        leftPx = blockLeftPx,
                        widthPx = blockWidthPx,
                        onFocus = { onFocusChange(airing) },
                        onClick = { onAiringClick(airing) },
                        onTune = onTune,
                        isTopRow = index == 0,
                        onRequestTopNav = onRequestTopNav
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
    onTune: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
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
            .padding(horizontal = 8.dp)
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
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onTune: () -> Unit,
    isTopRow: Boolean,
    onRequestTopNav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val genreColor = getGenreColor(airing.category)

    val bg = if (isFocused) Color(0xFF1E3A5F) else TvSurface
    val border = if (isFocused) {
        BorderStroke(2.dp, TvFocusHighlight)
    } else {
        BorderStroke(0.5.dp, Color(0x442E3A4E))
    }

    Box(
        modifier = Modifier
            .offset(x = leftPx.dp, y = 3.dp)
            .width((widthPx - 3f).dp)
            .height((ROW_HEIGHT - 6f).dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(border, RoundedCornerShape(6.dp))
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocus()
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
                            if (isTopRow) {
                                onRequestTopNav()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
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
    airings: List<TabloAiring>,
    windowStart: Long,
    now: Long,
    timeFormat: SimpleDateFormat,
    favoriteChannelIds: Set<String>,
    scheduledRecordingIds: Set<String>,
    firstItemFocusRequester: FocusRequester,
    onFocusChange: (TabloChannel, TabloAiring) -> Unit,
    onWatchChannel: (TabloChannel) -> Unit,
    onAssignToTile: (TabloChannel, Int) -> Unit,
    onShowDetails: (TabloChannel, TabloAiring) -> Unit,
    onToggleFavorite: (TabloChannel) -> Unit,
    onRequestTopNav: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val windowEnd = GuideTiming.windowEndMs(now)

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.channelId }) { index, channel ->
            val channelAirings = remember(channel, airings, windowStart, now) {
                TabloGuideSynthesizer.resolveAiringsForChannel(channel, airings, windowStart, windowEnd, now)
            }
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
                val remainingMinutes = ((totalMs - elapsedMs) / 60_000L).coerceAtLeast(1L)
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
                    .onFocusChanged {
                        isCardFocused = it.isFocused
                        if (it.isFocused) {
                            onFocusChange(channel, currentAiring)
                        }
                    }
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
                                        onRequestTopNav()
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
    airings: List<TabloAiring>,
    windowStart: Long,
    now: Long,
    timeFormat: SimpleDateFormat,
    favoriteChannelIds: Set<String>,
    onFocusChange: (TabloChannel, TabloAiring) -> Unit,
    onWatchChannel: (TabloChannel) -> Unit,
    onAssignToTile: (TabloChannel, Int) -> Unit,
    onToggleFavorite: (TabloChannel) -> Unit,
    onRequestTopNav: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val windowEnd = GuideTiming.windowEndMs(now)

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.channelId }) { index, channel ->
            val channelAirings = remember(channel, airings, windowStart, now) {
                TabloGuideSynthesizer.resolveAiringsForChannel(channel, airings, windowStart, windowEnd, now)
            }
            val currentAiring = channelAirings.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
                ?: channelAirings.firstOrNull()

            var isFocused by remember { mutableStateOf(false) }
            val isFavorite = favoriteChannelIds.contains(channel.channelId)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isFocused) Color(0xFF1E2D44) else TvSurface)
                    .border(
                        BorderStroke(if (isFocused) 2.dp else 1.dp, if (isFocused) TvFocusHighlight else TvBorder),
                        RoundedCornerShape(6.dp)
                    )
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (it.isFocused && currentAiring != null) {
                            onFocusChange(channel, currentAiring)
                        }
                    }
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
                                        onRequestTopNav()
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
    now: Long,
    timeFormat: SimpleDateFormat,
    isFavorite: Boolean,
    isRecorded: Boolean,
    onToggleFavorite: () -> Unit,
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
                text = "Fetching live OTA broadcast schedules and FAST streaming lineups",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
