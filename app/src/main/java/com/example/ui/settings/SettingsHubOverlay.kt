package com.example.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.MultiviewLayoutType
import com.example.model.SavedMultiviewItem
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.example.ui.theme.*
import com.example.ui.util.safeRequest

enum class SettingsHubTab(val title: String, val icon: ImageVector) {
    SAVED_PRESETS("Saved Multiviews", Icons.Default.Bookmark),
    BUILDER("Multiview Builder", Icons.Default.DashboardCustomize),
    TABLO_DEVICE("Tablo & Tuners", Icons.Default.Router),
    PREFERENCES("Preferences", Icons.Default.Tune)
}

/**
 * Consolidated Settings & Multiview Hub Overlay.
 * Replaces disconnected screens with a unified, high-performance Smart TV modal.
 */
@Composable
fun SettingsHubOverlay(
    tabloDevice: TabloDevice?,
    allChannels: List<TabloChannel>,
    savedMultiviews: List<SavedMultiviewItem>,
    initialTab: SettingsHubTab = SettingsHubTab.SAVED_PRESETS,
    onLaunchMultiview: (SavedMultiviewItem) -> Unit,
    onSaveMultiview: (String, MultiviewLayoutType, List<TabloChannel>) -> Unit,
    onDeleteMultiview: (Long) -> Unit,
    onRenameMultiview: (Long, String) -> Unit,
    onManualConnect: (String, Int) -> Unit = { _, _ -> },
    onLoginAccount: (String, String) -> Unit = { _, _ -> },
    onDisconnect: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(initialTab) }
    val tabFocusRequesters = remember { SettingsHubTab.values().associateWith { FocusRequester() } }
    val contentFocusRequester = remember { FocusRequester() }

    // Pre-populate Builder State
    var builderLayout by remember { mutableStateOf(MultiviewLayoutType.GRID_2X2) }
    var builderSlots by remember { mutableStateOf<List<TabloChannel?>>(listOf(null, null, null, null)) }
    var builderName by remember { mutableStateOf("") }
    var channelPickerSlotIndex by remember { mutableStateOf<Int?>(null) }

    // Edit Preset Name Dialog
    var editingPreset by remember { mutableStateOf<SavedMultiviewItem?>(null) }
    var editNameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        tabFocusRequesters[activeTab]?.safeRequest()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .padding(32.dp)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                            onDismiss()
                            true
                        } else false
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.90f),
                shape = RoundedCornerShape(16.dp),
                color = TvSurface,
                border = BorderStroke(1.dp, TvBorder)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TvSurfaceElevated)
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TabloTeal.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = TabloTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "SETTINGS & MULTIVIEW HUB",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .testTag("btn_close_settings")
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Main Body: Left Sidebar + Right Content Panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Left Sidebar Tabs
                        Column(
                            modifier = Modifier
                                .width(240.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF0F172A))
                                .border(BorderStroke(0.5.dp, TvBorder))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingsHubTab.values().forEach { tab ->
                                val isSelected = tab == activeTab
                                val req = tabFocusRequesters[tab]

                                SettingsTabButton(
                                    tab = tab,
                                    isSelected = isSelected,
                                    focusRequester = req,
                                    onClick = {
                                        activeTab = tab
                                    },
                                    onNavigateRight = {
                                        contentFocusRequester.safeRequest()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Device Info Footer Pill
                            if (tabloDevice != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x2200D2B4),
                                    border = BorderStroke(1.dp, TabloTeal.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(TabloTeal)
                                            )
                                            Text(
                                                text = tabloDevice.name,
                                                color = TabloTeal,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = "${tabloDevice.tunerCount}-Tuner (${tabloDevice.host})",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Right Content Panel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(TvBackground)
                                .padding(24.dp)
                        ) {
                            when (activeTab) {
                                SettingsHubTab.SAVED_PRESETS -> {
                                    SavedPresetsPane(
                                        savedMultiviews = savedMultiviews,
                                        focusRequester = contentFocusRequester,
                                        onLaunch = {
                                            onLaunchMultiview(it)
                                            onDismiss()
                                        },
                                        onCreateNew = {
                                            activeTab = SettingsHubTab.BUILDER
                                        },
                                        onEdit = {
                                            editingPreset = it
                                            editNameText = it.name
                                        },
                                        onDelete = onDeleteMultiview,
                                        onRequestSidebar = {
                                            tabFocusRequesters[activeTab]?.safeRequest()
                                        }
                                    )
                                }
                                SettingsHubTab.BUILDER -> {
                                    MultiviewBuilderPane(
                                        allChannels = allChannels,
                                        selectedLayout = builderLayout,
                                        slots = builderSlots,
                                        presetName = builderName,
                                        focusRequester = contentFocusRequester,
                                        onSelectLayout = { layout ->
                                            builderLayout = layout
                                            builderSlots = (0 until layout.maxChannels).map { idx ->
                                                builderSlots.getOrNull(idx)
                                            }
                                        },
                                        onOpenSlotPicker = { slotIdx ->
                                            channelPickerSlotIndex = slotIdx
                                        },
                                        onClearSlot = { slotIdx ->
                                            builderSlots = builderSlots.mapIndexed { idx, ch ->
                                                if (idx == slotIdx) null else ch
                                            }
                                        },
                                        onNameChange = { builderName = it },
                                        onSaveAndLaunch = {
                                            val validChannels = builderSlots.filterNotNull()
                                            if (validChannels.isNotEmpty()) {
                                                val finalName = builderName.ifBlank { "${builderLayout.displayName} Preset" }
                                                onSaveMultiview(finalName, builderLayout, validChannels)
                                                val newItem = SavedMultiviewItem(
                                                    name = finalName,
                                                    layoutType = builderLayout,
                                                    channels = validChannels,
                                                    preferredAudioChannelId = validChannels.first().channelId
                                                )
                                                onLaunchMultiview(newItem)
                                                onDismiss()
                                            }
                                        },
                                        onRequestSidebar = {
                                            tabFocusRequesters[activeTab]?.safeRequest()
                                        }
                                    )
                                }
                                SettingsHubTab.TABLO_DEVICE -> {
                                    TabloDevicePane(
                                        device = tabloDevice,
                                        focusRequester = contentFocusRequester,
                                        onManualConnect = onManualConnect,
                                        onLoginAccount = onLoginAccount,
                                        onDisconnect = {
                                            onDisconnect()
                                            onDismiss()
                                        },
                                        onRequestSidebar = {
                                            tabFocusRequesters[activeTab]?.safeRequest()
                                        }
                                    )
                                }
                                SettingsHubTab.PREFERENCES -> {
                                    PreferencesPane(
                                        focusRequester = contentFocusRequester,
                                        onRequestSidebar = {
                                            tabFocusRequesters[activeTab]?.safeRequest()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Channel Picker for Builder Slot
    channelPickerSlotIndex?.let { slotIdx ->
        BuilderChannelPickerDialog(
            allChannels = allChannels,
            slotNumber = slotIdx + 1,
            onSelectChannel = { channel ->
                val newSlots = builderSlots.toMutableList()
                while (newSlots.size <= slotIdx) newSlots.add(null)
                newSlots[slotIdx] = channel
                builderSlots = newSlots
                channelPickerSlotIndex = null
            },
            onDismiss = { channelPickerSlotIndex = null }
        )
    }

    // Edit Preset Name Dialog
    editingPreset?.let { preset ->
        EditPresetNameDialog(
            currentName = editNameText,
            onNameChange = { editNameText = it },
            onConfirm = {
                if (editNameText.isNotBlank()) {
                    onRenameMultiview(preset.id, editNameText)
                }
                editingPreset = null
            },
            onDismiss = { editingPreset = null }
        )
    }
}

@Composable
private fun SettingsTabButton(
    tab: SettingsHubTab,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onNavigateRight: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> TabloTeal
        isSelected -> Color(0x3300D2B4)
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused -> Color.Black
        isSelected -> TabloTeal
        else -> TextSecondary
    }

    var mod = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(bg)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .focusable(interactionSource = interactionSource)
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    onNavigateRight()
                    true
                } else false
            } else false
        }
        .padding(horizontal = 12.dp, vertical = 10.dp)

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = mod
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = tab.title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Saved Presets Tab View
 */
@Composable
private fun SavedPresetsPane(
    savedMultiviews: List<SavedMultiviewItem>,
    focusRequester: FocusRequester,
    onLaunch: (SavedMultiviewItem) -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (SavedMultiviewItem) -> Unit,
    onDelete: (Long) -> Unit,
    onRequestSidebar: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Saved Multiview Presets",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quickly launch multi-channel configurations with custom layouts.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onCreateNew,
                colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Preset", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (savedMultiviews.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TvSurfaceElevated)
                    .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No Saved Multiviews Yet",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Build a multi-channel layout in the Multiview Builder or save during live playback.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedMultiviews) { preset ->
                    SavedPresetCard(
                        preset = preset,
                        onLaunch = { onLaunch(preset) },
                        onEdit = { onEdit(preset) },
                        onDelete = { onDelete(preset.id) },
                        onRequestSidebar = onRequestSidebar
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedPresetCard(
    preset: SavedMultiviewItem,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRequestSidebar: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isFocused) Color(0xFF1E293B) else TvSurfaceElevated,
        border = BorderStroke(
            if (isFocused) 2.dp else 1.dp,
            if (isFocused) TvFocusHighlight else TvBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onRequestSidebar()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onLaunch()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Title, Layout Badge, Channel Pills
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = preset.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0x3300D2B4), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = preset.layoutType.displayName,
                            color = TabloTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Channel Pills Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    preset.channels.forEach { ch ->
                        Box(
                            modifier = Modifier
                                .background(TvSurface, RoundedCornerShape(4.dp))
                                .border(BorderStroke(0.5.dp, Color(0x332E3A4E)), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${ch.displayChannel} ${ch.callSign}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Right: Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Launch", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(15.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/**
 * Custom Multiview Builder Pane
 */
@Composable
private fun MultiviewBuilderPane(
    allChannels: List<TabloChannel>,
    selectedLayout: MultiviewLayoutType,
    slots: List<TabloChannel?>,
    presetName: String,
    focusRequester: FocusRequester,
    onSelectLayout: (MultiviewLayoutType) -> Unit,
    onOpenSlotPicker: (Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onSaveAndLaunch: () -> Unit,
    onRequestSidebar: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Step 1: Layout Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "1. Choose Multiview Layout",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    MultiviewLayoutType.HORIZONTAL_2_UP,
                    MultiviewLayoutType.PIP,
                    MultiviewLayoutType.PRIMARY_1_PLUS_2,
                    MultiviewLayoutType.VERTICAL_3_UP,
                    MultiviewLayoutType.GRID_2X2,
                    MultiviewLayoutType.PRIMARY_1_PLUS_3
                ).forEach { layout ->
                    val isSelected = layout == selectedLayout
                    Surface(
                        onClick = { onSelectLayout(layout) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) TabloTeal else TvSurfaceElevated,
                        border = BorderStroke(1.dp, if (isSelected) TvFocusHighlight else TvBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${layout.maxChannels} Streams",
                                color = if (isSelected) Color.Black else TabloTeal,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = layout.displayName,
                                color = if (isSelected) Color.Black else TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Step 2: Channel Slot Assignments
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. Assign Channels to Slots",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                val assignedCount = slots.take(selectedLayout.maxChannels).count { it != null }
                Text(
                    text = "$assignedCount / ${selectedLayout.maxChannels} Assigned",
                    color = if (assignedCount > 0) TabloTeal else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (slotIdx in 0 until selectedLayout.maxChannels) {
                    val channel = slots.getOrNull(slotIdx)
                    BuilderSlotCard(
                        slotIndex = slotIdx,
                        channel = channel,
                        onClick = { onOpenSlotPicker(slotIdx) },
                        onClear = { onClearSlot(slotIdx) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Step 3: Preset Name & Suggestions
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "3. Preset Name",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Sunday Sports Blitz", "Morning News Dual", "Local News Trio", "Prime 4-Way").forEach { suggestion ->
                    Surface(
                        onClick = { onNameChange(suggestion) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x2200D2B4),
                        border = BorderStroke(0.5.dp, TabloTeal.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = suggestion,
                            color = TabloTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvSurfaceElevated)
                    .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = presetName,
                    onValueChange = onNameChange,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(TabloTeal),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (presetName.isEmpty()) {
                            Text("Enter custom preset name...", color = TextMuted, fontSize = 12.sp)
                        }
                        innerTextField()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Step 4: Action Button
        val validChannels = slots.take(selectedLayout.maxChannels).filterNotNull()
        Button(
            onClick = onSaveAndLaunch,
            enabled = validChannels.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = TabloTeal,
                disabledContainerColor = TvSurfaceElevated
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save Preset & Launch Multiview",
                color = if (validChannels.isNotEmpty()) Color.Black else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BuilderSlotCard(
    slotIndex: Int,
    channel: TabloChannel?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isFocused) Color(0xFF1E293B) else TvSurfaceElevated,
        border = BorderStroke(
            if (isFocused) 2.dp else 1.dp,
            if (isFocused) TvFocusHighlight else if (channel != null) TabloTeal.copy(alpha = 0.5f) else TvBorder
        ),
        modifier = modifier
            .height(110.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            if (channel != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Slot ${slotIndex + 1}",
                            color = TabloTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(12.dp))
                        }
                    }

                    Column {
                        Text(
                            text = channel.displayChannel,
                            color = TvFocusHighlight,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = channel.callSign,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = channel.network,
                            color = TextSecondary,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x332E3A4E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = TabloTeal, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Slot ${slotIndex + 1}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Click to Assign",
                        color = TextMuted,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

/**
 * Tablo Device Details & Connection Manager Pane
 */
@Composable
private fun TabloDevicePane(
    device: TabloDevice?,
    focusRequester: FocusRequester,
    onManualConnect: (String, Int) -> Unit,
    onLoginAccount: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onRequestSidebar: () -> Unit
) {
    var ipInput by remember { mutableStateOf(device?.host ?: "") }
    var portInput by remember { mutableStateOf(device?.port?.toString() ?: "8885") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Tablo Device & Tuner Management",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        if (device != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TvSurfaceElevated,
                border = BorderStroke(1.dp, TvBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Text(
                                text = device.name,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Disconnect", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = Color(0x332E3A4E))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("IP Address & Port", color = TextMuted, fontSize = 10.sp)
                            Text("${device.host}:${device.port}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Column {
                            Text("Tuner Hardware", color = TextMuted, fontSize = 10.sp)
                            Text("${device.tunerCount} Live Tuners", color = TabloTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Hardware Model", color = TextMuted, fontSize = 10.sp)
                            Text(device.model.ifBlank { "Tablo DUAL/QUAD" }, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Manual Connection Fallback
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = TvSurfaceElevated,
            border = BorderStroke(1.dp, TvBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Direct IP Connection",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TvSurface)
                            .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = ipInput,
                            onValueChange = { ipInput = it },
                            textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                            cursorBrush = SolidColor(TabloTeal),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (ipInput.isEmpty()) {
                                    Text("Tablo IP (e.g. 192.168.1.50)", color = TextMuted, fontSize = 11.sp)
                                }
                                innerTextField()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            val port = portInput.toIntOrNull() ?: 8885
                            onManualConnect(ipInput, port)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Connect", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * App Preferences Pane
 */
@Composable
private fun PreferencesPane(
    focusRequester: FocusRequester,
    onRequestSidebar: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Application Preferences",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = TvSurfaceElevated,
            border = BorderStroke(1.dp, TvBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Guide Soft Refresh", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Automatically updates program schedules in the background", color = TextSecondary, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0x3300D2B4), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Every 60 Seconds", color = TabloTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = Color(0x332E3A4E))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Audio Focus Crossfade", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Smoothly transition sound when moving D-pad between Multiview tiles", color = TextSecondary, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0x3300D2B4), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Enabled (150ms)", color = TabloTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Slot Channel Picker Dialog for Builder
 */
@Composable
private fun BuilderChannelPickerDialog(
    allChannels: List<TabloChannel>,
    slotNumber: Int,
    onSelectChannel: (TabloChannel) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(allChannels, searchQuery) {
        if (searchQuery.isBlank()) allChannels else {
            allChannels.filter {
                it.callSign.contains(searchQuery, ignoreCase = true) ||
                it.network.contains(searchQuery, ignoreCase = true) ||
                it.displayChannel.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(420.dp)
                .height(480.dp),
            shape = RoundedCornerShape(12.dp),
            color = TvSurface,
            border = BorderStroke(1.dp, TvBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Assign Slot $slotNumber Channel",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                // Search field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvSurfaceElevated)
                        .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 11.sp),
                        cursorBrush = SolidColor(TabloTeal),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Search channels...", color = TextMuted, fontSize = 11.sp)
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Channel List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { channel ->
                        Surface(
                            onClick = { onSelectChannel(channel) },
                            shape = RoundedCornerShape(6.dp),
                            color = TvSurfaceElevated,
                            border = BorderStroke(0.5.dp, TvBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = channel.displayChannel,
                                        color = if (channel.isOtt) TabloTeal else TvFocusHighlight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = channel.callSign,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = channel.network,
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Edit Preset Name Dialog
 */
@Composable
private fun EditPresetNameDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(360.dp),
            shape = RoundedCornerShape(12.dp),
            color = TvSurface,
            border = BorderStroke(1.dp, TvBorder)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Rename Multiview Preset",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvSurfaceElevated)
                        .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = currentName,
                        onValueChange = onNameChange,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(TabloTeal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Save", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
