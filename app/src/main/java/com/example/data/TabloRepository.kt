package com.example.data

import android.util.Log
import com.example.data.remote.TabloApiMapper
import com.example.data.remote.TabloApiService
import com.example.data.remote.TabloCloudLoginRequest
import com.example.data.remote.TabloCloudLoginResponse
import com.example.data.remote.TabloDiscoveryManager
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.example.model.TabloResult
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
 * Fetches all Tablo data from a real connected device using the documented API.
 * It never manufactures channels, airings, or stream URLs as a substitute for
 * device data.
 */
class TabloRepository(
    private val apiService: TabloApiService = createDefaultApiService(),
    private val discoveryManager: TabloDiscoveryManager = TabloDiscoveryManager(apiService)
) {

    companion object {
        private const val BATCH_SIZE = 100
        private const val MAX_BATCH_CHUNKS = 40

        fun createDefaultApiService(): TabloApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.tablotv.com/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(TabloApiService::class.java)
        }
    }

    suspend fun fetchServerInfo(host: String, port: Int = 8885): TabloDevice? = withContext(Dispatchers.IO) {
        val portsToTry = if (port == 8885) listOf(8885, 8881) else listOf(port, 8885, 8881).distinct()
        for (p in portsToTry) {
            val url = "http://$host:$p/server/info"
            try {
                val response = apiService.getServerInfo(url)
                val model = response.model
                return@withContext TabloDevice(
                    serverId = response.serverId ?: "tablo-$host",
                    name = response.name?.ifBlank { null } ?: model?.name?.ifBlank { null } ?: "Tablo ($host)",
                    model = model?.name ?: model?.type ?: "Tablo",
                    host = host,
                    port = p,
                    streamingPort = 80,
                    tunerCount = model?.tuners ?: 4,
                    activeTuners = 0,
                    isConnected = true,
                    firmware = response.version ?: ""
                )
            } catch (e: Exception) {
                Log.d("TabloRepository", "fetchServerInfo failed on port $p for $host: ${e.message}")
            }
        }
        null
    }

    suspend fun loginTabloAccount(email: String, password: String): TabloResult<List<TabloDevice>> = withContext(Dispatchers.IO) {
        try {
            val request = TabloCloudLoginRequest(email = email.trim(), password = password)
            var response: TabloCloudLoginResponse? = null
            try {
                response = apiService.loginTabloCloud("https://lighthousetv.ewscloud.com/api/v2/login/", request)
            } catch (e: Exception) {
                Log.d("TabloRepository", "Lighthouse login failed, trying secondary endpoint: ${e.message}")
                try {
                    response = apiService.loginTabloCloud("https://api.tablotv.com/account/login", request)
                } catch (e2: Exception) {
                    Log.d("TabloRepository", "Secondary login endpoint failed: ${e2.message}")
                }
            }

            if (response != null && !response.devices.isNullOrEmpty()) {
                val devices = response.devices.mapNotNull { cloudDev ->
                    val host = cloudDev.privateIp ?: cloudDev.publicIp ?: return@mapNotNull null
                    val port = cloudDev.httpPort ?: 8885
                    fetchServerInfo(host, port) ?: TabloDevice(
                        serverId = cloudDev.serverId ?: cloudDev.serverid ?: "tablo-$host",
                        name = cloudDev.name?.ifBlank { null } ?: "Tablo ($host)",
                        model = cloudDev.model ?: cloudDev.boardType ?: "Tablo",
                        host = host,
                        port = port,
                        streamingPort = 80,
                        tunerCount = 4,
                        activeTuners = 0,
                        isConnected = false,
                        firmware = cloudDev.serverVersion ?: ""
                    )
                }
                if (devices.isNotEmpty()) {
                    return@withContext TabloResult.Success(devices)
                }
            }

            // Also check hosted association info for any devices linked to this user's network
            val assocDevices = discoveryManager.discoverTablos()
            if (assocDevices.isNotEmpty()) {
                TabloResult.Success(assocDevices)
            } else if (response?.error != null) {
                TabloResult.Error("Login failed: ${response.error}")
            } else {
                TabloResult.Error("No Tablo devices found under this account. Please verify your credentials or ensure your Tablo is powered on.")
            }
        } catch (e: Exception) {
            TabloResult.Error("Failed to sign in to Tablo account: ${e.message ?: "Connection error"}")
        }
    }


    suspend fun fetchGuideStatus(device: TabloDevice): Boolean? = withContext(Dispatchers.IO) {
        try {
            val status = apiService.getGuideStatus("${device.localBaseUrl}/server/guide/status")
            status.guideSeeded
        } catch (e: Exception) {
            Log.d("TabloRepository", "Guide status fetch failed: ${e.message}")
            null
        }
    }

    suspend fun fetchTunerStatus(device: TabloDevice): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            val tuners = apiService.getTuners("${device.localBaseUrl}/server/tuners")
            val active = tuners.count { it.inUse == true }
            Pair(active, device.tunerCount)
        } catch (e: Exception) {
            Pair(0, device.tunerCount)
        }
    }

    suspend fun fetchLiveChannels(device: TabloDevice): TabloResult<List<TabloChannel>> =
        withContext(Dispatchers.IO) {
            val baseUrl = device.localBaseUrl
            try {
                val channelPaths = apiService.getChannelPaths("$baseUrl/guide/channels")
                if (channelPaths.isEmpty()) {
                    return@withContext TabloResult.Error("No channels are available on this Tablo")
                }

                val channels = batchLoadChannels(baseUrl, channelPaths)
                if (channels.isEmpty()) {
                    return@withContext TabloResult.Error("Could not load channel information from the Tablo")
                }
                return@withContext TabloResult.Success(
                    channels.sortedWith(compareBy({ it.majorNumber }, { it.minorNumber }, { it.callSign }))
                )
            } catch (e: Exception) {
                Log.d("TabloRepository", "Live channel fetch failed: ${e.message}")
                TabloResult.Error("The Tablo is not responding. Make sure it is powered on and on the same network.")
            }
        }

    private suspend fun batchLoadChannels(baseUrl: String, paths: List<String>): List<TabloChannel> {
        val channels = mutableListOf<TabloChannel>()
        // Batch is the documented fast path; fall back to individual fetches.
        try {
            for (chunkStart in paths.indices step BATCH_SIZE) {
                val chunk = paths.subList(chunkStart, minOf(chunkStart + BATCH_SIZE, paths.size))
                val batch = apiService.postChannelBatch("$baseUrl/batch", chunk)
                for ((path, detail) in batch) {
                    TabloApiMapper.channelFromDetail(path, detail)?.let(channels::add)
                }
            }
            if (channels.isNotEmpty()) return channels
        } catch (e: Exception) {
            Log.d("TabloRepository", "Channel batch failed, falling back to individual: ${e.message}")
        }
        for (path in paths) {
            val fullUrl = if (path.startsWith("http")) path else "$baseUrl$path"
            try {
                val detail = apiService.getChannelDetail(fullUrl)
                TabloApiMapper.channelFromDetail(path, detail)?.let(channels::add)
            } catch (e: Exception) {
                Log.w("TabloRepository", "Failed to fetch channel detail for $path: ${e.message}")
            }
        }
        return channels
    }

    suspend fun fetchGuideAirings(
        device: TabloDevice,
        channelIds: List<String>,
        windowStart: Long,
        windowEnd: Long
    ): TabloResult<List<TabloAiring>> = withContext(Dispatchers.IO) {
        val baseUrl = device.localBaseUrl
        val channelSet = channelIds.toSet()
        try {
            val guideSeeded = fetchGuideStatus(device)
            if (guideSeeded == false) {
                return@withContext TabloResult.Error("Guide data is not available on this Tablo yet.")
            }

            val airingPaths = apiService.getAiringPaths("$baseUrl/guide/airings")
            if (airingPaths.isEmpty()) {
                return@withContext TabloResult.Error("No guide information is available from this Tablo")
            }

            val now = System.currentTimeMillis()
            val maxKept = if (channelSet.isEmpty()) 600 else (channelSet.size * 10).coerceIn(400, 4000)
            val kept = mutableListOf<TabloAiring>()
            var processedChunks = 0

            for (chunkStart in airingPaths.indices step BATCH_SIZE) {
                if (processedChunks >= MAX_BATCH_CHUNKS) break
                if (kept.size >= maxKept && processedChunks >= 6) break
                val chunk = airingPaths.subList(chunkStart, minOf(chunkStart + BATCH_SIZE, airingPaths.size))
                processedChunks++

                val batch = runCatching { apiService.postBatch("$baseUrl/batch", chunk) }.getOrNull() ?: continue
                for ((path, detail) in batch) {
                    val airing = TabloApiMapper.airingFromDetail(path, detail, now) ?: continue
                    if (airing.channelId !in channelSet) continue
                    if (airing.endTimeMillis > windowStart && airing.startTimeMillis < windowEnd) {
                        kept.add(airing)
                    }
                }
            }

            if (kept.isEmpty()) {
                return@withContext TabloResult.Error("No programming found in the selected time window")
            }
            TabloResult.Success(kept.sortedWith(compareBy({ it.channelId }, { it.startTimeMillis })))
        } catch (e: Exception) {
            Log.d("TabloRepository", "Guide airings fetch failed: ${e.message}")
            TabloResult.Error("The guide could not be loaded. Make sure the device is reachable.")
        }
    }

    /**
     * Requests a live watch stream via the documented <channel_path>/watch
     * endpoint. Returns the device-provided playlist URL or null on failure.
     */
    suspend fun fetchWatchStreamUrl(device: TabloDevice, channel: TabloChannel): String? =
        withContext(Dispatchers.IO) {
            val watchUrl = "${device.localBaseUrl}${channel.channelPath}/watch"
            try {
                val emptyBody = "".toRequestBody("application/json".toMediaType())
                val response = apiService.postWatch(watchUrl, emptyBody)
                val playlist = response.playlistUrl
                if (!playlist.isNullOrBlank()) {
                    return@withContext if (playlist.startsWith("http")) playlist else "${device.streamingBaseUrl}$playlist"
                }
            } catch (e: Exception) {
                Log.d("TabloRepository", "Watch stream request failed for ${channel.channelId}: ${e.message}")
            }
            null
        }

    suspend fun discoverDevices(): List<TabloDevice> = discoveryManager.discoverTablos()
}