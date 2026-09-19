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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import com.example.ui.util.safeRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun SearchScreen(
    channels: List<TabloChannel>,
    airings: List<TabloAiring>,
    onSelectChannel: (TabloChannel) -> Unit,
    onBack: () -> Unit,
    onRequestTopNav: () -> Unit = {},
    onNavigateLeftPage: () -> Unit = {},
    onNavigateRightPage: () -> Unit = {},
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    val focusManager = LocalFocusManager.current
    val searchInputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequester) {
        if (focusRequester != null) {
            searchInputFocusRequester.safeRequest()
        }
    }

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
            .padding(horizontal = 40.dp, vertical = 20.dp)
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Section: Centered Title, Native Search Text Field & Category Filter Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Native Fire TV Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search broadcasts, shows, or channels...",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontSize = 15.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TabloTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TvFocusHighlight,
                        unfocusedBorderColor = TvBorder,
                        focusedContainerColor = TvSurfaceElevated,
                        unfocusedContainerColor = TvSurface,
                        cursorColor = TabloTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .focusRequester(searchInputFocusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    onRequestTopNav()
                                    true
                                } else false
                            } else false
                        }
                )

                // Category Pills aligned on same row
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    categories.forEach { cat ->
                        TvCategoryFilterPill(
                            label = cat,
                            isSelected = selectedCategory == cat,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }
            }

            // Results Counter & Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredResults.size} broadcast matches found",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                if (airings.isEmpty()) {
                    Text(
                        text = "Visit the TV Guide to load full programming window",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // Results List
            if (filteredResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Type in the search bar above using your Fire TV remote" else "No broadcasts match \"$searchQuery\"",
                            color = TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (airing.isLive) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isFocused) Color.Black else LiveRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = airing.title,
                    color = titleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val channelInfo = if (channel != null) "${channel.displayChannel} ${channel.network} • " else ""
            Text(
                text = "$channelInfo${airing.category} • ${airing.rating}",
                color = subColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Tune In",
                tint = if (isFocused) Color.Black else TabloTeal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Watch",
                color = if (isFocused) Color.Black else TabloTeal,
                fontSize = 14.sp,
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(if (isFocused) Color.Black else TabloTeal.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = channel.displayChannel,
                    color = if (isFocused) TabloTeal else Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "${channel.callSign} (${channel.network})",
                    color = titleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OTA Live Feed • ${channel.resolution}",
                    color = subColor,
                    fontSize = 13.sp
                )
            }
        }

        Text(
            text = "Tune In",
            color = if (isFocused) Color.Black else TabloTeal,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
