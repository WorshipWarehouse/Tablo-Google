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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val CHANNEL_COLUMN_WIDTH = 180f
private const val SLOT_WIDTH = 170f
private const val SLOT_MINUTES = GuideTiming.SLOT_MINUTES
private const val PX_PER_MINUTE = SLOT_WIDTH / SLOT_MINUTES.toFloat()
private const val ROW_HEIGHT = 68f

enum class GuideViewFormat {
    LIST,
    GRID
}

enum class ChannelFilterCategory(val label: String) {
    ALL("All Channels"),
    OTA("OTA Antenna"),
    FAST("FAST Streaming"),
    SPORTS("Sports"),
    NEWS("News"),
    MOVIES("Movies")
}

/**
 * TV Guide Component built with Jetpack Compose.
 * Fetches and displays live channel listings from the Tablo API in a scrollable list format,
 * fully optimized for D-pad/remote control navigation with explicit focus management and highlights.
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
    val windowStart = GuideTiming.windowStartMs()
    val now = System.currentTimeMillis()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    var selectedFormat by remember { mutableStateOf(GuideViewFormat.LIST) }
    var selectedCategory by remember { mutableStateOf(ChannelFilterCategory.ALL) }
    var programDialog by remember { mutableStateOf<Pair<TabloChannel, TabloAiring>?>(null) }

    val filterFocusRequesters = remember {
        ChannelFilterCategory.values().associateWith { FocusRequester() }
    }
    val firstItemFocusRequester = remember { FocusRequester() }

    // Filter channels based on selected category chip
    val filteredChannels = remember(channels, airings, selectedCategory) {
        when (selectedCategory) {
            ChannelFilterCategory.ALL -> channels
            ChannelFilterCategory.OTA -> channels.filter { !it.isOtt }
            ChannelFilterCategory.FAST -> channels.filter { it.isOtt }
            ChannelFilterCategory.SPORTS -> channels.filter { ch ->
                val activeAiring = airings.firstOrNull { it.channelId == ch.channelId && it.isLive }
                activeAiring?.category.equals("Sports", ignoreCase = true) ||
                        activeAiring?.title?.contains("Sports", ignoreCase = true) == true
            }
            ChannelFilterCategory.NEWS -> channels.filter { ch ->
                val activeAiring = airings.firstOrNull { it.channelId == ch.channelId && it.isLive }
                activeAiring?.category.equals("News", ignoreCase = true) ||
                        activeAiring?.title?.contains("News", ignoreCase = true) == true
            }
            ChannelFilterCategory.MOVIES -> channels.filter { ch ->
                val activeAiring = airings.firstOrNull { it.channelId == ch.channelId && it.isLive }
                activeAiring?.category.equals("Movies", ignoreCase = true) ||
                        activeAiring?.title?.contains("Movie", ignoreCase = true) == true
            }
        }
    }

    // Auto-focus content when navigating down from top quick bar
    LaunchedEffect(focusRequester) {
        if (focusRequester != null) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 24.dp, vertical = 14.dp)
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header: Tablo Status, Live Clock, Format Toggle, & Refresh listings
            GuideTopHeader(
                now = now,
                timeFormat = timeFormat,
                channelCount = channels.size,
                tabloDevice = tabloDevice,
                isLoading = isLoading,
                viewFormat = selectedFormat,
                onToggleFormat = {
                    selectedFormat = if (selectedFormat == GuideViewFormat.LIST) {
                        GuideViewFormat.GRID
                    } else {
                        GuideViewFormat.LIST
                    }
                },
                onRefresh = onRefresh,
                onRequestTopNav = onRequestTopNav
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            CategoryFilterBar(
                selectedCategory = selectedCategory,
                categories = ChannelFilterCategory.values().toList(),
                onSelectCategory = { selectedCategory = it },
                filterFocusRequesters = filterFocusRequesters,
                onRequestTopNav = onRequestTopNav,
                onNavigateDownToContent = {
                    try {
                        firstItemFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Guide Body: Scrollable List Format vs Timeline Matrix
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading && channels.isEmpty()) {
                    // Loading State from Tablo API
                    GuideLoadingState()
                } else if (channels.isEmpty()) {
                    // Empty / Unreachable State
                    GuideEmptyState(
                        onRetry = onRefresh,
                        onRequestTopNav = onRequestTopNav
                    )
                } else {
                    when (selectedFormat) {
                        GuideViewFormat.LIST -> {
                            // The requested Scrollable List Format
                            TvLiveChannelScrollableList(
                                channels = filteredChannels,
                                airings = airings,
                                windowStart = windowStart,
                                now = now,
                                timeFormat = timeFormat,
                                firstItemFocusRequester = firstItemFocusRequester,
                                onWatchChannel = onWatchChannel,
                                onAssignToTile = onAssignToTile,
                                onShowDetails = { ch, airing -> programDialog = Pair(ch, airing) },
                                onRequestTopNav = {
                                    filterFocusRequesters[selectedCategory]?.requestFocus()
                                        ?: onRequestTopNav()
                                },
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        GuideViewFormat.GRID -> {
                            // Timeline Grid Matrix (EPG block view)
                            TvTimelineGrid(
                                channels = filteredChannels,
                                airings = airings,
                                windowStart = windowStart,
                                now = now,
                                timeFormat = timeFormat,
                                onWatchChannel = onWatchChannel,
                                onAssignToTile = onAssignToTile,
                                onShowDetails = { ch, airing -> programDialog = Pair(ch, airing) },
                                onRequestTopNav = onRequestTopNav,
                                onBack = onBack,
                                onNavigateLeftPage = onNavigateLeftPage,
                                onNavigateRightPage = onNavigateRightPage,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Program Action / Synopsis Dialog
        if (programDialog != null) {
            val (channel, airing) = programDialog!!
            ProgramActionDialog(
                channel = channel,
                airing = airing,
                timeFormat = timeFormat,
                onWatchFullscreen = {
                    programDialog = null
                    onWatchChannel(channel)
                },
                onAssignToTile = { tile ->
                    programDialog = null
                    onAssignToTile(channel, tile)
                },
                onDismiss = { programDialog = null }
            )
        }
    }
}

/**
 * Top Header displaying Tablo device connection status, Live clock beacon, View format switcher, and Tablo API Refresh button.
 */
