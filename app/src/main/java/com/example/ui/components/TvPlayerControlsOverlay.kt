package com.example.ui.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MultiviewLayoutType
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.ui.theme.LiveRed
import com.example.ui.theme.TabloTeal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvFocusHighlight
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated

/**
 * TV Player Controls Overlay & Multiview Manager Drawer
 * Emulates the Tablo App + YouTube TV UI and multiviewapp.com user experience:
 * - Prominent Play/Pause button
 * - Go to Live button with live indicator
 * - Multiview button configuring 2 to 4 channels with quick channel picker
 * - TV Guide button
 */
@Composable
fun TvPlayerControlsOverlay(
    visible: Boolean,
    isPlaying: Boolean,
    activeChannel: TabloChannel?,
    activeAiring: TabloAiring?,
    layoutType: MultiviewLayoutType,
    focusedTileIndex: Int,
    channels: List<TabloChannel>,
    activeMultiviewChannels: List<TabloChannel?>,
    onTogglePlayPause: () -> Unit,
    onGoToLive: () -> Unit,
    onOpenGuide: () -> Unit,
    onSelectLayout: (MultiviewLayoutType) -> Unit,
    onAssignChannelToTile: (TabloChannel, Int) -> Unit,
    onRemoveChannelFromTile: (Int) -> Unit,
    onSoloTile: (Int) -> Unit,
    onDismissControls: () -> Unit,
    onSaveLayout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMultiviewDrawer by remember { mutableStateOf(false) }
    var selectedSlotForPicker by remember { mutableStateOf<Int?>(null) }
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            try {
                initialFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            showMultiviewDrawer = false
            selectedSlotForPicker = null
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x70000000),
                            Color(0xEE090D14)
                        ),
                        startY = 0.3f
                    )
                )
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                            if (showMultiviewDrawer) {
                                showMultiviewDrawer = false
                                selectedSlotForPicker = null
                                true
                            } else {
                                onDismissControls()
                                true
                            }
                        } else false
                    } else false
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 40.dp, vertical = 28.dp)
            ) {
                // Program Info & Live Status Badge Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (activeChannel != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TvSurfaceElevated)
                                    .border(BorderStroke(1.dp, TabloTeal.copy(alpha = 0.6f)), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${activeChannel.displayChannel} ${activeChannel.network}",
                                    color = TabloTeal,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                val programTitle = activeAiring?.title ?: "${activeChannel.callSign} Live Stream"
                                Text(
                                    text = programTitle,
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val programDesc = activeAiring?.description ?: "Broadcasting over-the-air with Tablo"
                                Text(
                                    text = programDesc,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = "Select a channel from TV Guide to begin",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Live Status Indicator & Audio Focus
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (layoutType != MultiviewLayoutType.SOLO) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(TvSurfaceElevated)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Active Audio",
                                    tint = TabloTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Audio: Tile ${focusedTileIndex + 1}",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Glowing LIVE Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(LiveRed.copy(alpha = 0.2f))
                                .border(BorderStroke(1.dp, LiveRed.copy(alpha = 0.7f)), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LiveRed)
                            )
                            Text(
                                text = "LIVE",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary TV Playback & Multiview Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. PLAY / PAUSE BUTTON
                    PlayerControlButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = if (isPlaying) "Pause" else "Play",
                        isPrimary = true,
                        modifier = Modifier.focusRequester(initialFocusRequester),
                        testTag = "btn_play_pause",
                        onClick = onTogglePlayPause
                    )

                    // 2. GO TO LIVE BUTTON
                    PlayerControlButton(
                        icon = Icons.Default.LiveTv,
                        label = "Go to Live",
                        testTag = "btn_go_to_live",
                        badge = "LIVE",
                        badgeColor = LiveRed,
                        onClick = onGoToLive
                    )

                    // 3. MULTIVIEW BUTTON (Opens 2-4 Channel Manager)
                    val multiviewSummary = when (layoutType) {
                        MultiviewLayoutType.SOLO -> "Multiview"
                        MultiviewLayoutType.HORIZONTAL_2_UP, MultiviewLayoutType.PIP -> "Multiview (2)"
                        MultiviewLayoutType.PRIMARY_1_PLUS_2, MultiviewLayoutType.VERTICAL_3_UP -> "Multiview (3)"
                        MultiviewLayoutType.PRIMARY_1_PLUS_3, MultiviewLayoutType.GRID_2X2 -> "Multiview (4)"
                    }
                    PlayerControlButton(
                        icon = Icons.Default.GridView,
                        label = multiviewSummary,
                        testTag = "btn_multiview_drawer",
                        isActive = showMultiviewDrawer || layoutType != MultiviewLayoutType.SOLO,
                        onClick = {
                            showMultiviewDrawer = !showMultiviewDrawer
                        }
                    )

                    // 4. TV GUIDE BUTTON
                    PlayerControlButton(
                        icon = Icons.Default.FormatListBulleted,
                        label = "TV Guide",
                        testTag = "btn_tv_guide",
                        onClick = onOpenGuide
                    )

                    // 4.5. SAVE LAYOUT BUTTON
                    PlayerControlButton(
                        icon = Icons.Default.Save,
                        label = "Save Layout",
                        testTag = "btn_save_layout",
                        onClick = onSaveLayout
                    )

                    // 5. SOLO VIEW BUTTON (if currently in multiview)
                    if (layoutType != MultiviewLayoutType.SOLO) {
                        PlayerControlButton(
                            icon = Icons.Default.Fullscreen,
                            label = "Solo View",
                            testTag = "btn_solo_view",
                            onClick = { onSoloTile(focusedTileIndex) }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Close Controls button
                    IconButton(
                        onClick = onDismissControls,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TvSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Controls",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Multiview Setup Panel (YouTube TV + Tablo + MultiviewApp style)
                if (showMultiviewDrawer) {
                    Spacer(modifier = Modifier.height(20.dp))
                    MultiviewConfigPanel(
                        currentLayout = layoutType,
                        activeChannels = activeMultiviewChannels,
                        allChannels = channels,
                        selectedSlotForPicker = selectedSlotForPicker,
                        onSelectLayout = onSelectLayout,
                        onSelectSlotForPicker = { selectedSlotForPicker = it },
                        onAssignChannel = { ch, slot ->
                            onAssignChannelToTile(ch, slot)
                            selectedSlotForPicker = null
                        },
                        onRemoveChannel = onRemoveChannelFromTile,
                        onCloseDrawer = {
                            showMultiviewDrawer = false
                            selectedSlotForPicker = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * Individual Player Control Button with remote focus border & ripple
 */
@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isActive: Boolean = false,
    badge: String? = null,
    badgeColor: Color = TabloTeal
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isFocused -> TabloTeal
        isActive -> TvSurfaceElevated.copy(alpha = 0.9f)
        isPrimary -> TvSurfaceElevated
        else -> TvSurface.copy(alpha = 0.85f)
    }

    val contentColor = when {
        isFocused -> Color.Black
        isActive -> TabloTeal
        else -> TextPrimary
    }

    val borderStroke = when {
        isFocused -> BorderStroke(2.dp, TvFocusHighlight)
        isActive -> BorderStroke(1.dp, TabloTeal)
        else -> BorderStroke(1.dp, TvBorder)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = borderStroke,
        modifier = modifier
            .testTag(testTag)
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )

            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.Medium
            )

            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isFocused) Color.Black.copy(alpha = 0.2f) else badgeColor.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = if (isFocused) Color.Black else badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Multiview Configuration Panel (2-4 Channels)
 * Allows choosing layout mode (1, 2, 3, 4 channels) and assigning/swapping live channels
 */
@Composable
private fun MultiviewConfigPanel(
    currentLayout: MultiviewLayoutType,
    activeChannels: List<TabloChannel?>,
    allChannels: List<TabloChannel>,
    selectedSlotForPicker: Int?,
    onSelectLayout: (MultiviewLayoutType) -> Unit,
    onSelectSlotForPicker: (Int?) -> Unit,
    onAssignChannel: (TabloChannel, Int) -> Unit,
    onRemoveChannel: (Int) -> Unit,
    onCloseDrawer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TvSurface)
            .border(BorderStroke(1.5.dp, TabloTeal.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = TabloTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "MULTIVIEW CONFIGURATION (2–4 CHANNELS)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                IconButton(onClick = onCloseDrawer, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step 1: Select Channel Count / Layout
            Text(
                text = "1. SELECT MULTIVIEW LAYOUT",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LayoutOptionButton(
                    title = "1 View",
                    subtitle = "Solo Fullscreen",
                    isSelected = currentLayout == MultiviewLayoutType.SOLO,
                    onClick = { onSelectLayout(MultiviewLayoutType.SOLO) },
                    modifier = Modifier.weight(1f)
                )
                LayoutOptionButton(
                    title = "2 Views",
                    subtitle = "Side-by-Side Split",
                    isSelected = currentLayout == MultiviewLayoutType.HORIZONTAL_2_UP,
                    onClick = { onSelectLayout(MultiviewLayoutType.HORIZONTAL_2_UP) },
                    modifier = Modifier.weight(1f)
                )
                LayoutOptionButton(
                    title = "3 Views",
                    subtitle = "1 Large + 2 Stacked",
                    isSelected = currentLayout == MultiviewLayoutType.PRIMARY_1_PLUS_2,
                    onClick = { onSelectLayout(MultiviewLayoutType.PRIMARY_1_PLUS_2) },
                    modifier = Modifier.weight(1f)
                )
                LayoutOptionButton(
                    title = "4 Views",
                    subtitle = "2x2 Quad Grid",
                    isSelected = currentLayout == MultiviewLayoutType.GRID_2X2 || currentLayout == MultiviewLayoutType.PRIMARY_1_PLUS_3,
                    onClick = { onSelectLayout(MultiviewLayoutType.GRID_2X2) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Step 2: Tile Slots
            val slotCount = when (currentLayout) {
                MultiviewLayoutType.SOLO -> 1
                MultiviewLayoutType.HORIZONTAL_2_UP, MultiviewLayoutType.PIP -> 2
                MultiviewLayoutType.PRIMARY_1_PLUS_2, MultiviewLayoutType.VERTICAL_3_UP -> 3
                MultiviewLayoutType.PRIMARY_1_PLUS_3, MultiviewLayoutType.GRID_2X2 -> 4
            }

            Text(
                text = "2. CHANNELS IN MULTIVIEW (TILES 1 TO $slotCount)",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (tileIdx in 0 until slotCount) {
                    val assigned = activeChannels.getOrNull(tileIdx)
                    val isEditingThisSlot = selectedSlotForPicker == tileIdx
                    TileSlotCard(
                        tileIndex = tileIdx,
                        channel = assigned,
                        isEditing = isEditingThisSlot,
                        onOpenPicker = {
                            if (isEditingThisSlot) {
                                onSelectSlotForPicker(null)
                            } else {
                                onSelectSlotForPicker(tileIdx)
                            }
                        },
                        onRemove = { onRemoveChannel(tileIdx) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Step 3: Quick Channel Picker Strip (When clicking any slot)
            if (selectedSlotForPicker != null) {
                val targetSlot = selectedSlotForPicker
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "SELECT CHANNEL FOR TILE ${targetSlot + 1}:",
                    color = TabloTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(allChannels) { channel ->
                        ChannelPickerCard(
                            channel = channel,
                            onClick = { onAssignChannel(channel, targetSlot) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutOptionButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = when {
        isFocused -> TabloTeal
        isSelected -> TvSurfaceElevated
        else -> TvBackground
    }

    val border = when {
        isFocused -> BorderStroke(2.dp, TvFocusHighlight)
        isSelected -> BorderStroke(1.5.dp, TabloTeal)
        else -> BorderStroke(1.dp, TvBorder)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = border,
        modifier = modifier
            .height(58.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (isFocused) Color.Black else if (isSelected) TabloTeal else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = if (isFocused) Color.Black.copy(alpha = 0.8f) else TextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TileSlotCard(
    tileIndex: Int,
    channel: TabloChannel?,
    isEditing: Boolean,
    onOpenPicker: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val border = when {
        isFocused -> BorderStroke(2.dp, TvFocusHighlight)
        isEditing -> BorderStroke(1.5.dp, TabloTeal)
        channel != null -> BorderStroke(1.dp, TabloTeal.copy(alpha = 0.4f))
        else -> BorderStroke(1.dp, TvBorder)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) TvSurfaceElevated else TvBackground,
        border = border,
        modifier = modifier
            .height(84.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TILE ${tileIndex + 1}",
                    color = if (channel != null) TabloTeal else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                if (channel != null && tileIndex > 0) {
                    Text(
                        text = "Remove",
                        color = LiveRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onRemove() }
                    )
                }
            }

            if (channel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPicker() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${channel.displayChannel} ${channel.network}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = channel.callSign,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = if (isEditing) "Close" else "Change",
                        color = TabloTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Button(
                    onClick = onOpenPicker,
                    colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceElevated),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                ) {
                    Text(
                        text = if (isEditing) "Selecting..." else "+ Add Channel",
                        color = TabloTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelPickerCard(
    channel: TabloChannel,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) TabloTeal else TvSurfaceElevated,
        border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder),
        modifier = Modifier
            .width(140.dp)
            .height(58.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${channel.displayChannel} ${channel.network}",
                color = if (isFocused) Color.Black else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = channel.callSign,
                color = if (isFocused) Color.Black.copy(alpha = 0.8f) else TextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
