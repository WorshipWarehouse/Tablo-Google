package com.example.ui.multiview

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MultiviewLayoutType
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.playback.MultiviewPlayerManager
import com.example.ui.components.TvPlayerControlsOverlay
import com.example.ui.components.TvVideoTile
import com.example.ui.theme.TabloTeal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvFocusHighlight
import com.example.ui.theme.TvSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MultiviewScreen(
    channels: List<TabloChannel?>,
    airings: List<TabloAiring>,
    playerManager: MultiviewPlayerManager,
    layoutType: MultiviewLayoutType,
    focusedTileIndex: Int,
    allChannels: List<TabloChannel> = emptyList(),
    isPlaying: Boolean = true,
    onFocusChanged: (Int) -> Unit,
    onSelectSolo: (Int) -> Unit,
    onBackFromSolo: () -> Unit,
    onRequestQuickBar: (fromLeft: Boolean) -> Unit,
    onTogglePlayPause: () -> Unit = {},
    onGoToLive: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onSelectLayout: (MultiviewLayoutType) -> Unit = {},
    onAssignChannelToTile: (TabloChannel, Int) -> Unit = { _, _ -> },
    onRemoveChannelFromTile: (Int) -> Unit = {},
    onNavigateLeftPage: () -> Unit = {},
    onNavigateRightPage: () -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var showTileInfo by remember { mutableStateOf(true) }
    var showControlsOverlay by remember { mutableStateOf(false) }

    // Auto-hide lower-third tile info pill after 4 seconds
    LaunchedEffect(focusedTileIndex, layoutType) {
        showTileInfo = true
        delay(4000)
        showTileInfo = false
    }

    // Auto-hide controls overlay after 15 seconds of no interactions
    LaunchedEffect(showControlsOverlay) {
        if (showControlsOverlay) {
            delay(15000)
            showControlsOverlay = false
        }
    }

    val activeChannel = channels.getOrNull(focusedTileIndex)
    val activeAiring = airings.find { it.channelId == activeChannel?.channelId }

    // Explicit D-pad navigation interceptor for Fire TV Remote
    var baseDpadModifier = if (!showControlsOverlay) {
        Modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (layoutType != MultiviewLayoutType.SOLO) {
                                val slotChannel = channels.getOrNull(focusedTileIndex)
                                if (slotChannel == null) {
                                    // Empty tile -> open controls/multiview drawer to pick channel
                                    showControlsOverlay = true
                                } else {
                                    onSelectSolo(focusedTileIndex)
                                }
                                true
                            } else {
                                // In Solo mode, clicking center toggles or shows player controls
                                showControlsOverlay = true
                                true
                            }
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (layoutType == MultiviewLayoutType.SOLO) {
                                onBackFromSolo()
                                true
                            } else {
                                onRequestQuickBar(focusedTileIndex == 0)
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            when (layoutType) {
                                MultiviewLayoutType.GRID_2X2 -> {
                                    if (focusedTileIndex == 2) {
                                        onFocusChanged(0)
                                        true
                                    } else if (focusedTileIndex == 3) {
                                        onFocusChanged(1)
                                        true
                                    } else {
                                        onRequestQuickBar(focusedTileIndex == 0)
                                        true
                                    }
                                }
                                MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                                    if (focusedTileIndex in 2..3) {
                                        onFocusChanged(focusedTileIndex - 1)
                                        true
                                    } else {
                                        onRequestQuickBar(focusedTileIndex == 0)
                                        true
                                    }
                                }
                                MultiviewLayoutType.PRIMARY_1_PLUS_2 -> {
                                    if (focusedTileIndex == 2) {
                                        onFocusChanged(1)
                                        true
                                    } else {
                                        onRequestQuickBar(focusedTileIndex == 0)
                                        true
                                    }
                                }
                                MultiviewLayoutType.VERTICAL_3_UP -> {
                                    onRequestQuickBar(focusedTileIndex == 0)
                                    true
                                }
                                MultiviewLayoutType.HORIZONTAL_2_UP, MultiviewLayoutType.PIP -> {
                                    onRequestQuickBar(focusedTileIndex == 0)
                                    true
                                }
                                MultiviewLayoutType.SOLO -> {
                                    onRequestQuickBar(true)
                                    true
                                }
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (layoutType) {
                            MultiviewLayoutType.GRID_2X2 -> {
                                if (focusedTileIndex == 0) {
                                    onFocusChanged(2)
                                    true
                                } else if (focusedTileIndex == 1) {
                                    onFocusChanged(3)
                                    true
                                } else {
                                    showControlsOverlay = true
                                    true
                                }
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                                if (focusedTileIndex in 1..2) {
                                    onFocusChanged(focusedTileIndex + 1)
                                    true
                                } else {
                                    showControlsOverlay = true
                                    true
                                }
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_2 -> {
                                if (focusedTileIndex == 1) {
                                    onFocusChanged(2)
                                    true
                                } else {
                                    showControlsOverlay = true
                                    true
                                }
                            }
                            MultiviewLayoutType.VERTICAL_3_UP,
                            MultiviewLayoutType.HORIZONTAL_2_UP,
                            MultiviewLayoutType.PIP,
                            MultiviewLayoutType.SOLO -> {
                                showControlsOverlay = true
                                true
                            }
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (layoutType) {
                            MultiviewLayoutType.GRID_2X2 -> {
                                if (focusedTileIndex == 1) {
                                    onFocusChanged(0)
                                    true
                                } else if (focusedTileIndex == 3) {
                                    onFocusChanged(2)
                                    true
                                } else {
                                    onNavigateLeftPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                                if (focusedTileIndex > 0) {
                                    onFocusChanged(0)
                                    true
                                } else {
                                    onNavigateLeftPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_2 -> {
                                if (focusedTileIndex > 0) {
                                    onFocusChanged(0)
                                    true
                                } else {
                                    onNavigateLeftPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.VERTICAL_3_UP -> {
                                if (focusedTileIndex > 0) {
                                    onFocusChanged(focusedTileIndex - 1)
                                    true
                                } else {
                                    onNavigateLeftPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.HORIZONTAL_2_UP, MultiviewLayoutType.PIP -> {
                                if (focusedTileIndex == 1) {
                                    onFocusChanged(0)
                                    true
                                } else {
                                    onNavigateLeftPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.SOLO -> {
                                onNavigateLeftPage()
                                true
                            }
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (layoutType) {
                            MultiviewLayoutType.GRID_2X2 -> {
                                if (focusedTileIndex == 0) {
                                    onFocusChanged(1)
                                    true
                                } else if (focusedTileIndex == 2) {
                                    onFocusChanged(3)
                                    true
                                } else {
                                    onNavigateRightPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                                if (focusedTileIndex == 0) {
                                    onFocusChanged(1)
                                    true
                                } else {
                                    onNavigateRightPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_2 -> {
                                if (focusedTileIndex == 0) {
                                    onFocusChanged(1)
                                    true
                                } else {
                                    onNavigateRightPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.VERTICAL_3_UP -> {
                                if (focusedTileIndex < 2) {
                                    onFocusChanged(focusedTileIndex + 1)
                                    true
                                } else {
                                    onNavigateRightPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.HORIZONTAL_2_UP, MultiviewLayoutType.PIP -> {
                                if (focusedTileIndex == 0) {
                                    onFocusChanged(1)
                                    true
                                } else {
                                    onNavigateRightPage()
                                    true
                                }
                            }
                            MultiviewLayoutType.SOLO -> {
                                onNavigateRightPage()
                                true
                            }
                        }
                    }
                    KeyEvent.KEYCODE_MENU -> {
                        showControlsOverlay = true
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        onTogglePlayPause()
                        true
                    }
                    else -> false
                }
            } else false
        }
    } else {
        Modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                    ) {
                        showControlsOverlay = false
                        true
                    } else false
                } else false
            }
    }

    if (focusRequester != null) {
        baseDpadModifier = baseDpadModifier.focusRequester(focusRequester)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .then(baseDpadModifier)
    ) {
        when (layoutType) {
            MultiviewLayoutType.GRID_2X2 -> {
                // 2x2 Grid (4 channels)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                            RenderTile(
                                tileIndex = 0,
                                channels = channels,
                                airings = airings,
                                playerManager = playerManager,
                                isFocused = focusedTileIndex == 0,
                                onFocus = { onFocusChanged(0) },
                                onSelect = { onSelectSolo(0) },
                                onEmptyClick = {
                                    onFocusChanged(0)
                                    showControlsOverlay = true
                                },
                                showInfo = showTileInfo
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                            RenderTile(
                                tileIndex = 1,
                                channels = channels,
                                airings = airings,
                                playerManager = playerManager,
                                isFocused = focusedTileIndex == 1,
                                onFocus = { onFocusChanged(1) },
                                onSelect = { onSelectSolo(1) },
                                onEmptyClick = {
                                    onFocusChanged(1)
                                    showControlsOverlay = true
                                },
                                showInfo = showTileInfo
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                            RenderTile(
                                tileIndex = 2,
                                channels = channels,
                                airings = airings,
                                playerManager = playerManager,
                                isFocused = focusedTileIndex == 2,
                                onFocus = { onFocusChanged(2) },
                                onSelect = { onSelectSolo(2) },
                                onEmptyClick = {
                                    onFocusChanged(2)
                                    showControlsOverlay = true
                                },
                                showInfo = showTileInfo
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                            RenderTile(
                                tileIndex = 3,
                                channels = channels,
                                airings = airings,
                                playerManager = playerManager,
                                isFocused = focusedTileIndex == 3,
                                onFocus = { onFocusChanged(3) },
                                onSelect = { onSelectSolo(3) },
                                onEmptyClick = {
                                    onFocusChanged(3)
                                    showControlsOverlay = true
                                },
                                showInfo = showTileInfo
                            )
                        }
                    }
                }
            }

            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                // 1+3 Primary Layout:
                val primaryIndex = focusedTileIndex.coerceIn(0, 3)
                val otherIndices = (0 until 4).filter { it != primaryIndex }

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(2.4f)
                            .fillMaxHeight()
                            .padding(2.dp)
                    ) {
                        RenderTile(
                            tileIndex = primaryIndex,
                            channels = channels,
                            airings = airings,
                            playerManager = playerManager,
                            isFocused = true,
                            onFocus = { onFocusChanged(primaryIndex) },
                            onSelect = { onSelectSolo(primaryIndex) },
                            onEmptyClick = {
                                onFocusChanged(primaryIndex)
                                showControlsOverlay = true
                            },
                            showInfo = showTileInfo
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    ) {
                        otherIndices.forEach { secondaryIndex ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(2.dp)
                            ) {
                                RenderTile(
                                    tileIndex = secondaryIndex,
                                    channels = channels,
                                    airings = airings,
                                    playerManager = playerManager,
                                    isFocused = false,
                                    onFocus = { onFocusChanged(secondaryIndex) },
                                    onSelect = { onFocusChanged(secondaryIndex) },
                                    onEmptyClick = {
                                        onFocusChanged(secondaryIndex)
                                        showControlsOverlay = true
                                    },
                                    showInfo = showTileInfo
                                )
                            }
                        }
                    }
                }
            }

            MultiviewLayoutType.PRIMARY_1_PLUS_2 -> {
                // 3 Views (1+2 Primary Layout): 1 large left, 2 stacked right
                val primaryIndex = 0
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(2.3f)
                            .fillMaxHeight()
                            .padding(2.dp)
                    ) {
                        RenderTile(
                            tileIndex = primaryIndex,
                            channels = channels,
                            airings = airings,
                            playerManager = playerManager,
                            isFocused = focusedTileIndex == primaryIndex,
                            onFocus = { onFocusChanged(primaryIndex) },
                            onSelect = { onSelectSolo(primaryIndex) },
                            onEmptyClick = {
                                onFocusChanged(primaryIndex)
                                showControlsOverlay = true
                            },
                            showInfo = showTileInfo
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    ) {
                        listOf(1, 2).forEach { secondaryIndex ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(2.dp)
                            ) {
                                RenderTile(
                                    tileIndex = secondaryIndex,
                                    channels = channels,
                                    airings = airings,
                                    playerManager = playerManager,
                                    isFocused = focusedTileIndex == secondaryIndex,
                                    onFocus = { onFocusChanged(secondaryIndex) },
                                    onSelect = { onSelectSolo(secondaryIndex) },
                                    onEmptyClick = {
                                        onFocusChanged(secondaryIndex)
                                        showControlsOverlay = true
                                    },
                                    showInfo = showTileInfo
                                )
                            }
                        }
                    }
                }
            }

            MultiviewLayoutType.VERTICAL_3_UP -> {
                // 3 Views: 3-Column Vertical Split (33% / 33% / 33%)
                Row(modifier = Modifier.fillMaxSize()) {
                    for (i in 0..2) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                            RenderTile(
                                tileIndex = i,
                                channels = channels,
                                airings = airings,
                                playerManager = playerManager,
                                isFocused = focusedTileIndex == i,
                                onFocus = { onFocusChanged(i) },
                                onSelect = { onSelectSolo(i) },
                                onEmptyClick = {
                                    onFocusChanged(i)
                                    showControlsOverlay = true
                                },
                                showInfo = showTileInfo
                            )
                        }
                    }
                }
            }

            MultiviewLayoutType.PIP -> {
                // Picture-in-Picture: Primary Fullscreen (Tile 0) + Floating PiP (Tile 1) in bottom-right
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main Background Stream (Tile 0)
                    Box(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                        RenderTile(
                            tileIndex = 0,
                            channels = channels,
                            airings = airings,
                            playerManager = playerManager,
                            isFocused = focusedTileIndex == 0,
                            onFocus = { onFocusChanged(0) },
                            onSelect = { onSelectSolo(0) },
                            onEmptyClick = {
                                onFocusChanged(0)
                                showControlsOverlay = true
                            },
                            showInfo = showTileInfo
                        )
                    }

                    // Floating Picture-in-Picture Sub-Stream (Tile 1)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 24.dp)
                            .fillMaxWidth(0.32f)
                            .aspectRatio(16f / 9f)
                    ) {
                        RenderTile(
                            tileIndex = 1,
                            channels = channels,
                            airings = airings,
                            playerManager = playerManager,
                            isFocused = focusedTileIndex == 1,
                            onFocus = { onFocusChanged(1) },
                            onSelect = { onSelectSolo(1) },
                            onEmptyClick = {
                                onFocusChanged(1)
                                showControlsOverlay = true
                            },
                            showInfo = showTileInfo
                        )
                    }
                }
            }

            MultiviewLayoutType.HORIZONTAL_2_UP -> {
                // 2-Up Horizontal Split
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                        RenderTile(
                            tileIndex = 0,
                            channels = channels,
                            airings = airings,
                            playerManager = playerManager,
                            isFocused = focusedTileIndex == 0,
                            onFocus = { onFocusChanged(0) },
                            onSelect = { onSelectSolo(0) },
                            onEmptyClick = {
                                onFocusChanged(0)
                                showControlsOverlay = true
                            },
                            showInfo = showTileInfo
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                        RenderTile(
                            tileIndex = 1,
                            channels = channels,
                            airings = airings,
                            playerManager = playerManager,
                            isFocused = focusedTileIndex == 1,
                            onFocus = { onFocusChanged(1) },
                            onSelect = { onSelectSolo(1) },
                            onEmptyClick = {
                                onFocusChanged(1)
                                showControlsOverlay = true
                            },
                            showInfo = showTileInfo
                        )
                    }
                }
            }

            MultiviewLayoutType.SOLO -> {
                // Solo / Fullscreen Mode (No borders)
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderTile(
                        tileIndex = focusedTileIndex,
                        channels = channels,
                        airings = airings,
                        playerManager = playerManager,
                        isFocused = true,
                        onFocus = { /* already focused */ },
                        onSelect = { showControlsOverlay = !showControlsOverlay },
                        onEmptyClick = { showControlsOverlay = true },
                        showInfo = showTileInfo,
                        showBorder = false
                    )
                }
            }
        }

        // TV Player Controls & Multiview Overlay
        TvPlayerControlsOverlay(
            visible = showControlsOverlay,
            isPlaying = isPlaying,
            activeChannel = activeChannel,
            activeAiring = activeAiring,
            layoutType = layoutType,
            focusedTileIndex = focusedTileIndex,
            channels = allChannels,
            activeMultiviewChannels = channels,
            onTogglePlayPause = onTogglePlayPause,
            onGoToLive = onGoToLive,
            onOpenGuide = onOpenGuide,
            onSelectLayout = onSelectLayout,
            onAssignChannelToTile = onAssignChannelToTile,
            onRemoveChannelFromTile = onRemoveChannelFromTile,
            onSoloTile = onSelectSolo,
            onDismissControls = { showControlsOverlay = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun RenderTile(
    tileIndex: Int,
    channels: List<TabloChannel?>,
    airings: List<TabloAiring>,
    playerManager: MultiviewPlayerManager,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onSelect: () -> Unit,
    onEmptyClick: () -> Unit,
    showInfo: Boolean,
    showBorder: Boolean = true
) {
    val channel = channels.getOrNull(tileIndex)
    if (channel == null) {
        val borderStroke = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvSurface)
                .border(borderStroke, RoundedCornerShape(6.dp))
                .clickable {
                    onFocus()
                    onEmptyClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Channel",
                    tint = if (isFocused) TabloTeal else TextMuted,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "+ Add Channel (Tile ${tileIndex + 1})",
                    color = if (isFocused) TabloTeal else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Press Select to choose channel",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        return
    }

    val airing = airings.find { it.channelId == channel.channelId }
    val player = playerManager.getPlayer(tileIndex)

    TvVideoTile(
        tileIndex = tileIndex,
        channel = channel,
        airing = airing,
        player = player,
        isAudioFocused = isFocused,
        onFocused = onFocus,
        onSelect = onSelect,
        showOverlayInfo = showInfo,
        showBorder = showBorder
    )
}