@Composable
private fun GuideTopHeader(
    now: Long,
    timeFormat: SimpleDateFormat,
    channelCount: Int,
    tabloDevice: TabloDevice?,
    isLoading: Boolean,
    viewFormat: GuideViewFormat,
    onToggleFormat: () -> Unit,
    onRefresh: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Left: Screen Title + Live Status Badge + Tuner & Channel Stats
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "TV GUIDE",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            // Red LIVE Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33EF4444))
                    .border(BorderStroke(1.dp, LiveRed.copy(alpha = 0.6f)), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LiveRed)
                )
                Text(
                    text = "LIVE • ${timeFormat.format(Date(now))}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tablo Tuner & Channel Stats Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TvSurfaceElevated)
                    .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = TabloTeal,
                    modifier = Modifier.size(15.dp)
                )
                val deviceName = tabloDevice?.name ?: "Tablo 4th Gen"
                val tunersText = if (tabloDevice != null) "${tabloDevice.tunerCount} Tuners" else "4 Tuners"
                Text(
                    text = "$deviceName • $tunersText • $channelCount Channels",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Right: Format Switcher (List vs Grid) + Refresh Listings Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // View Format Switcher Pill
            TvFormatPill(
                viewFormat = viewFormat,
                onClick = onToggleFormat,
                onRequestTopNav = onRequestTopNav
            )

            // Refresh Listings Button
            TvRefreshPill(
                isLoading = isLoading,
                onClick = onRefresh,
                onRequestTopNav = onRequestTopNav
            )
        }
    }
}

