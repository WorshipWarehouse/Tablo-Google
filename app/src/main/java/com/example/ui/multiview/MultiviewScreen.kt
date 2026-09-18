package com.example.ui.multiview

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.example.model.MultiviewLayoutType
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.playback.MultiviewPlayerManager
import com.example.ui.components.TvVideoTile
import com.example.ui.theme.TvBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MultiviewScreen(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    playerManager: MultiviewPlayerManager,
    layoutType: MultiviewLayoutType,
    focusedTileIndex: Int,
    onFocusChanged: (Int) -> Unit,
    onSelectSolo: (Int) -> Unit,
    onBackFromSolo: () -> Unit,
    onRequestQuickBar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showTileInfo by remember { mutableStateOf(true) }

    // Auto-hide lower-third tile info pill after 4 seconds to keep video 100% immersive
    LaunchedEffect(focusedTileIndex, layoutType) {
        showTileInfo = true
        delay(4000)
        showTileInfo = false
    }

    // Explicit D-pad navigation interceptor for Fire TV Remote
    val dpadModifier = Modifier
        .fillMaxSize()
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (layoutType != MultiviewLayoutType.SOLO) {
                            onSelectSolo(focusedTileIndex)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (layoutType == MultiviewLayoutType.SOLO) {
                            onBackFromSolo()
                            true
                        } else {
                            onRequestQuickBar()
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
                                    // At top row, pressing UP brings up the TV navigation bar
                                    onRequestQuickBar()
                                    true
                                }
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                                if (focusedTileIndex > 1) {
                                    onFocusChanged(focusedTileIndex - 1)
                                    true
                                } else {
                                    onRequestQuickBar()
                                    true
                                }
                            }
                            MultiviewLayoutType.HORIZONTAL_2_UP, MultiviewLayoutType.SOLO -> {
                                onRequestQuickBar()
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
                                } else false
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                                if (focusedTileIndex in 1..2) {
                                    onFocusChanged(focusedTileIndex + 1)
                                    true
                                } else false
                            }
                            else -> false
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
                                } else false
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                                if (focusedTileIndex > 0) {
                                    // Move back to Primary large tile
                                    onFocusChanged(0)
                                    true
                                } else false
                            }
                            MultiviewLayoutType.HORIZONTAL_2_UP -> {
                                if (focusedTileIndex == 1) {
                                    onFocusChanged(0)
                                    true
                                } else false
                            }
                            else -> false
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
                                } else false
                            }
                            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                                if (focusedTileIndex == 0) {
                                    // Move from large primary to first small tile
                                    onFocusChanged(1)
                                    true
                                } else false
                            }
                            MultiviewLayoutType.HORIZONTAL_2_UP -> {
                                if (focusedTileIndex == 0) {
                                    onFocusChanged(1)
                                    true
                                } else false
                            }
                            else -> false
                        }
                    }
                    KeyEvent.KEYCODE_MENU -> {
                        onRequestQuickBar()
                        true
                    }
                    else -> false
                }
            } else false
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .then(dpadModifier)
    ) {
        when (layoutType) {
            MultiviewLayoutType.GRID_2X2 -> {
                // 2x2 Grid (4 channels)
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Row: Tile 0, Tile 1
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
                                showInfo = showTileInfo
                            )
                        }
                    }
                    // Bottom Row: Tile 2, Tile 3
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
                                showInfo = showTileInfo
                            )
                        }
                    }
                }
            }

            MultiviewLayoutType.PRIMARY_1_PLUS_3 -> {
                // 1+3 Primary Layout:
                // CRITICAL REQUIREMENT: THE ACTIVE AUDIO STREAM MUST BE THE LARGE PRIMARY STREAM.
                // The focused tile index IS the large tile on the left.
                // The other 3 indices occupy the 3 smaller tiles on the right.
                val primaryIndex = focusedTileIndex
                val otherIndices = (0 until 4).filter { it != primaryIndex }

                Row(modifier = Modifier.fillMaxSize()) {
                    // Large Primary Stream (Left ~68% width)
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
                            showInfo = showTileInfo
                        )
                    }

                    // 3 Stacked Secondary Streams (Right ~32% width)
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
                                    onFocus = {
                                        // When user moves focus to a secondary tile, it immediately becomes the primary large tile!
                                        onFocusChanged(secondaryIndex)
                                    },
                                    onSelect = {
                                        onFocusChanged(secondaryIndex)
                                    },
                                    showInfo = showTileInfo
                                )
                            }
                        }
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
                            showInfo = showTileInfo
                        )
                    }
                }
            }

            MultiviewLayoutType.SOLO -> {
                // Solo / Fullscreen Mode
                Box(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                    RenderTile(
                        tileIndex = focusedTileIndex,
                        channels = channels,
                        airings = airings,
                        playerManager = playerManager,
                        isFocused = true,
                        onFocus = { /* already focused */ },
                        onSelect = { /* already in solo */ },
                        showInfo = showTileInfo
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderTile(
    tileIndex: Int,
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    playerManager: MultiviewPlayerManager,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onSelect: () -> Unit,
    showInfo: Boolean
) {
    val channel = channels.getOrNull(tileIndex) ?: TabloChannel(
        channelId = "ch_$tileIndex",
        callSign = "OTA",
        majorNumber = tileIndex + 1,
        minorNumber = 1,
        network = "Broadcast"
    )
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
        showOverlayInfo = showInfo
    )
}
