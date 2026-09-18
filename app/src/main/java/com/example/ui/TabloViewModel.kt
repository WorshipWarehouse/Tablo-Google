package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TabloRepository
import com.example.data.local.AppDatabase
import com.example.data.local.SavedMultiviewRepository
import com.example.data.remote.TabloApiClient
import com.example.data.remote.TabloDiscoveryManager
import com.example.model.MultiviewLayoutType
import com.example.model.SavedMultiviewItem
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.example.playback.MultiviewPlayerManager
import com.example.ui.components.TvScreenSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TabloViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val savedRepository = SavedMultiviewRepository(database.savedMultiviewDao())
    private val tabloRepository = TabloRepository()
    private val discoveryManager = TabloDiscoveryManager()

    val playerManager = MultiviewPlayerManager(application)

    val savedMultiviews: StateFlow<List<SavedMultiviewItem>> = savedRepository.savedMultiviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSection = MutableStateFlow(TvScreenSection.MULTIVIEW)
    val currentSection: StateFlow<TvScreenSection> = _currentSection.asStateFlow()

    private val _currentLayout = MutableStateFlow(MultiviewLayoutType.GRID_2X2)
    val currentLayout: StateFlow<MultiviewLayoutType> = _currentLayout.asStateFlow()

    private var previousLayoutBeforeSolo: MultiviewLayoutType = MultiviewLayoutType.GRID_2X2

    private val _focusedTileIndex = MutableStateFlow(0)
    val focusedTileIndex: StateFlow<Int> = _focusedTileIndex.asStateFlow()

    private val _isQuickBarVisible = MutableStateFlow(false)
    val isQuickBarVisible: StateFlow<Boolean> = _isQuickBarVisible.asStateFlow()

    private val _channels = MutableStateFlow<List<TabloChannel>>(emptyList())
    val channels: StateFlow<List<TabloChannel>> = _channels.asStateFlow()

    private val _airings = MutableStateFlow<List<TabloAiring>>(emptyList())
    val airings: StateFlow<List<TabloAiring>> = _airings.asStateFlow()

    private val _activeMultiviewChannels = MutableStateFlow<List<TabloChannel>>(emptyList())
    val activeMultiviewChannels: StateFlow<List<TabloChannel>> = _activeMultiviewChannels.asStateFlow()

    private val _tabloDevice = MutableStateFlow<TabloDevice?>(null)
    val tabloDevice: StateFlow<TabloDevice?> = _tabloDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TabloDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TabloDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var autoHideJob: Job? = null

    init {
        initializeInitialSetup()
    }

    private fun initializeInitialSetup() {
        viewModelScope.launch {
            // Load demo / initial channels and airings from repository
            val initialChannels = tabloRepository.getMockChannels()
            val initialAirings = tabloRepository.getMockGuideAirings()
            val defaultDevice = tabloRepository.getDemoDevice()

            _channels.value = initialChannels
            _airings.value = initialAirings
            _tabloDevice.value = defaultDevice
            _discoveredDevices.value = listOf(defaultDevice)

            // Populate initial 4 channels for 2x2 multiview
            val top4 = initialChannels.take(4)
            _activeMultiviewChannels.value = top4

            // Start playing the streams with smooth staggered decoder allocation
            top4.forEachIndexed { index, channel ->
                if (index == 0) {
                    playerManager.playChannel(index, channel, channel.streamUrl)
                } else {
                    viewModelScope.launch {
                        delay(150L * index)
                        playerManager.playChannel(index, channel, channel.streamUrl)
                    }
                }
            }
            playerManager.setAudioTile(0)

            // Trigger background discovery for any live hardware Tablos
            startDiscovery()
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
        val currentList = _activeMultiviewChannels.value.toMutableList()
        val index = _focusedTileIndex.value
        if (index < currentList.size) {
            currentList[index] = channel
        } else {
            currentList.add(channel)
        }
        _activeMultiviewChannels.value = currentList
        playerManager.playChannel(index, channel, channel.streamUrl)
        enterSolo(index)
        _currentSection.value = TvScreenSection.MULTIVIEW
    }

    fun assignChannelToTile(channel: TabloChannel, tileIndex: Int) {
        val currentList = _activeMultiviewChannels.value.toMutableList()
        while (currentList.size <= tileIndex) {
            currentList.add(channel)
        }
        currentList[tileIndex] = channel
        _activeMultiviewChannels.value = currentList
        playerManager.playChannel(tileIndex, channel, channel.streamUrl)
        setFocusedTile(tileIndex)
        _currentSection.value = TvScreenSection.MULTIVIEW
    }

    fun saveCurrentMultiview(name: String) {
        viewModelScope.launch {
            val focusedChannel = _activeMultiviewChannels.value.getOrNull(_focusedTileIndex.value)
            val item = SavedMultiviewItem(
                name = name,
                layoutType = _currentLayout.value,
                channels = _activeMultiviewChannels.value,
                preferredAudioChannelId = focusedChannel?.channelId ?: ""
            )
            savedRepository.saveMultiview(item)
        }
    }

    fun loadSavedMultiview(item: SavedMultiviewItem) {
        viewModelScope.launch {
            _activeMultiviewChannels.value = item.channels
            item.channels.forEachIndexed { index, channel ->
                playerManager.playChannel(index, channel, channel.streamUrl)
            }
            _currentLayout.value = item.layoutType
            val prefIndex = item.channels.indexOfFirst { it.channelId == item.preferredAudioChannelId }
            val focusIndex = if (prefIndex != -1) prefIndex else 0
            setFocusedTile(focusIndex)
            _currentSection.value = TvScreenSection.MULTIVIEW
            hideQuickBar()
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
            try {
                val devices = tabloRepository.discoverDevices()
                _discoveredDevices.value = devices
            } catch (e: Exception) {
                Log.e("TabloViewModel", "Discovery error: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun selectDevice(device: TabloDevice) {
        viewModelScope.launch {
            _tabloDevice.value = device
            try {
                val remoteChannels = tabloRepository.fetchLiveChannels(device)
                if (remoteChannels.isNotEmpty()) {
                    _channels.value = remoteChannels
                    val airings = tabloRepository.fetchLiveAirings(device, remoteChannels.map { it.channelId })
                    if (airings.isNotEmpty()) {
                        _airings.value = airings
                    }
                    _activeMultiviewChannels.value = remoteChannels.take(4)
                    remoteChannels.take(4).forEachIndexed { i, ch ->
                        val watchUrl = tabloRepository.fetchWatchStreamUrl(device, ch)
                        playerManager.playChannel(i, ch, watchUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e("TabloViewModel", "Error tuning to device: ${e.message}")
            }
        }
    }

    fun connectDirectIp(ip: String) {
        viewModelScope.launch {
            val device = tabloRepository.fetchServerInfo(ip) ?: discoveryManager.connectDirectIp(ip)
            if (device != null) {
                selectDevice(device)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.releaseAll()
    }
}
