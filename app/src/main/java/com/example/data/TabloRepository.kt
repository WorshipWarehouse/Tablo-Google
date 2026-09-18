package com.example.data

import android.util.Log
import com.example.data.remote.TabloApiService
import com.example.data.remote.TabloDiscoveryManager
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Data repository class responsible for fetching live TV channels, program listings,
 * and tuner status from the Tablo OTA DVR API via Retrofit with resilient local fallbacks.
 */
class TabloRepository(
    private val apiService: TabloApiService = createDefaultApiService(),
    private val discoveryManager: TabloDiscoveryManager = TabloDiscoveryManager()
) {

    companion object {
        fun createDefaultApiService(): TabloApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.tablotv.com/") // Default base URL for cloud association
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(TabloApiService::class.java)
        }
    }

    /**
     * Query Tablo device hardware info via Retrofit.
     */
    suspend fun fetchServerInfo(host: String, port: Int = 8885): TabloDevice? = withContext(Dispatchers.IO) {
        val url = "http://$host:$port/server/info"
        try {
            val response = apiService.getServerInfo(url)
            val serverId = response.serverId ?: "tablo-${host.replace(".", "-")}"
            val name = response.name ?: "Tablo TV"
            val model = response.model ?: "Tablo DUAL / QUAD"
            val tuners = response.tuners ?: 4
            val version = response.version ?: "2.2.44"
            TabloDevice(
                serverId = serverId,
                name = name,
                model = model,
                host = host,
                port = port,
                tunerCount = tuners,
                activeTuners = 0,
                isConnected = true,
                firmware = version
            )
        } catch (e: Exception) {
            Log.d("TabloRepository", "fetchServerInfo failed: ${e.message}")
            null
        }
    }

    /**
     * Fetch active tuner allocation status.
     */
    suspend fun fetchTunerStatus(device: TabloDevice): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val url = "${device.localBaseUrl}/server/tuners"
        try {
            val response = apiService.getTuners(url)
            var active = 0
            for ((_, value) in response) {
                if (value is Map<*, *>) {
                    if (value["in_use"] == true) active++
                }
            }
            Pair(active, device.tunerCount)
        } catch (e: Exception) {
            Pair(0, device.tunerCount)
        }
    }

    /**
     * Fetch live OTA channel lineup from Tablo DVR.
     */
    suspend fun fetchLiveChannels(device: TabloDevice): List<TabloChannel> = withContext(Dispatchers.IO) {
        val url = "${device.localBaseUrl}/guide/channels"
        try {
            val channelPaths = apiService.getChannelPaths(url)
            val channels = mutableListOf<TabloChannel>()
            for (path in channelPaths) {
                if (path.isNotEmpty()) {
                    val fullUrl = if (path.startsWith("http")) path else "${device.localBaseUrl}$path"
                    try {
                        val detail = apiService.getChannelDetail(fullUrl)
                        val ch = detail.channel
                        val callSign = ch?.callSign ?: "OTA"
                        val major = ch?.major ?: 1
                        val minor = ch?.minor ?: 1
                        val network = ch?.network ?: callSign
                        val resolution = ch?.resolution ?: "1080i"
                        val id = path.substringAfterLast("/")
                        channels.add(
                            TabloChannel(
                                channelId = id,
                                callSign = callSign,
                                majorNumber = major,
                                minorNumber = minor,
                                network = network,
                                resolution = resolution,
                                channelPath = path,
                                streamUrl = ""
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("TabloRepository", "Failed to fetch detail for $path: ${e.message}")
                    }
                }
            }
            if (channels.isNotEmpty()) {
                return@withContext channels
            }
        } catch (e: Exception) {
            Log.d("TabloRepository", "Live channel fetch failed: ${e.message}")
        }
        // Fallback to high-quality test channel feeds
        discoveryManager.getMockChannels()
    }

    /**
     * Fetch live TV EPG program airings for guide and multiview overlays.
     */
    suspend fun fetchLiveAirings(device: TabloDevice, channelIds: List<String>): List<TabloAiring> = withContext(Dispatchers.IO) {
        val url = "${device.localBaseUrl}/guide/airings"
        try {
            val airingPaths = apiService.getAiringPaths(url)
            val airings = mutableListOf<TabloAiring>()
            val count = minOf(airingPaths.size, 20)
            for (i in 0 until count) {
                val path = airingPaths[i]
                if (path.isNotEmpty()) {
                    val fullUrl = if (path.startsWith("http")) path else "${device.localBaseUrl}$path"
                    try {
                        val detail = apiService.getAiringDetail(fullUrl)
                        val title = detail.show?.title ?: detail.airingDetails?.title ?: "Live Broadcast"
                        val episodeTitle = detail.episode?.title
                        val description = detail.episode?.description ?: ""
                        val duration = detail.airingDetails?.duration ?: 1800L
                        val channelPath = detail.channelPath ?: ""
                        val channelId = channelPath.substringAfterLast("/")
                        airings.add(
                            TabloAiring(
                                airingId = path.substringAfterLast("/"),
                                channelId = channelId,
                                title = title,
                                episodeTitle = episodeTitle,
                                description = description,
                                startTimeMillis = System.currentTimeMillis() - 10 * 60 * 1000L,
                                durationSeconds = duration,
                                category = "Live TV",
                                isLive = true
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("TabloRepository", "Failed to fetch airing detail: ${e.message}")
                    }
                }
            }
            if (airings.isNotEmpty()) {
                return@withContext airings
            }
        } catch (e: Exception) {
            Log.d("TabloRepository", "Live airings fetch failed: ${e.message}")
        }
        discoveryManager.getMockGuideAirings()
    }

    /**
     * Request a transcode/watch HLS stream playlist URL for a selected channel.
     */
    suspend fun fetchWatchStreamUrl(device: TabloDevice, channel: TabloChannel): String = withContext(Dispatchers.IO) {
        val watchUrl = "${device.localBaseUrl}${channel.channelPath}/watch"
        try {
            val emptyBody = "".toRequestBody("application/json".toMediaType())
            val response = apiService.postWatch(watchUrl, emptyBody)
            val playlist = response.playlistUrl
            if (!playlist.isNullOrEmpty()) {
                return@withContext if (playlist.startsWith("http")) {
                    playlist
                } else {
                    "http://${device.host}:${device.streamingPort}$playlist"
                }
            }
        } catch (e: Exception) {
            Log.d("TabloRepository", "Watch stream request failed: ${e.message}")
        }
        // If device stream URL isn't available or direct channel has fallback
        if (channel.streamUrl.isNotEmpty()) {
            channel.streamUrl
        } else {
            "http://${device.host}:${device.streamingPort}/stream/pl.m3u8?channel=${channel.channelId}"
        }
    }

    /**
     * Discover local Tablo devices via Cloud Association Server and mDNS.
     */
    suspend fun discoverDevices(): List<TabloDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<TabloDevice>()
        try {
            val assoc = apiService.getAssociationServerInfo()
            val cservers = assoc.cservers
            if (cservers != null) {
                for (cserver in cservers) {
                    val host = cserver.privateIp
                    if (!host.isNullOrEmpty()) {
                        val verified = fetchServerInfo(host)
                        if (verified != null) {
                            devices.add(verified)
                        } else {
                            devices.add(
                                TabloDevice(
                                    serverId = cserver.serverId ?: "tablo-assoc",
                                    name = cserver.name ?: "Living Room Tablo",
                                    model = "Tablo DUAL / QUAD",
                                    host = host,
                                    tunerCount = 4,
                                    isConnected = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("TabloRepository", "Assoc server lookup failed: ${e.message}")
        }

        // Also incorporate local discovery manager scans
        val localFound = discoveryManager.discoverTablos()
        for (dev in localFound) {
            if (devices.none { it.host == dev.host }) {
                devices.add(dev)
            }
        }

        if (devices.isEmpty()) {
            devices.add(discoveryManager.getDemoTabloDevice())
        }
        devices
    }

    fun getDemoDevice(): TabloDevice = discoveryManager.getDemoTabloDevice()

    fun getMockChannels(): List<TabloChannel> = discoveryManager.getMockChannels()

    fun getMockGuideAirings(): List<TabloAiring> = discoveryManager.getMockGuideAirings()
}
