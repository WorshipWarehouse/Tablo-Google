package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TabloRepository
import com.example.data.local.AppDatabase
import com.example.data.local.SavedMultiviewRepository
import com.example.data.local.TabloDeviceRepository
import com.example.model.GuideTiming
import com.example.model.MultiviewLayoutType
import com.example.model.SavedMultiviewItem
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.example.model.TabloResult
import com.example.playback.MultiviewPlayerManager
import com.example.ui.components.TvScreenSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TabloViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val savedRepository = SavedMultiviewRepository(database.savedMultiviewDao())
    private val deviceRepository = TabloDeviceRepository(database.tabloDeviceDao())
    private val favoriteRepository = com.example.data.local.FavoriteChannelRepository(database.favoriteChannelDao())
    private val tabloRepository = TabloRepository()

    val playerManager = MultiviewPlayerManager(application) { tileIndex, _ ->
        _tileErrors.value = _tileErrors.value + (tileIndex to STREAM_ERROR_MESSAGE)
    }

    val savedMultiviews: StateFlow<List<SavedMultiviewItem>> = savedRepository.savedMultiviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSection = MutableStateFlow(TvScreenSection.TABLO)
    val currentSection: StateFlow<TvScreenSection> = _currentSection.asStateFlow()

    private val _currentLayout = MutableStateFlow(MultiviewLayoutType.GRID_2X2)
    val currentLayout: StateFlow<MultiviewLayoutType> = _currentLayout.asStateFlow()

    private var previousLayoutBeforeSolo: MultiviewLayoutType = MultiviewLayoutType.GRID_2X2

    private val _focusedTileIndex = MutableStateFlow(0)
    val focusedTileIndex: StateFlow<Int> = _focusedTileIndex.asStateFlow()

    private val _isQuickBarVisible = MutableStateFlow(false)
    val isQuickBarVisible: StateFlow<Boolean> = _isQuickBarVisible.asStateFlow()

    val favoriteChannelIds: StateFlow<Set<String>> = favoriteRepository.favoriteChannelIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _channels = MutableStateFlow<List<TabloChannel>>(emptyList())
    val channels: StateFlow<List<TabloChannel>> = combine(_channels, favoriteChannelIds) { list, favs ->
        val (favorites, rest) = list.partition { favs.contains(it.channelId) }
        favorites + rest
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _airings = MutableStateFlow<List<TabloAiring>>(emptyList())
    val airings: StateFlow<List<TabloAiring>> = _airings.asStateFlow()

    private val _activeMultiviewChannels = MutableStateFlow<List<TabloChannel?>>(emptyTileSlots())
    val activeMultiviewChannels: StateFlow<List<TabloChannel?>> = _activeMultiviewChannels.asStateFlow()

    private val _tabloDevice = MutableStateFlow<TabloDevice?>(null)
    val tabloDevice: StateFlow<TabloDevice?> = _tabloDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TabloDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TabloDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoadingChannels = MutableStateFlow(false)
    val isLoadingChannels: StateFlow<Boolean> = _isLoadingChannels.asStateFlow()

    private val _isLoadingGuide = MutableStateFlow(false)
    val isLoadingGuide: StateFlow<Boolean> = _isLoadingGuide.asStateFlow()

    private val _channelError = MutableStateFlow<String?>(null)
    val channelError: StateFlow<String?> = _channelError.asStateFlow()

    private val _guideError = MutableStateFlow<String?>(null)
    val guideError: StateFlow<String?> = _guideError.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _tileErrors = MutableStateFlow<Map<Int, String>>(emptyMap())
    val tileErrors: StateFlow<Map<Int, String>> = _tileErrors.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var autoHideJob: Job? = null
    private var lastManualIp: String? = null
    private var dataLoadJob: Job? = null

    init {
        viewModelScope.launch {
            val savedDevice = deviceRepository.load()
            if (savedDevice != null) {
                _tabloDevice.value = savedDevice
                _currentSection.value = TvScreenSection.GUIDE
                loadChannelsAndGuide(savedDevice)
            } else {
                _currentSection.value = TvScreenSection.TABLO
            }
        }
    }

    private fun loadChannelsAndGuide(device: TabloDevice) {
        dataLoadJob?.cancel()
        dataLoadJob = viewModelScope.launch {
            _isLoadingChannels.value = true
            _channelError.value = null
            _guideError.value = null
            try {
                when (val result = tabloRepository.fetchLiveChannels(device)) {
                    is TabloResult.Error -> {
                        _channelError.value = result.message
                        _connectionError.value = result.message
                        _isLoadingChannels.value = false
                        _currentSection.value = TvScreenSection.TABLO
                    }
                    is TabloResult.Success -> {
                        _channels.value = result.data
                        _channelError.value = null
                        _connectionError.value = null
                        _isLoadingChannels.value = false

                        viewModelScope.launch {
                            val (active, total) = tabloRepository.fetchTunerStatus(device)
                            _tabloDevice.value = _tabloDevice.value?.copy(
                                activeTuners = active,
                                tunerCount = total,
                                isConnected = true
                            )
                        }

                        // Requirement: Do not fill in the multiview by default.
                        // Multiview starts empty; user picks initial channel in TV Guide.
                        _activeMultiviewChannels.value = emptyTileSlots()
                        _currentLayout.value = MultiviewLayoutType.SOLO

                        loadGuide(device, result.data)
                    }
                }
            } catch (e: Exception) {
                Log.e("TabloViewModel", "Load data failed: ${e.message}")
                _connectionError.value = "The Tablo did not respond. Check that it is powered on and connected."
                _isLoadingChannels.value = false
                _currentSection.value = TvScreenSection.TABLO
            }
        }
    }

    private fun loadGuide(device: TabloDevice, channels: List<TabloChannel>) {
        viewModelScope.launch {
            _isLoadingGuide.value = true
            _guideError.value = null
            val now = System.currentTimeMillis()
            val windowStart = GuideTiming.windowStartMs(now)
            val windowEnd = GuideTiming.windowEndMs(now)
            when (val result = tabloRepository.fetchGuideAirings(
                device,
                channels.map { it.channelId },
                windowStart,
                windowEnd
            )) {
                is TabloResult.Error -> {
                    _guideError.value = result.message
                    _airings.value = emptyList()
                }
                is TabloResult.Success -> {
                    _airings.value = result.data
                    _guideError.value = null
                }
            }
            _isLoadingGuide.value = false
        }
    }

    private val _focusedEpgTimeMs = MutableStateFlow<Long>(System.currentTimeMillis())
    val focusedEpgTimeMs: StateFlow<Long> = _focusedEpgTimeMs.asStateFlow()

    fun updateFocusedEpgTime(timeMs: Long) {
        _focusedEpgTimeMs.value = timeMs
    }

    private val sessionTokens = mutableMapOf<Int, String>()
    private val keepaliveJobs = mutableMapOf<Int, Job>()
    private val tuningJobs = mutableMapOf<Int, Job>()

    private fun playChannelInTile(channel: TabloChannel, tileIndex: Int) {
        val device = _tabloDevice.value ?: return
        val slots = _activeMultiviewChannels.value.toMutableList()
        while (slots.size <= tileIndex) slots.add(null)
        slots[tileIndex] = channel
        _activeMultiviewChannels.value = slots

        _tileErrors.value = _tileErrors.value - tileIndex

        // Cancel previous tuning job on this tile to ensure exactly one /watch per tile per tune
        tuningJobs[tileIndex]?.cancel()

        tuningJobs[tileIndex] = viewModelScope.launch {
            // Cancel current keepalive for old session immediately
            keepaliveJobs[tileIndex]?.cancel()
            keepaliveJobs.remove(tileIndex)

            // Extract old token to delete later
            val oldToken = sessionTokens[tileIndex]
            sessionTokens.remove(tileIndex)

            val sessionResult = tabloRepository.fetchWatchStreamSession(device, channel)
            val url = sessionResult?.playlistUrl
            val token = sessionResult?.sessionToken
            val keepaliveSec = sessionResult?.keepaliveSeconds ?: 10L

            if (url.isNullOrEmpty()) {
                _tileErrors.value = _tileErrors.value + (tileIndex to STREAM_ERROR_MESSAGE)
                // Delete old session token asynchronously even if new tune fails
                if (!oldToken.isNullOrBlank()) {
                    launch {
                        delay(6000L) // safe delay, ensures ExoPlayer is not starting up on it
                        try {
                            tabloRepository.deleteSession(device, oldToken)
                        } catch (e: Exception) {
                            Log.w("TabloViewModel", "Error deleting old session $oldToken asynchronously: ${e.message}")
                        }
                    }
                }
            } else {
                if (!token.isNullOrBlank()) {
                    sessionTokens[tileIndex] = token
                }
                playerManager.playChannel(tileIndex, channel, url)
                if (!token.isNullOrBlank()) {
                    startKeepaliveJob(device, tileIndex, token, keepaliveSec)
                }

                // Delete old session token asynchronously
                if (!oldToken.isNullOrBlank() && oldToken != token) {
                    launch {
                        delay(6000L) // Safe delay to make sure ExoPlayer isn't starting up on the old session
                        try {
                            tabloRepository.deleteSession(device, oldToken)
                        } catch (e: Exception) {
                            Log.w("TabloViewModel", "Error deleting old session $oldToken asynchronously: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun startKeepaliveJob(device: TabloDevice, tileIndex: Int, token: String, keepaliveSec: Long) {
        keepaliveJobs[tileIndex]?.cancel()
        val delayMs = (keepaliveSec.coerceAtLeast(5L) * 1000L)
        keepaliveJobs[tileIndex] = viewModelScope.launch {
            while (true) {
                delay(delayMs)
                tabloRepository.sendKeepalive(device, token)
            }
        }
    }

    private suspend fun stopTileSession(tileIndex: Int) {
        tuningJobs[tileIndex]?.cancel()
        tuningJobs.remove(tileIndex)
        keepaliveJobs[tileIndex]?.cancel()
        keepaliveJobs.remove(tileIndex)
        val token = sessionTokens.remove(tileIndex)
        val device = _tabloDevice.value
        if (device != null && !token.isNullOrBlank()) {
            // Do not DELETE a session while ExoPlayer is still starting up on it.
            // When explicitly stopping a tile or releasing, we perform deletion asynchronously after a safe delay.
            viewModelScope.launch {
                delay(4000L) // Wait a short period to make sure the player has stopped referencing it
                try {
                    tabloRepository.deleteSession(device, token)
                } catch (e: Exception) {
                    Log.w("TabloViewModel", "Error deleting session $token for tile $tileIndex: ${e.message}")
                }
            }
        }
    }

    fun setFocusedTile(index: Int) {
        if (index in 0..3) {
            _focusedTileIndex.value = index
            playerManager.setAudioTile(index)
        }
    }

    fun setLayout(layout: MultiviewLayoutType) {
        if (_currentLayout.value != MultiviewLayoutType.SOLO) {
            previousLayoutBeforeSolo = _currentLayout.value
        }
        _currentLayout.value = layout
        resetAutoHideQuickBar()
    }

    fun enterSolo(tileIndex: Int) {
        if (_currentLayout.value != MultiviewLayoutType.SOLO) {
            previousLayoutBeforeSolo = _currentLayout.value
        }
        setFocusedTile(tileIndex)
        _currentLayout.value = MultiviewLayoutType.SOLO
        hideQuickBar()
    }

    fun exitSolo() {
        if (_currentLayout.value == MultiviewLayoutType.SOLO) {
            _currentLayout.value = previousLayoutBeforeSolo
            playerManager.setAudioTile(_focusedTileIndex.value)
        }
    }

    fun setSection(section: TvScreenSection) {
        if (section == TvScreenSection.GUIDE && _airings.value.isEmpty() && _guideError.value == null) {
            _tabloDevice.value?.let { device -> loadGuide(device, _channels.value) }
        }
        _currentSection.value = section
        if (section == TvScreenSection.MULTIVIEW) {
            resetAutoHideQuickBar()
        } else {
            _isQuickBarVisible.value = true
        }
    }

    fun toggleQuickBar() {
        if (_isQuickBarVisible.value) {
            hideQuickBar()
        } else {
            showQuickBar()
        }
    }

    fun showQuickBar() {
        _isQuickBarVisible.value = true
        if (_currentSection.value == TvScreenSection.MULTIVIEW) {
            resetAutoHideQuickBar()
        }
    }

    fun hideQuickBar() {
        _isQuickBarVisible.value = false
        autoHideJob?.cancel()
    }

    private fun resetAutoHideQuickBar() {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(4500)
            if (_currentSection.value == TvScreenSection.MULTIVIEW) {
                _isQuickBarVisible.value = false
            }
        }
    }

    fun tuneChannelFullscreen(channel: TabloChannel) {
        val targetIndex = 0
        // Free decoders and resources on other tiles
        for (i in 0 until 4) {
            if (i != targetIndex) {
                viewModelScope.launch { stopTileSession(i) }
                playerManager.stopTile(i)
            }
        }
        val slots = emptyTileSlots().toMutableList()
        slots[targetIndex] = channel
        _activeMultiviewChannels.value = slots
        _focusedTileIndex.value = targetIndex
        _currentLayout.value = MultiviewLayoutType.SOLO
        playChannelInTile(channel, targetIndex)
        playerManager.setAudioTile(targetIndex)
        _isPlaying.value = true
        _currentSection.value = TvScreenSection.MULTIVIEW
    }

    fun assignChannelToTile(channel: TabloChannel, tileIndex: Int) {
        val idx = tileIndex.coerceIn(0, 3)
        playChannelInTile(channel, idx)
        setFocusedTile(idx)
        if (_currentLayout.value == MultiviewLayoutType.SOLO) {
            _currentLayout.value = MultiviewLayoutType.HORIZONTAL_2_UP
        }
        _isPlaying.value = true
        _currentSection.value = TvScreenSection.MULTIVIEW
    }

    fun removeChannelFromTile(tileIndex: Int) {
        if (tileIndex in 0..3) {
            viewModelScope.launch { stopTileSession(tileIndex) }
            val slots = _activeMultiviewChannels.value.toMutableList()
            if (tileIndex < slots.size) {
                slots[tileIndex] = null
                _activeMultiviewChannels.value = slots
            }
            playerManager.stopTile(tileIndex)
            _tileErrors.value = _tileErrors.value - tileIndex
            val filled = slots.mapIndexedNotNull { index, ch -> if (ch != null) index else null }
            if (filled.isNotEmpty() && _focusedTileIndex.value == tileIndex) {
                setFocusedTile(filled.first())
            }
        }
    }

    fun togglePlayPause() {
        val currentFocus = _focusedTileIndex.value
        val playing = playerManager.togglePlayPause(currentFocus)
        _isPlaying.value = playing
    }

    fun goToLive() {
        val currentFocus = _focusedTileIndex.value
        playerManager.goToLive(currentFocus)
        _isPlaying.value = true
    }

    fun setMultiviewChannelsCount(count: Int) {
        when (count) {
            1 -> {
                _currentLayout.value = MultiviewLayoutType.SOLO
            }
            2 -> {
                _currentLayout.value = MultiviewLayoutType.HORIZONTAL_2_UP
            }
            3 -> {
                _currentLayout.value = MultiviewLayoutType.PRIMARY_1_PLUS_2
            }
            4 -> {
                _currentLayout.value = MultiviewLayoutType.GRID_2X2
            }
        }
    }

    fun saveCurrentMultiview(name: String) {
        viewModelScope.launch {
            val focusedChannel = _activeMultiviewChannels.value.getOrNull(_focusedTileIndex.value)
            val item = SavedMultiviewItem(
                name = name,
                layoutType = _currentLayout.value,
                channels = _activeMultiviewChannels.value.filterNotNull(),
                preferredAudioChannelId = focusedChannel?.channelId ?: ""
            )
            savedRepository.saveMultiview(item)
        }
    }

    fun saveCustomMultiview(name: String, layout: MultiviewLayoutType, channels: List<TabloChannel>) {
        viewModelScope.launch {
            val item = SavedMultiviewItem(
                name = name,
                layoutType = layout,
                channels = channels,
                preferredAudioChannelId = channels.firstOrNull()?.channelId ?: ""
            )
            savedRepository.saveMultiview(item)
        }
    }

    fun loadSavedMultiview(item: SavedMultiviewItem) {
        viewModelScope.launch {
            _activeMultiviewChannels.value = List(4) { index -> item.channels.getOrNull(index) }
            item.channels.forEachIndexed { index, channel ->
                playChannelInTile(channel, index)
            }
            _currentLayout.value = item.layoutType
            val prefIndex = item.channels.indexOfFirst { it.channelId == item.preferredAudioChannelId }
            val focusIndex = if (prefIndex != -1) prefIndex else 0
            setFocusedTile(focusIndex)
            _currentSection.value = TvScreenSection.MULTIVIEW
            hideQuickBar()
        }
    }

    fun toggleFavoriteChannel(channel: TabloChannel) {
        viewModelScope.launch {
            val favs = favoriteChannelIds.value
            if (favs.contains(channel.channelId)) {
                favoriteRepository.removeFavorite(channel.channelId)
            } else {
                favoriteRepository.addFavorite(channel.channelId)
            }
        }
    }

    fun renameSavedMultiview(id: Long, newName: String) {
        viewModelScope.launch {
            savedRepository.rename(id, newName)
        }
    }

    fun deleteSavedMultiview(id: Long) {
        viewModelScope.launch {
            savedRepository.delete(id)
        }
    }

    fun startDiscovery() {
        viewModelScope.launch {
            _isScanning.value = true
            _connectionError.value = null
            try {
                val devices = tabloRepository.discoverDevices()
                _discoveredDevices.value = devices
                if (devices.isEmpty()) {
                    _connectionError.value = "Tablo not found. Make sure your Tablo and Fire TV are connected to the same network."
                }
            } catch (e: Exception) {
                Log.e("TabloViewModel", "Discovery error: ${e.message}")
                _connectionError.value = "Tablo not found. Make sure your Tablo and Fire TV are connected to the same network."
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun selectDevice(device: TabloDevice) {
        viewModelScope.launch {
            val connected = device.copy(isConnected = true)
            deviceRepository.save(connected)
            _tabloDevice.value = connected
            _currentSection.value = TvScreenSection.GUIDE
            loadChannelsAndGuide(connected)
        }
    }

    fun loginTabloAccount(email: String, password: String) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value = null
            _connectionError.value = null
            try {
                when (val result = tabloRepository.loginTabloAccount(email, password)) {
                    is TabloResult.Success -> {
                        val devices = result.data
                        _discoveredDevices.value = devices
                        if (devices.size == 1) {
                            selectDevice(devices.first())
                        }
                    }
                    is TabloResult.Error -> {
                        _loginError.value = result.message
                    }
                }
            } catch (e: Exception) {
                Log.e("TabloViewModel", "Login error: ${e.message}")
                _loginError.value = e.message ?: "Login failed. Please check network connection."
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun connectDirectIp(ip: String, port: Int = 8885) {
        viewModelScope.launch {
            lastManualIp = ip.trim()
            _isConnecting.value = true
            _connectionError.value = null
            val device = tabloRepository.fetchServerInfo(ip.trim(), port)
            _isConnecting.value = false
            if (device == null) {
                _connectionError.value = "Tablo not found at ${ip.trim()}:$port. Check the IP and make sure the device is powered on."
            } else {
                selectDevice(device)
            }
        }
    }

    fun retryConnection() {
        val device = _tabloDevice.value
        if (device != null && device.isConnected) {
            loadChannelsAndGuide(device)
        } else if (!lastManualIp.isNullOrBlank()) {
            connectDirectIp(lastManualIp!!)
        } else {
            startDiscovery()
        }
    }

    fun refreshGuide() {
        val device = _tabloDevice.value ?: return
        loadGuide(device, _channels.value)
    }

    fun refreshChannelsAndGuide() {
        val device = _tabloDevice.value ?: return
        loadChannelsAndGuide(device)
    }

    fun disconnect() {
        viewModelScope.launch {
            playerManager.releaseAll()
            deviceRepository.clear()
            _tabloDevice.value = null
            _channels.value = emptyList()
            _airings.value = emptyList()
            _activeMultiviewChannels.value = emptyTileSlots()
            _discoveredDevices.value = emptyList()
            _channelError.value = null
            _guideError.value = null
            _connectionError.value = null
            _tileErrors.value = emptyMap()
            _isLoadingChannels.value = false
            _isLoadingGuide.value = false
            _currentSection.value = TvScreenSection.TABLO
            lastManualIp = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.releaseAll()
    }

    private companion object {
        const val STREAM_ERROR_MESSAGE = "Unable to start stream. Check that a tuner is free and the Tablo is reachable."

        fun emptyTileSlots(): List<TabloChannel?> = List(4) { null }
    }
}