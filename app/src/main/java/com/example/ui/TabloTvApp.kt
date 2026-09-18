package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MultiviewLayoutType
import com.example.ui.components.TvQuickBar
import com.example.ui.components.TvScreenSection
import com.example.ui.connect.TabloConnectionScreen
import com.example.ui.guide.GuideScreen
import com.example.ui.multiview.MultiviewScreen
import com.example.ui.saved.SaveMultiviewNameDialog
import com.example.ui.saved.SavedMultiviewsScreen
import com.example.ui.search.SearchScreen
import com.example.ui.theme.TvBackground

@Composable
fun TabloTvApp(
    viewModel: TabloViewModel = viewModel()
) {
    val currentSection by viewModel.currentSection.collectAsState()
    val currentLayout by viewModel.currentLayout.collectAsState()
    val focusedTileIndex by viewModel.focusedTileIndex.collectAsState()
    val isQuickBarVisible by viewModel.isQuickBarVisible.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val airings by viewModel.airings.collectAsState()
    val activeChannels by viewModel.activeMultiviewChannels.collectAsState()
    val tabloDevice by viewModel.tabloDevice.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val savedMultiviews by viewModel.savedMultiviews.collectAsState()

    var showQuickSaveDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        // Main Screen Section
        AnimatedContent(
            targetState = currentSection,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            label = "ScreenTransition",
            modifier = Modifier.fillMaxSize()
        ) { section ->
            when (section) {
                TvScreenSection.MULTIVIEW -> {
                    MultiviewScreen(
                        channels = activeChannels,
                        airings = airings,
                        playerManager = viewModel.playerManager,
                        layoutType = currentLayout,
                        focusedTileIndex = focusedTileIndex,
                        onFocusChanged = { viewModel.setFocusedTile(it) },
                        onSelectSolo = { viewModel.enterSolo(it) },
                        onBackFromSolo = { viewModel.exitSolo() },
                        onRequestQuickBar = { viewModel.showQuickBar() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                TvScreenSection.GUIDE -> {
                    GuideScreen(
                        channels = channels,
                        airings = airings,
                        onWatchChannel = { viewModel.tuneChannelFullscreen(it) },
                        onAssignToTile = { ch, tile -> viewModel.assignChannelToTile(ch, tile) },
                        onBack = { viewModel.setSection(TvScreenSection.MULTIVIEW) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                TvScreenSection.SEARCH -> {
                    SearchScreen(
                        channels = channels,
                        airings = airings,
                        onSelectChannel = { viewModel.tuneChannelFullscreen(it) },
                        onBack = { viewModel.setSection(TvScreenSection.MULTIVIEW) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                TvScreenSection.SAVED -> {
                    SavedMultiviewsScreen(
                        savedItems = savedMultiviews,
                        currentChannels = activeChannels,
                        currentLayout = currentLayout,
                        onLoadMultiview = { viewModel.loadSavedMultiview(it) },
                        onSaveNewMultiview = { name, _, _ -> viewModel.saveCurrentMultiview(name) },
                        onRenameMultiview = { id, name -> viewModel.renameSavedMultiview(id, name) },
                        onDeleteMultiview = { id -> viewModel.deleteSavedMultiview(id) },
                        onBack = { viewModel.setSection(TvScreenSection.MULTIVIEW) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                TvScreenSection.TABLO -> {
                    TabloConnectionScreen(
                        currentDevice = tabloDevice,
                        discoveredDevices = discoveredDevices,
                        isScanning = isScanning,
                        onStartScan = { viewModel.startDiscovery() },
                        onSelectDevice = { viewModel.selectDevice(it) },
                        onManualConnect = { viewModel.connectDirectIp(it) },
                        onBack = { viewModel.setSection(TvScreenSection.MULTIVIEW) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Top Quick Bar HUD (Always present at top layer, auto-hides or stays open during navigation)
        TvQuickBar(
            currentSection = currentSection,
            currentLayout = currentLayout,
            tabloDevice = tabloDevice,
            visible = isQuickBarVisible || currentSection != TvScreenSection.MULTIVIEW,
            onSelectSection = { viewModel.setSection(it) },
            onSelectLayout = { viewModel.setLayout(it) },
            onSaveCurrentMultiview = { showQuickSaveDialog = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Quick Save Multiview Modal
        if (showQuickSaveDialog) {
            val defaultName = "${currentLayout.displayName} Preset"
            SaveMultiviewNameDialog(
                defaultName = defaultName,
                onSave = { name ->
                    showQuickSaveDialog = false
                    viewModel.saveCurrentMultiview(name)
                },
                onDismiss = { showQuickSaveDialog = false }
            )
        }
    }
}
