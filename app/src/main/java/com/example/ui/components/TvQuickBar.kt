package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MultiviewLayoutType
import com.example.model.TabloDevice
import com.example.ui.theme.LiveRed
import com.example.ui.theme.PillBackground
import com.example.ui.theme.TabloTeal
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TvFocusHighlight

enum class TvScreenSection(val label: String, val icon: ImageVector) {
    MULTIVIEW("Multiview", Icons.Default.GridView),
    GUIDE("TV Guide", Icons.Default.LiveTv),
    SEARCH("Search", Icons.Default.Search),
    SAVED("Saved", Icons.Default.Bookmark),
    TABLO("Tablo", Icons.Default.Router)
}

@Composable
fun TvQuickBar(
    currentSection: TvScreenSection,
    currentLayout: MultiviewLayoutType,
    tabloDevice: TabloDevice?,
    visible: Boolean,
    onSelectSection: (TvScreenSection) -> Unit,
    onSelectLayout: (MultiviewLayoutType) -> Unit,
    onSaveCurrentMultiview: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFA0B0F19),
                            Color(0xE60B0F19),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Row: App Brand + Screen Navigation Tabs + Device Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Brand Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(TabloTeal, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TABLO TV",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }

                    // Navigation Tabs (Multiview, Guide, Search, Saved, Tablo)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvScreenSection.values().forEach { section ->
                            TvNavPill(
                                label = section.label,
                                icon = section.icon,
                                isSelected = currentSection == section,
                                onClick = { onSelectSection(section) }
                            )
                        }
                    }

                    // Connected Device & Tuner Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0x331E293B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val deviceName = tabloDevice?.name ?: "Living Room Tablo"
                        val tunersTotal = tabloDevice?.tunerCount ?: 4
                        val tunersInUse = tabloDevice?.activeTuners ?: 0
                        val available = tunersTotal - tunersInUse

                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = TabloTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$deviceName ($available/$tunersTotal Tuners Free)",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Sub-Row (When on Multiview): Layout Selector + Quick Save
                if (currentSection == TvScreenSection.MULTIVIEW) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LAYOUT:",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            listOf(
                                MultiviewLayoutType.GRID_2X2,
                                MultiviewLayoutType.PRIMARY_1_PLUS_3,
                                MultiviewLayoutType.HORIZONTAL_2_UP,
                                MultiviewLayoutType.SOLO
                            ).forEach { layout ->
                                TvLayoutPill(
                                    layout = layout,
                                    isSelected = currentLayout == layout,
                                    onClick = { onSelectLayout(layout) }
                                )
                            }
                        }

                        // Save current multiview button
                        TvNavPill(
                            label = "Save Current Multiview",
                            icon = Icons.Default.Bookmark,
                            isSelected = false,
                            onClick = onSaveCurrentMultiview
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvNavPill(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = when {
        isFocused -> TabloTeal
        isSelected -> Color(0x5500D2B4)
        else -> PillBackground
    }
    val contentColor = when {
        isFocused -> Color.Black
        isSelected -> TabloTeal
        else -> TextPrimary
    }
    val border = when {
        isFocused -> BorderStroke(2.dp, TvFocusHighlight)
        isSelected -> BorderStroke(1.dp, TabloTeal.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, Color(0x22FFFFFF))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(border, RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun TvLayoutPill(
    layout: MultiviewLayoutType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = when {
        isFocused -> TabloTeal
        isSelected -> Color(0x4400D2B4)
        else -> Color(0x331E293B)
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
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(border, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = layout.displayName,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}
