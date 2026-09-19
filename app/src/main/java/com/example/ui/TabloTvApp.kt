package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MultiviewLayoutType
import com.example.ui.components.TvQuickBar
import com.example.ui.components.TvScreenSection
import com.example.ui.connect.TabloConnectionScreen
import com.example.ui.guide.GuideScreen
import com.example.ui.login.TabloLoginScreen
import com.example.ui.multiview.MultiviewScreen
import com.example.ui.saved.SaveMultiviewNameDialog
import com.example.ui.saved.SavedMultiviewsScreen
import com.example.ui.search.SearchScreen
import com.example.ui.theme.TvBackground
import com.example.ui.util.safeRequest

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
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val savedMultiviews by viewModel.savedMultiviews.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoadingChannels by viewModel.isLoadingChannels.collectAsState()
    val isLoadingGuide by viewModel.isLoadingGuide.collectAsState()

    var showQuickSaveDialog by remember { mutableStateOf(false) }

    val navFocusRequesters = remember {
        mapOf(
            TvScreenSection.MULTIVIEW to FocusRequester(),
            TvScreenSection.GUIDE to FocusRequester(),
            TvScreenSection.SEARCH to FocusRequester(),
            TvScreenSection.SAVED to FocusRequester(),
            TvScreenSection.TABLO to FocusRequester()
        )
    }
    val contentFocusRequester = remember { FocusRequester() }

    fun handleSectionSelect(section: TvScreenSection) {
        viewModel.setSection(section)
    }

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
            // Authenticated TV Experience
            if (currentSection == TvScreenSection.MULTIVIEW) {
                // Multiview: Full Screen Video Surface with focused D-pad control
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
                    onRequestQuickBar = { fromLeft ->
                        viewModel.showQuickBar()
                        val target = if (fromLeft) TvScreenSection.MULTIVIEW else TvScreenSection.SEARCH
                        navFocusRequesters[target]?.safeRequest()
                    },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onGoToLive = { viewModel.goToLive() },
                    onOpenGuide = { handleSectionSelect(TvScreenSection.GUIDE) },
                    onSelectLayout = { viewModel.setLayout(it) },
                    onAssignChannelToTile = { ch, tile -> viewModel.assignChannelToTile(ch, tile) },
                    onRemoveChannelFromTile = { viewModel.removeChannelFromTile(it) },
                    onNavigateLeftPage = {
                        handleSectionSelect(TvScreenSection.TABLO)
                        navFocusRequesters[TvScreenSection.TABLO]?.safeRequest()
                    },
                    onNavigateRightPage = {
                        handleSectionSelect(TvScreenSection.GUIDE)
                        navFocusRequesters[TvScreenSection.GUIDE]?.safeRequest()
                    },
                    modifier = Modifier.fillMaxSize(),
                    focusRequester = contentFocusRequester
                )

                // Overlaid TV Quick Bar (HUD slides down from top over video)
                TvQuickBar(
                    currentSection = currentSection,
                    currentLayout = currentLayout,
                    tabloDevice = tabloDevice,
                    visible = isQuickBarVisible,
                    onSelectSection = { handleSectionSelect(it) },
                    onSelectLayout = { viewModel.setLayout(it) },
                    onSaveCurrentMultiview = { showQuickSaveDialog = true },
                    navFocusRequesters = navFocusRequesters,
                    onNavigateDown = { fromSection ->
                        viewModel.hideQuickBar()
                        val targetTile = when (fromSection) {
                            TvScreenSection.MULTIVIEW, TvScreenSection.GUIDE -> 0
                            else -> 1
                        }
                        viewModel.setFocusedTile(targetTile)
                        contentFocusRequester.safeRequest()
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            } else {
                // Dedicated Screens (Guide, Search, Saved, Tablo): Permanent Top Nav Bar + Body
                Column(modifier = Modifier.fillMaxSize()) {
                    TvQuickBar(
                        currentSection = currentSection,
                        currentLayout = currentLayout,
                        tabloDevice = tabloDevice,
                        visible = true,
                        onSelectSection = { handleSectionSelect(it) },
                        onSelectLayout = { viewModel.setLayout(it) },
                        onSaveCurrentMultiview = { showQuickSaveDialog = true },
                        navFocusRequesters = navFocusRequesters,
                        onNavigateDown = {
                            contentFocusRequester.safeRequest()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = currentSection,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                            },
                            label = "ScreenTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { section ->
                            when (section) {
                                TvScreenSection.GUIDE -> {
                                    GuideScreen(
                                        channels = channels,
                                        airings = airings,
                                        tabloDevice = tabloDevice,
                                        isLoading = isLoadingChannels || isLoadingGuide,
                                        onRefresh = { viewModel.refreshChannelsAndGuide() },
                                        focusRequester = contentFocusRequester,
                                        onWatchChannel = { viewModel.tuneChannelFullscreen(it) },
                                        onAssignToTile = { ch, tile -> viewModel.assignChannelToTile(ch, tile) },
                                        onBack = { viewModel.setSection(TvScreenSection.MULTIVIEW) },
                                        onRequestTopNav = {
                                            navFocusRequesters[TvScreenSection.GUIDE]?.safeRequest()
                                        },
                                        onNavigateLeftPage = {
                                            handleSectionSelect(TvScreenSection.MULTIVIEW)
                                            navFocusRequesters[TvScreenSection.MULTIVIEW]?.safeRequest()
                                        },
                                        onNavigateRightPage = {
                                            handleSectionSelect(TvScreenSection.SEARCH)
                                            navFocusRequesters[TvScreenSection.SEARCH]?.safeRequest()
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                TvScreenSection.SEARCH -> {
                                    SearchScreen(
                                        channels = channels,
                                        airings = airings,
                                        onSelectChannel = { viewModel.tuneChannelFullscreen(it) },
                                        onBack = { viewModel.setSection(TvScreenSection.MULTIVIEW) },
                                        focusRequester = contentFocusRequester,
                                        onRequestTopNav = {
                                            navFocusRequesters[TvScreenSection.SEARCH]?.safeRequest()
                                        },
                                        onNavigateLeftPage = {
                                            handleSectionSelect(TvScreenSection.GUIDE)
                                            navFocusRequesters[TvScreenSection.GUIDE]?.safeRequest()
                                        },
                                        onNavigateRightPage = {
                                            handleSectionSelect(TvScreenSection.SAVED)
                                            navFocusRequesters[TvScreenSection.SAVED]?.safeRequest()
                                        },
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
                                        focusRequester = contentFocusRequester,
                                        onRequestTopNav = {
                                            navFocusRequesters[TvScreenSection.SAVED]?.safeRequest()
                                        },
                                        onNavigateLeftPage = {
                                            handleSectionSelect(TvScreenSection.SEARCH)
                                            navFocusRequesters[TvScreenSection.SEARCH]?.safeRequest()
                                        },
                                        onNavigateRightPage = {
                                            handleSectionSelect(TvScreenSection.TABLO)
                                            navFocusRequesters[TvScreenSection.TABLO]?.safeRequest()
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                TvScreenSection.TABLO -> {
                                    TabloConnectionScreen(
                                        currentDevice = tabloDevice,
                                        discoveredDevices = discoveredDevices,
                                        isScanning = isScanning,
                                        isConnecting = isConnecting,
                                        isLoggingIn = isLoggingIn,
                                        connectionError = connectionError,
                                        loginError = loginError,
                                        onStartScan = { viewModel.startDiscovery() },
                                        onSelectDevice = { viewModel.selectDevice(it) },
                                        onManualConnect = { ip, port -> viewModel.connectDirectIp(ip, port) },
                                        onLoginAccount = { email, pass -> viewModel.loginTabloAccount(email, pass) },
                                        onDisconnect = { viewModel.disconnect() },
                                        focusRequester = contentFocusRequester,
                                        onBack = {
                                            if (tabloDevice != null) {
                                                viewModel.setSection(TvScreenSection.MULTIVIEW)
                                            }
                                        },
                                        onRequestTopNav = {
                                            navFocusRequesters[TvScreenSection.TABLO]?.safeRequest()
                                        },
                                        onNavigateLeftPage = {
                                            handleSectionSelect(TvScreenSection.SAVED)
                                            navFocusRequesters[TvScreenSection.SAVED]?.safeRequest()
                                        },
                                        onNavigateRightPage = {
                                            handleSectionSelect(TvScreenSection.MULTIVIEW)
                                            navFocusRequesters[TvScreenSection.MULTIVIEW]?.safeRequest()
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                TvScreenSection.MULTIVIEW -> {}
                            }
                        }
                    }
                }
            }
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