@Composable
private fun TvFormatPill(
    viewFormat: GuideViewFormat,
    onClick: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = if (isFocused) TabloTeal else TvSurfaceElevated
    val contentColor = if (isFocused) Color.Black else TextPrimary
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = border,
        modifier = Modifier
            .testTag("btn_guide_format_toggle")
            .height(36.dp)
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = if (viewFormat == GuideViewFormat.LIST) Icons.Default.ViewList else Icons.Default.GridView,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (viewFormat == GuideViewFormat.LIST) "List Format" else "Grid Matrix",
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TvRefreshPill(
    isLoading: Boolean,
    onClick: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = if (isFocused) TabloTeal else TvSurfaceElevated
    val contentColor = if (isFocused) Color.Black else TextPrimary
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = border,
        modifier = Modifier
            .testTag("btn_refresh_listings")
            .height(36.dp)
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = contentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = if (isLoading) "Updating..." else "Refresh",
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Filter bar with D-pad navigation between category chips.
 */
@Composable
private fun CategoryFilterBar(
    selectedCategory: ChannelFilterCategory,
    categories: List<ChannelFilterCategory>,
    onSelectCategory: (ChannelFilterCategory) -> Unit,
    filterFocusRequesters: Map<ChannelFilterCategory, FocusRequester>,
    onRequestTopNav: () -> Unit,
    onNavigateDownToContent: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { cat ->
            val isSelected = cat == selectedCategory
            val req = filterFocusRequesters[cat]
            TvFilterChip(
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
private fun TvFilterChip(
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
        .clip(RoundedCornerShape(16.dp))
        .background(bg)
        .border(border, RoundedCornerShape(16.dp))
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
        .padding(horizontal = 14.dp, vertical = 7.dp)

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    Box(modifier = mod) {
        Text(
            text = category.label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * The primary TV Guide component: Scrollable List Format.
 * Vertically scrolling list of live channel cards with Now Playing, Progress Bar,
 * Up Next preview, and direct remote action shortcuts.
 */
@Composable
private fun TvLiveChannelScrollableList(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    windowStart: Long,
    now: Long,
    timeFormat: SimpleDateFormat,
    firstItemFocusRequester: FocusRequester,
    onWatchChannel: (TabloChannel) -> Unit,
    onAssignToTile: (TabloChannel, Int) -> Unit,
    onShowDetails: (TabloChannel, TabloAiring) -> Unit,
    onRequestTopNav: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.channelId }) { index, channel ->
            // Find current live broadcast and upcoming broadcast for this channel
            val channelAirings = remember(channel, airings, windowStart, now) {
                val windowEnd = GuideTiming.windowEndMs(now)
                TabloGuideSynthesizer.resolveAiringsForChannel(channel, airings, windowStart, windowEnd, now)
            }

            val currentAiring = channelAirings.firstOrNull { airing ->
                airing.isLive || (now in airing.startTimeMillis until airing.endTimeMillis)
            } ?: channelAirings.firstOrNull() ?: TabloAiring(
                airingId = "live-${channel.channelId}",
                channelId = channel.channelId,
                title = "${channel.callSign} Live Programming",
                episodeTitle = "${channel.network} Broadcast",
                description = "Live television broadcast airing now on ${channel.callSign} (${channel.network}).",
                startTimeMillis = now - (15 * 60_000L),
                durationSeconds = 3600L,
                category = "Broadcast",
                isLive = true
            )

            val upcomingAiring = channelAirings.firstOrNull { airing ->
                airing.startTimeMillis >= currentAiring.endTimeMillis
            }

            // Remote Navigation item
            TvChannelListItemCard(
                index = index,
                channel = channel,
                currentAiring = currentAiring,
                upcomingAiring = upcomingAiring,
                now = now,
                timeFormat = timeFormat,
                focusRequester = if (index == 0) firstItemFocusRequester else null,
                onFocused = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(index)
                    }
                },
                onWatchChannel = { onWatchChannel(channel) },
                onAssignToTile = { tile -> onAssignToTile(channel, tile) },
                onShowDetails = { onShowDetails(channel, currentAiring) },
                onRequestTopNav = onRequestTopNav,
                onBack = onBack
            )
        }
    }
}

/**
 * A single live channel listing card in the scrollable list format.
 * Includes complete channel branding, Now Playing, progress bar, Up Next line, and action buttons.
 */
@Composable
private fun TvChannelListItemCard(
    index: Int,
    channel: TabloChannel,
    currentAiring: TabloAiring,
    upcomingAiring: TabloAiring?,
    now: Long,
    timeFormat: SimpleDateFormat,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onWatchChannel: () -> Unit,
    onAssignToTile: (Int) -> Unit,
    onShowDetails: () -> Unit,
    onRequestTopNav: () -> Unit,
    onBack: () -> Unit
) {
    var isCardFocused by remember { mutableStateOf(false) }

    // Broadcast elapsed progress calculation
    val elapsedMs = (now - currentAiring.startTimeMillis).coerceAtLeast(0L)
    val totalMs = (currentAiring.durationSeconds * 1000L).coerceAtLeast(60_000L)
    val progress = (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0.05f, 0.98f)
    val remainingMinutes = ((totalMs - elapsedMs) / 60_000L).coerceAtLeast(1L)

    // Visual styles based on focus
    val cardBackground = if (isCardFocused) Color(0xFF0F2624) else TvSurface
    val cardBorder = if (isCardFocused) {
        BorderStroke(2.5.dp, TvFocusHighlight)
    } else {
        BorderStroke(1.dp, TvBorder)
    }

    var mod = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(cardBackground)
        .border(cardBorder, RoundedCornerShape(12.dp))
        .onFocusChanged {
            isCardFocused = it.isFocused
            if (it.isFocused) {
                onFocused()
            }
        }
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        onWatchChannel()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        onWatchChannel()
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
        .padding(16.dp)

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    Column(modifier = mod) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Channel Identity Column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.width(220.dp)
            ) {
                // Channel Number Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCardFocused) TabloTeal else Color(0x3300D2B4))
                        .border(
                            BorderStroke(1.dp, if (isCardFocused) TabloTeal else TabloTeal.copy(alpha = 0.5f)),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = channel.displayChannel,
                        color = if (isCardFocused) Color.Black else TabloTeal,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Column {
                    Text(
                        text = channel.network.ifBlank { channel.callSign },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${channel.callSign} • ${if (channel.resolution.isNotBlank()) channel.resolution else if (channel.isOtt) "720p" else "1080p"}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (channel.isOtt) "FAST Streaming" else "OTA Broadcast",
                        color = if (channel.isOtt) TabloTealDark else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Middle: Live Airing Details & Progress
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsating Red LIVE beacon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33EF4444))
                            .border(BorderStroke(0.5.dp, LiveRed), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(LiveRed)
                        )
                        Text(
                            text = "LIVE",
                            color = LiveRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Program Title
                    Text(
                        text = currentAiring.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Category Pill
                    if (currentAiring.category.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TvSurfaceElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentAiring.category.uppercase(),
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Episode / Subtitle
                if (!currentAiring.episodeTitle.isNullOrBlank()) {
                    Text(
                        text = currentAiring.episodeTitle,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Air times & progress bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = if (isCardFocused) TvFocusHighlight else TabloTeal,
                        trackColor = TvBorder,
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Text(
                        text = "${timeFormat.format(Date(currentAiring.startTimeMillis))} – ${timeFormat.format(Date(currentAiring.endTimeMillis))} • ${remainingMinutes}m left",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Up Next Line
                if (upcomingAiring != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Up Next @ ${timeFormat.format(Date(upcomingAiring.startTimeMillis))}: ${upcomingAiring.title}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Focused Remote Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Watch Fullscreen Button
                TvActionButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Watch Live",
                    isPrimary = true,
                    onClick = onWatchChannel
                )

                // Quick Multiview Tile Assign
                for (tileIndex in 0..3) {
                    TvTileAssignButton(
                        tileIndex = tileIndex,
                        onClick = { onAssignToTile(tileIndex) }
                    )
                }

                // Info / Synopsis Button
                TvActionButton(
                    icon = Icons.Default.Info,
                    label = "Details",
                    isPrimary = false,
                    onClick = onShowDetails
                )
            }
        }
    }
}

@Composable
private fun TvActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = when {
        isFocused -> if (isPrimary) Color.White else TabloTeal
        isPrimary -> TabloTeal
        else -> TvSurfaceElevated
    }
    val contentColor = when {
        isFocused -> Color.Black
        isPrimary -> Color.Black
        else -> TextPrimary
    }
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = border,
        modifier = Modifier
            .height(36.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TvTileAssignButton(
    tileIndex: Int,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = if (isFocused) TabloTeal else TvSurfaceElevated
    val contentColor = if (isFocused) Color.Black else TextSecondary
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = border,
        modifier = Modifier
            .height(36.dp)
            .width(36.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "T${tileIndex + 1}",
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Timeline Grid Format for 2D Matrix browsing with D-pad navigation.
 */
@Composable
private fun TvTimelineGrid(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    windowStart: Long,
    now: Long,
    timeFormat: SimpleDateFormat,
    onWatchChannel: (TabloChannel) -> Unit,
    onAssignToTile: (TabloChannel, Int) -> Unit,
    onShowDetails: (TabloChannel, TabloAiring) -> Unit,
    onRequestTopNav: () -> Unit,
    onBack: () -> Unit,
    onNavigateLeftPage: () -> Unit,
    onNavigateRightPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val singleSlotMs = SLOT_MINUTES * 60_000L
    val timelineWidth = (GuideTiming.SLOT_COUNT * SLOT_WIDTH).toInt()
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()

    val timeSlots = (0 until GuideTiming.SLOT_COUNT).map { GuideTiming.slotTimeMs(windowStart, it) }
    val contentWidth = (timelineWidth + CHANNEL_COLUMN_WIDTH.toInt()).dp

    Column(modifier = modifier) {
        // Timeline Header Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Color(0x66101726), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .horizontalScroll(scrollState)
        ) {
            Row(modifier = Modifier.width(contentWidth).fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .width(CHANNEL_COLUMN_WIDTH.dp)
                        .fillMaxHeight()
                        .padding(start = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "CHANNELS",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                timeSlots.forEach { slotTime ->
                    Box(
                        modifier = Modifier
                            .width(SLOT_WIDTH.dp)
                            .fillMaxHeight()
                            .border(BorderStroke(0.5.dp, Color(0x22FFFFFF)))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = timeFormat.format(Date(slotTime)),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Channel Rows in Timeline format
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.channelId }) { index, channel ->
                val windowEnd = GuideTiming.windowEndMs(now)
                val channelAirings = TabloGuideSynthesizer.resolveAiringsForChannel(channel, airings, windowStart, windowEnd, now)
                val nowLineX = PX_PER_MINUTE * ((now - windowStart) / 60_000L)

                HorizontalGuideRow(
                    index = index,
                    channel = channel,
                    channelAirings = channelAirings,
                    windowStart = windowStart,
                    singleSlotMs = singleSlotMs,
                    nowLineX = nowLineX,
                    contentWidth = contentWidth,
                    scrollState = scrollState,
                    onTune = { onWatchChannel(channel) },
                    onAiringClick = { airing -> onShowDetails(channel, airing) },
                    onRequestTopNav = onRequestTopNav
                )
            }
        }
    }
}

@Composable
private fun HorizontalGuideRow(
    index: Int,
    channel: TabloChannel,
    channelAirings: List<TabloAiring>,
    windowStart: Long,
    singleSlotMs: Long,
    nowLineX: Float,
    contentWidth: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onTune: () -> Unit,
    onAiringClick: (TabloAiring) -> Unit,
    onRequestTopNav: () -> Unit
) {
    var isRowFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .background(if (isRowFocused) Color(0x3300D2B4) else TvSurface)
            .border(BorderStroke(1.dp, if (isRowFocused) TabloTeal.copy(alpha = 0.6f) else TvBorder))
            .horizontalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier.width(contentWidth).fillMaxHeight()
        ) {
            // Channel Header Cell
            ChannelHeaderCell(
                channel = channel,
                isSelected = isRowFocused,
                onTune = onTune,
                modifier = Modifier
                    .width(CHANNEL_COLUMN_WIDTH.dp)
                    .fillMaxHeight()
                    .onFocusChanged { isRowFocused = it.isFocused }
                    .focusable()
            )

            // Airings across timeline
            channelAirings.forEach { airing ->
                val leftPx = timelineLeftPx(airing, windowStart, singleSlotMs)
                val widthPx = timelineWidthPx(airing, windowStart, singleSlotMs)
                val isLive = airing.isLive

                var isAiringFocused by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .offset(x = leftPx.dp)
                        .width(widthPx.dp)
                        .fillMaxHeight()
                        .clickable { onAiringClick(airing) }
                        .onFocusChanged { isAiringFocused = it.isFocused }
                        .focusable()
                        .background(
                            when {
                                isAiringFocused -> TabloTeal.copy(alpha = 0.35f)
                                isLive -> Color(0x331E293B)
                                else -> Color(0x33131A26)
                            },
                            RoundedCornerShape(3.dp)
                        )
                        .border(
                            BorderStroke(
                                if (isAiringFocused) 2.dp else 0.5.dp,
                                if (isAiringFocused) TvFocusHighlight else if (isLive) LiveRed.copy(alpha = 0.6f) else Color(0x22FFFFFF)
                            ),
                            RoundedCornerShape(3.dp)
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (airing.isLive) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(LiveRed, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = airing.title,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = airing.episodeTitle ?: airing.category,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Current Time Red Vertical Indicator Line
            if (nowLineX in 0f..(GuideTiming.SLOT_COUNT * SLOT_WIDTH) && channelAirings.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(x = (CHANNEL_COLUMN_WIDTH + nowLineX).dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(LiveRed.copy(alpha = 0.7f))
                )
            }
        }
    }
}

@Composable
fun ChannelHeaderCell(
    channel: TabloChannel,
    isSelected: Boolean,
    onTune: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(if (isSelected) Color(0x5500D2B4) else TvSurfaceElevated)
            .clickable { onTune() }
            .padding(horizontal = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Color(0x44000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = channel.displayChannel,
                color = if (isSelected) TabloTeal else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = channel.callSign,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = channel.network,
                color = TextSecondary,
                fontSize = 11.sp
            )
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
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Fetching live channel listings from Tablo API...",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Loading OTA broadcasts, FAST streams, and current program guide.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Empty / Retry state when no channels were returned.
 */
@Composable
private fun GuideEmptyState(
    onRetry: () -> Unit,
    onRequestTopNav: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(TvSurface)
                .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(16.dp))
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = TabloTeal,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No Live Channels Found",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ensure your Tablo is powered on and connected to the local network,\nand that an antenna channel scan has completed.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            TvActionButton(
                icon = Icons.Default.Refresh,
                label = "Scan / Retry Fetching from Tablo",
                isPrimary = true,
                onClick = onRetry
            )
        }
    }
}

/**
 * Program detail popup with options to watch fullscreen or assign to any Multiview tile (1-4).
 */
@Composable
fun ProgramActionDialog(
    channel: TabloChannel,
    airing: TabloAiring,
    timeFormat: SimpleDateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) },
    onWatchFullscreen: () -> Unit,
    onAssignToTile: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF20F172A))
                .border(BorderStroke(2.dp, TvFocusHighlight), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TabloTeal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${channel.displayChannel} ${channel.network}",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = airing.title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Time and category
                Text(
                    text = "${timeFormat.format(Date(airing.startTimeMillis))} – ${timeFormat.format(Date(airing.endTimeMillis))} • ${airing.category}",
                    color = TabloTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val desc = airing.description ?: "Live television broadcast airing on ${channel.callSign}."
                Text(
                    text = desc,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action 1: Watch Fullscreen
                Button(
                    onClick = onWatchFullscreen,
                    colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .focusable()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Watch Fullscreen Live", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "OR ASSIGN TO MULTIVIEW TILE (1–4):",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (tile in 0..3) {
                        Button(
                            onClick = { onAssignToTile(tile) },
                            colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .focusable()
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = null,
                                tint = TabloTeal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tile ${tile + 1}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun airingsForChannel(channel: TabloChannel, airings: List<TabloAiring>): List<TabloAiring> =
    airings.filter { it.channelId == channel.channelId }

private fun timelineLeftPx(airing: TabloAiring, windowStart: Long, singleSlotMs: Long): Float {
    require(singleSlotMs > 0)
    val startPx = PX_PER_MINUTE * ((airing.startTimeMillis - windowStart) / 60_000L)
    val x = startPx.coerceIn(0f, (GuideTiming.SLOT_COUNT * SLOT_WIDTH).toFloat())
    return x + CHANNEL_COLUMN_WIDTH
}

private fun timelineWidthPx(airing: TabloAiring, windowStart: Long, singleSlotMs: Long): Float {
    val windowEnd = windowStart + (GuideTiming.SLOT_COUNT * singleSlotMs)
    val startClamped = airing.startTimeMillis.coerceIn(windowStart, windowEnd)
    val endClamped = airing.endTimeMillis.coerceIn(windowStart, windowEnd)
    val widthPx = PX_PER_MINUTE * ((endClamped - startClamped) / 60_000L)
    return widthPx.coerceAtLeast(18f)
}
