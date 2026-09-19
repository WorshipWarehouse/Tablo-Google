package com.example.ui.saved

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.MultiviewLayoutType
import com.example.model.SavedMultiviewItem
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
fun SavedMultiviewsScreen(
    savedItems: List<SavedMultiviewItem>,
    currentChannels: List<TabloChannel?>,
    currentLayout: MultiviewLayoutType,
    onLoadMultiview: (SavedMultiviewItem) -> Unit,
    onSaveNewMultiview: (String, MultiviewLayoutType, List<TabloChannel>) -> Unit,
    onRenameMultiview: (Long, String) -> Unit,
    onDeleteMultiview: (Long) -> Unit,
    onBack: () -> Unit,
    onRequestTopNav: () -> Unit = {},
    onNavigateLeftPage: () -> Unit = {},
    onNavigateRightPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<SavedMultiviewItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SavedMultiviewItem?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 28.dp, vertical = 20.dp)
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Column {
                    Text(
                        text = "SAVED MULTIVIEWS",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Select any saved multiview to instantly tune in with preferred audio focus",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = { showSaveDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = TabloTeal)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Current Multiview", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            // Grid of Saved Multiview Cards
            if (savedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No saved multiview configurations yet",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure your favorite 2x2 or 1+3 channels and click 'Save Current Multiview'",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(savedItems) { item ->
                        SavedMultiviewCard(
                            item = item,
                            onLaunch = { onLoadMultiview(item) },
                            onRename = { itemToRename = item },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }

        // Save Current Dialog
        if (showSaveDialog) {
            SaveMultiviewNameDialog(
                defaultName = "My Multiview",
                onSave = { name ->
                    showSaveDialog = false
                    onSaveNewMultiview(name, currentLayout, currentChannels.filterNotNull())
                },
                onDismiss = { showSaveDialog = false }
            )
        }

        // Rename Dialog
        if (itemToRename != null) {
            SaveMultiviewNameDialog(
                defaultName = itemToRename!!.name,
                onSave = { newName ->
                    onRenameMultiview(itemToRename!!.id, newName)
                    itemToRename = null
                },
                onDismiss = { itemToRename = null }
            )
        }

        // Delete Confirmation Dialog
        if (itemToDelete != null) {
            Dialog(onDismissRequest = { itemToDelete = null }) {
                Box(
                    modifier = Modifier
                        .width(420.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF2101726))
                        .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(12.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Delete '${itemToDelete!!.name}'?",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "This multiview preset will be removed from your saved list.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { itemToDelete = null },
                                colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceElevated),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = TextPrimary)
                            }
                            Button(
                                onClick = {
                                    onDeleteMultiview(itemToDelete!!.id)
                                    itemToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LiveRed),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedMultiviewCard(
    item: SavedMultiviewItem,
    onLaunch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = if (isFocused) Color(0xFF1E293B) else TvSurface
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(border, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onLaunch)
            .focusable(interactionSource = interactionSource)
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = item.name,
                    color = if (isFocused) TabloTeal else TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${item.layoutType.displayName} • ${item.channels.size} Channels",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Channel Pills Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item.channels.forEach { ch ->
                Box(
                    modifier = Modifier
                        .background(Color(0x440F172A), RoundedCornerShape(6.dp))
                        .border(BorderStroke(0.5.dp, Color(0x33FFFFFF)), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${ch.displayChannel} ${ch.network}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Launch Action Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(if (isFocused) TabloTeal else Color(0x3300D2B4), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isFocused) Color.Black else TabloTeal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Watch Preset",
                color = if (isFocused) Color.Black else TabloTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SaveMultiviewNameDialog(
    defaultName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF20F172A))
                .border(BorderStroke(1.5.dp, TabloTeal.copy(alpha = 0.8f)), RoundedCornerShape(14.dp))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Name Your Multiview",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                val focusManager = LocalFocusManager.current

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Sunday Football", color = TextMuted.copy(alpha = 0.6f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (name.isNotBlank()) onSave(name.trim())
                        }
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
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick preset suggestions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Sunday Football", "Local News", "Sports Night", "Primetime").forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x331E293B))
                                .clickable { name = suggestion }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(suggestion, color = TabloTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceElevated),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextPrimary)
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (name.isNotBlank()) onSave(name.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Multiview", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
