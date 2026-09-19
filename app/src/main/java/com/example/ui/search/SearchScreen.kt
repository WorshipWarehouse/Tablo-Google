package com.example.ui.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.ui.components.TvRemoteKeyboard
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

@Composable
fun SearchScreen(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    onSelectChannel: (TabloChannel) -> Unit,
    onBack: () -> Unit,
    onRequestTopNav: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = listOf("ALL", "SPORTS", "MOVIES", "SERIES")

    // Filtered airings and channels
    val filteredResults = remember(searchQuery, selectedCategory, airings, channels) {
        val query = searchQuery.trim().lowercase()
        val allItems = mutableListOf<SearchResultItem>()

        // Add channel results
        channels.forEach { ch ->
            if (query.isEmpty() || ch.callSign.lowercase().contains(query) || ch.network.lowercase().contains(query) || ch.displayChannel.contains(query)) {
                if (selectedCategory == "ALL") {
                    allItems.add(SearchResultItem.ChannelItem(ch))
                }
            }
        }

        // Add airing results
        airings.forEach { airing ->
            val matchCategory = when (selectedCategory) {
                "ALL" -> true
                "SPORTS" -> airing.category.equals("Sports", ignoreCase = true)
                "MOVIES" -> airing.category.equals("Movies", ignoreCase = true) || airing.category.equals("Drama", ignoreCase = true)
                "SERIES" -> airing.category.equals("Series", ignoreCase = true)
                else -> true
            }

            val matchQuery = query.isEmpty() ||
                airing.title.lowercase().contains(query) ||
                (airing.episodeTitle?.lowercase()?.contains(query) == true) ||
                (airing.description?.lowercase()?.contains(query) == true)

            if (matchCategory && matchQuery) {
                val ch = channels.find { it.channelId == airing.channelId }
                allItems.add(SearchResultItem.AiringItem(airing, ch))
            }
        }

        allItems
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 28.dp, vertical = 18.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            onRequestTopNav()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Side: Fire TV On-Screen Remote Keyboard + Query Display
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "SEARCH LIVE TV",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                // Search Input Field Box
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TvSurfaceElevated, RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TabloTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (searchQuery.isEmpty()) "Type using Fire TV remote..." else searchQuery,
                        color = if (searchQuery.isEmpty()) TextMuted else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // On-Screen D-Pad Navigable Keyboard
                TvRemoteKeyboard(
                    onKeyPress = { char -> searchQuery += char },
                    onBackspace = { if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1) },
                    onClear = { searchQuery = "" },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(28.dp))

            // Right Side: Category Filters & Search Results
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Category Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    categories.forEach { cat ->
                        TvCategoryFilterPill(
                            label = cat,
                            isSelected = selectedCategory == cat,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                // Results Counter
                Text(
                    text = "${filteredResults.size} broadcast matches found",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Search scope note
                if (airings.isEmpty()) {
                    Text(
                        text = "No guide data is loaded yet. Search only covers the currently loaded guide window; visit the TV Guide to refresh programming.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Results List
                if (filteredResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No broadcasts match your search",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredResults) { result ->
                            when (result) {
                                is SearchResultItem.AiringItem -> {
                                    SearchAiringResultCard(
                                        airing = result.airing,
                                        channel = result.channel,
                                        onClick = {
                                            if (result.channel != null) {
                                                onSelectChannel(result.channel)
                                            }
                                        }
                                    )
                                }
                                is SearchResultItem.ChannelItem -> {
                                    SearchChannelResultCard(
                                        channel = result.channel,
                                        onClick = { onSelectChannel(result.channel) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class SearchResultItem {
    data class AiringItem(val airing: TabloAiring, val channel: TabloChannel?) : SearchResultItem()
    data class ChannelItem(val channel: TabloChannel) : SearchResultItem()
}

@Composable
fun TvCategoryFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = when {
        isFocused -> TabloTeal
        isSelected -> Color(0x4400D2B4)
        else -> TvSurfaceElevated
    }
    val contentColor = when {
        isFocused -> Color.Black
        isSelected -> TabloTeal
        else -> TextSecondary
    }
    val border = if (isFocused) {
        BorderStroke(2.dp, TvFocusHighlight)
    } else {
        BorderStroke(1.dp, if (isSelected) TabloTeal.copy(alpha = 0.5f) else Color(0x22FFFFFF))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SearchAiringResultCard(
    airing: TabloAiring,
    channel: TabloChannel?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = if (isFocused) TabloTeal else TvSurface
    val titleColor = if (isFocused) Color.Black else TextPrimary
    val subColor = if (isFocused) Color(0xCC000000) else TextSecondary
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(border, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (airing.isLive) {
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            val channelInfo = if (channel != null) "${channel.displayChannel} ${channel.network} • " else ""
            Text(
                text = "$channelInfo${airing.category} • ${airing.rating}",
                color = subColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Tune In",
                tint = if (isFocused) Color.Black else TabloTeal,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Watch",
                color = if (isFocused) Color.Black else TabloTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SearchChannelResultCard(
    channel: TabloChannel,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = if (isFocused) TabloTeal else TvSurface
    val titleColor = if (isFocused) Color.Black else TextPrimary
    val subColor = if (isFocused) Color(0xCC000000) else TextSecondary
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(border, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(if (isFocused) Color.Black else TabloTeal.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = channel.displayChannel,
                    color = if (isFocused) TabloTeal else Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "${channel.callSign} (${channel.network})",
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OTA Live Feed • ${channel.resolution}",
                    color = subColor,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            text = "Tune In",
            color = if (isFocused) Color.Black else TabloTeal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
