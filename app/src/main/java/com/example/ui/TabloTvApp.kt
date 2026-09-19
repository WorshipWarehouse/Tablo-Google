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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MultiviewLayoutType
import com.example.ui.components.TvScreenSection
import com.example.ui.guide.GuideScreen
import com.example.ui.login.TabloLoginScreen
import com.example.ui.multiview.MultiviewScreen
import com.example.ui.saved.SaveMultiviewNameDialog
import com.example.ui.settings.SettingsHubOverlay
import com.example.ui.theme.TvBackground
import com.example.ui.util.safeRequest

@Composable
fun TabloTvApp(
    viewModel: TabloViewModel = viewModel()
) {
    val currentSection by viewModel.currentSection.collectAsState()
    val currentLayout by viewModel.currentLayout.collectAsState()
    val focusedTileIndex by viewModel.focusedTileIndex.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val airings by viewModel.airings.collectAsState()
    val activeChannels by viewModel.activeMultiviewChannels.collectAsState()
    val tabloDevice by viewModel.tabloDevice.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val savedMultiviews by viewModel.savedMultiviews.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoadingChannels by viewModel.isLoadingChannels.collectAsState()
    val isLoadingGuide by viewModel.isLoadingGuide.collectAsState()
    val focusedEpgTimeMs by viewModel.focusedEpgTimeMs.collectAsState()

    var showSettingsHub by remember { mutableStateOf(false) }
    var showQuickSaveDialog by remember { mutableStateOf(false) }

    val guideFocusRequester = remember { FocusRequester() }
    val playerFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        // GATING REQUIREMENT: User must login with Tablo account before accessing content
        if (tabloDevice == null) {
            TabloLoginScreen(
                isLoggingIn = isLoggingIn || isConnecting || isScanning,
                loginError = loginError ?: connectionError,
                discoveredDevices = discoveredDevices,
                onLogin = { email, pass -> viewModel.loginTabloAccount(email, pass) },
                onSelectDevice = { viewModel.selectDevice(it) },
                onLocalFallback = { viewModel.startDiscovery() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Main Application Layout: TV Guide is the Primary Landing Screen
            AnimatedContent(
                targetState = currentSection,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "MainAppTransition",
                modifier = Modifier.fillMaxSize()
            ) { section ->
                when (section) {
                    TvScreenSection.MULTIVIEW -> {
                        // Multiview / Live Video Surface with In-Player Action HUD & Mini Drawer
                        MultiviewScreen(
                            channels = activeChannels,
                            airings = airings,
                            playerManager = viewModel.playerManager,
                            layoutType = currentLayout,
                            focusedTileIndex = focusedTileIndex,
                            allChannels = channels,
                            isPlaying = isPlaying,
                            onFocusChanged = { viewModel.setFocusedTile(it) },
                            onSelectSolo = { viewModel.enterSolo(it) },
                            onBackFromSolo = { viewModel.exitSolo() },
                            onRequestQuickBar = {
                                // Toggle Settings Hub on quick action
                                showSettingsHub = true
                            },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onGoToLive = { viewModel.goToLive() },
                            onOpenGuide = { viewModel.setSection(TvScreenSection.GUIDE) },
                            onSelectLayout = { viewModel.setLayout(it) },
                            onAssignChannelToTile = { ch, tile -> viewModel.assignChannelToTile(ch, tile) },
                            onRemoveChannelFromTile = { viewModel.removeChannelFromTile(it) },
                            onNavigateLeftPage = { viewModel.setSection(TvScreenSection.GUIDE) },
                            onNavigateRightPage = { viewModel.setSection(TvScreenSection.GUIDE) },
                            modifier = Modifier.fillMaxSize(),
                            focusRequester = playerFocusRequester
                        )
                    }

                    TvScreenSection.GUIDE,
                    TvScreenSection.SEARCH,
                    TvScreenSection.SAVED,
                    TvScreenSection.TABLO -> {
                        // Unified Primary TV Guide Landing Page with Integrated Search & Settings Hub
                        GuideScreen(
                            channels = channels,
                            airings = airings,
                            tabloDevice = tabloDevice,
                            isLoading = isLoadingChannels || isLoadingGuide,
                            onRefresh = { viewModel.refreshChannelsAndGuide() },
                            onOpenSettings = { showSettingsHub = true },
                            focusRequester = guideFocusRequester,
                            onWatchChannel = { viewModel.tuneChannelFullscreen(it) },
                            onAssignToTile = { ch, tile -> viewModel.assignChannelToTile(ch, tile) },
                            focusedEpgTimeMs = focusedEpgTimeMs,
                            onUpdateFocusedEpgTime = { viewModel.updateFocusedEpgTime(it) },
                            onBack = {
                                // If already watching video in background, back returns to video; otherwise soft refresh
                                if (activeChannels.any { it != null }) {
                                    viewModel.setSection(TvScreenSection.MULTIVIEW)
                                }
                            },
                            onRequestTopNav = {
                                showSettingsHub = true
                            },
                            onNavigateLeftPage = { /* Single primary page */ },
                            onNavigateRightPage = { /* Single primary page */ },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Consolidated Settings & Multiview Hub Overlay Modal
        if (showSettingsHub) {
            SettingsHubOverlay(
                tabloDevice = tabloDevice,
                allChannels = channels,
                savedMultiviews = savedMultiviews,
                onDismiss = { showSettingsHub = false },
                onLaunchMultiview = { item ->
                    showSettingsHub = false
                    viewModel.loadSavedMultiview(item)
                },
                onSaveMultiview = { name, layout, chList ->
                    viewModel.saveCustomMultiview(name, layout, chList)
                },
                onDeleteMultiview = { id ->
                    viewModel.deleteSavedMultiview(id)
                },
                onRenameMultiview = { id, name ->
                    viewModel.renameSavedMultiview(id, name)
                },
                onManualConnect = { ip, port -> viewModel.connectDirectIp(ip, port) },
                onLoginAccount = { email, pass -> viewModel.loginTabloAccount(email, pass) },
                onDisconnect = {
                    showSettingsHub = false
                    viewModel.disconnect()
                }
            )
        }

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
