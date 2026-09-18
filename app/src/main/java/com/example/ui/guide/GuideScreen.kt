package com.example.ui.guide

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GuideScreen(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    onWatchChannel: (TabloChannel) -> Unit,
    onAssignToTile: (TabloChannel, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedChannelIndex by remember { mutableIntStateOf(0) }
    var selectedProgramAiring by remember { mutableStateOf<Pair<TabloChannel, TabloAiring>?>(null) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val currentTime = System.currentTimeMillis()
    val halfHour = 30 * 60 * 1000L
    val startSlotTime = (currentTime / halfHour) * halfHour
    val timeSlots = listOf(
        startSlotTime,
        startSlotTime + halfHour,
        startSlotTime + 2 * halfHour,
        startSlotTime + 3 * halfHour,
        startSlotTime + 4 * halfHour
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (selectedChannelIndex > 0) {
                                selectedChannelIndex--
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (selectedChannelIndex < channels.size - 1) {
                                selectedChannelIndex++
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Guide Header: Title + Current Live Time Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LIVE TV GUIDE",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0x33EF4444), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(LiveRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE • ${timeFormat.format(Date(currentTime))}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Press SELECT to watch or add to multiview • BACK to exit",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // Timeline Header Row (Time Slots)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color(0x66101726), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                // Channel Column Space
                Box(
                    modifier = Modifier
                        .width(180.dp)
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

                // Time Slots Headers
                timeSlots.forEach { slotTime ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
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

            // Channel Rows with Program Blocks
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(channels) { index, channel ->
                    val isChannelSelected = index == selectedChannelIndex
                    val channelAirings = airings.filter { it.channelId == channel.channelId }
                    val currentAiring = channelAirings.firstOrNull() ?: TabloAiring(
                        airingId = "mock_${channel.channelId}",
                        channelId = channel.channelId,
                        title = "${channel.network} Primetime Live",
                        startTimeMillis = startSlotTime,
                        durationSeconds = 3600L,
                        category = "General"
                    )

                    val secondaryAiring = channelAirings.getOrNull(1) ?: TabloAiring(
                        airingId = "mock2_${channel.channelId}",
                        channelId = channel.channelId,
                        title = "${channel.callSign} Late Night",
                        startTimeMillis = startSlotTime + 3600L * 1000L,
                        durationSeconds = 3600L,
                        category = "News"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .background(if (isChannelSelected) Color(0x3300D2B4) else TvSurface)
                            .border(BorderStroke(1.dp, if (isChannelSelected) TabloTeal.copy(alpha = 0.6f) else TvBorder))
                    ) {
                        // Left Channel Cell
                        ChannelHeaderCell(
                            channel = channel,
                            isSelected = isChannelSelected,
                            onTune = { onWatchChannel(channel) },
                            modifier = Modifier.width(180.dp).fillMaxHeight()
                        )

                        // Programs Grid for this Channel
                        // Airing 1 (Current)
                        ProgramGridCell(
                            airing = currentAiring,
                            isLive = true,
                            onSelect = { selectedProgramAiring = Pair(channel, currentAiring) },
                            modifier = Modifier.weight(2.2f).fillMaxHeight()
                        )

                        // Airing 2 (Upcoming)
                        ProgramGridCell(
                            airing = secondaryAiring,
                            isLive = false,
                            onSelect = { selectedProgramAiring = Pair(channel, secondaryAiring) },
                            modifier = Modifier.weight(1.8f).fillMaxHeight()
                        )
                    }
                }
            }
        }

        // Program Action Dialog
        if (selectedProgramAiring != null) {
            val (channel, airing) = selectedProgramAiring!!
            ProgramActionDialog(
                channel = channel,
                airing = airing,
                onWatchFullscreen = {
                    selectedProgramAiring = null
                    onWatchChannel(channel)
                },
                onAssignToTile = { tile ->
                    selectedProgramAiring = null
                    onAssignToTile(channel, tile)
                },
                onDismiss = { selectedProgramAiring = null }
            )
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

@Composable
fun ProgramGridCell(
    airing: TabloAiring,
    isLive: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = when {
        isFocused -> TabloTeal
        isLive -> Color(0x331E293B)
        else -> Color(0x22131A26)
    }
    val titleColor = if (isFocused) Color.Black else TextPrimary
    val subColor = if (isFocused) Color(0xCC000000) else TextSecondary
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(0.5.dp, Color(0x22FFFFFF))

    Box(
        modifier = modifier
            .border(border)
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelect)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isFocused) Color.Black else LiveRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = airing.title,
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = airing.episodeTitle ?: airing.category,
                color = subColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProgramActionDialog(
    channel: TabloChannel,
    airing: TabloAiring,
    onWatchFullscreen: () -> Unit,
    onAssignToTile: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF20F172A))
                .border(BorderStroke(1.5.dp, TabloTeal.copy(alpha = 0.7f)), RoundedCornerShape(14.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(TabloTeal, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${channel.displayChannel} ${channel.network}",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = airing.title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val desc = airing.description ?: "Live television broadcast on ${channel.callSign}."
                Text(
                    text = desc,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Watch Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onWatchFullscreen,
                        colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch Fullscreen", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "OR ASSIGN TO MULTIVIEW TILE:",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = TabloTeal, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tile ${tile + 1}", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
