package com.example.ui.guide

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GuideTiming
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.ui.theme.LiveRed
import com.example.ui.theme.TabloTeal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val CHANNEL_COLUMN_WIDTH = 180f
private const val SLOT_WIDTH = 170f
private const val SLOT_MINUTES = GuideTiming.SLOT_MINUTES
private const val PX_PER_MINUTE = SLOT_WIDTH / SLOT_MINUTES.toFloat()
private const val ROW_HEIGHT = 68f

@Composable
fun GuideScreen(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    onWatchChannel: (TabloChannel) -> Unit,
    onAssignToTile: (TabloChannel, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowStart = GuideTiming.windowStartMs()
    val now = System.currentTimeMillis()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val singleSlotMs = SLOT_MINUTES * 60_000L
    val timelineWidth = (GuideTiming.SLOT_COUNT * SLOT_WIDTH).toInt()

    var selectedChannelIndex by remember { mutableIntStateOf(0) }
    var selectedSlotIndex by remember {
        mutableIntStateOf(((now - windowStart) / singleSlotMs).toInt().coerceIn(0, GuideTiming.SLOT_COUNT - 1))
    }
    var programDialog by remember { mutableStateOf<Pair<TabloChannel, TabloAiring>?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()

    val timeSlots = (0 until GuideTiming.SLOT_COUNT).map { GuideTiming.slotTimeMs(windowStart, it) }
    val contentWidth = (timelineWidth + CHANNEL_COLUMN_WIDTH.toInt()).dp
    val contentWidthDp = contentWidth.value

    val selectedChannel = channels.getOrNull(selectedChannelIndex)
    val selectedAiring = selectedChannel?.let { ch ->
        airingsForChannel(ch, airings).firstOrNull {
            airingOverlapsSlot(it, windowStart, selectedSlotIndex, singleSlotMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        onBack()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedChannelIndex > 0) {
                            selectedChannelIndex--
                            scope.launch { listState.animateScrollToItem(selectedChannelIndex) }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (channels.isNotEmpty() && selectedChannelIndex < channels.size - 1) {
                            selectedChannelIndex++
                            scope.launch { listState.animateScrollToItem(selectedChannelIndex) }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val dir = if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        selectedSlotIndex = (selectedSlotIndex + dir).coerceIn(0, GuideTiming.SLOT_COUNT - 1)
                        val viewport = contentWidthDp.coerceAtMost(1000f).toInt()
                        val maxScroll = maxOf(0, timelineWidth + CHANNEL_COLUMN_WIDTH.toInt() - viewport)
                        val target = (selectedSlotIndex * SLOT_WIDTH - viewport / 3f).coerceIn(0f, maxScroll.toFloat()).toInt()
                        scope.launch { scrollState.animateScrollTo(target) }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (selectedChannel != null && selectedAiring != null) {
                            programDialog = Pair(selectedChannel, selectedAiring)
                        }
                        true
                    }
                    else -> false
                }
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
                            text = "LIVE • ${timeFormat.format(Date(now))}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "◄ ► scroll time • ▲▼ channels • SELECT for options • BACK to exit",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            if (airings.isEmpty()) {
                Text(
                    text = "No programming loaded yet. Open GUIDE again to retry fetching from the Tablo.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

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

            // Channel Rows
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(channels) { index, channel ->
                    val isChannelSelected = index == selectedChannelIndex
                    val channelAirings = airingsForChannel(channel, airings)
                    val nowLineX = PX_PER_MINUTE * ((now - windowStart) / 60_000L)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT.dp)
                            .background(if (isChannelSelected) Color(0x3300D2B4) else TvSurface)
                            .border(BorderStroke(1.dp, if (isChannelSelected) TabloTeal.copy(alpha = 0.6f) else TvBorder))
                    ) {
                        HorizontalGuideRow(
                            channel = channel,
                            channelAirings = channelAirings,
                            isChannelSelected = isChannelSelected,
                            windowStart = windowStart,
                            singleSlotMs = singleSlotMs,
                            nowLineX = nowLineX,
                            selectedSlotIndex = selectedSlotIndex,
                            contentWidth = contentWidth,
                            scrollState = scrollState,
                            onTune = { onWatchChannel(channel) },
                            onAiringClick = { airing -> programDialog = Pair(channel, airing) }
                        )
                    }
                }
            }
        }

        // Program Action Dialog
        if (programDialog != null) {
            val (channel, airing) = programDialog!!
            ProgramActionDialog(
                channel = channel,
                airing = airing,
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

@Composable
private fun HorizontalGuideRow(
    channel: TabloChannel,
    channelAirings: List<TabloAiring>,
    isChannelSelected: Boolean,
    windowStart: Long,
    singleSlotMs: Long,
    nowLineX: Float,
    selectedSlotIndex: Int,
    contentWidth: androidx.compose.ui.unit.Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onTune: () -> Unit,
    onAiringClick: (TabloAiring) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier.width(contentWidth).fillMaxHeight()
        ) {
            ChannelHeaderCell(
                channel = channel,
                isSelected = isChannelSelected,
                onTune = onTune,
                modifier = Modifier
                    .width(CHANNEL_COLUMN_WIDTH.dp)
                    .fillMaxHeight()
            )

            channelAirings.forEach { airing ->
                val leftPx = timelineLeftPx(airing, windowStart, singleSlotMs)
                val widthPx = timelineWidthPx(airing, windowStart, singleSlotMs)
                val isLive = airing.isLive
                val containsSelection = airingOverlapsSlot(airing, windowStart, selectedSlotIndex, singleSlotMs)

                Box(
                    modifier = Modifier
                        .offset(x = leftPx.dp)
                        .width(widthPx.dp)
                        .fillMaxHeight()
                        .clickable { onAiringClick(airing) }
                        .background(
                            when {
                                containsSelection -> TabloTeal.copy(alpha = 0.30f)
                                isLive -> Color(0x331E293B)
                                else -> Color(0x33131A26)
                            },
                            RoundedCornerShape(3.dp)
                        )
                        .border(
                            BorderStroke(
                                0.5.dp,
                                if (isLive) LiveRed.copy(alpha = 0.6f) else Color(0x22FFFFFF)
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

private fun airingsForChannel(channel: TabloChannel, airings: List<TabloAiring>): List<TabloAiring> =
    airings.filter { it.channelId == channel.channelId }

private fun airingOverlapsSlot(airing: TabloAiring, windowStart: Long, slotIndex: Int, singleSlotMs: Long): Boolean {
    val slotStart = windowStart + (slotIndex * singleSlotMs)
    val slotEnd = slotStart + singleSlotMs
    return airing.endTimeMillis > slotStart && airing.startTimeMillis < slotEnd
}

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