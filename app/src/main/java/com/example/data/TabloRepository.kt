package com.example.data

import android.util.Log
import com.example.data.remote.TabloApiMapper
import com.example.data.remote.TabloApiService
import com.example.data.remote.TabloCloudLoginRequest
import com.example.data.remote.TabloCloudLoginResponse
import com.example.data.remote.TabloDiscoveryManager
import com.example.data.remote.TabloGen4Auth
import com.example.data.remote.TabloGen4LoginRequest
import com.example.data.remote.TabloGen4SelectRequest
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Fetches all Tablo data from a real connected device using the documented API.
 * It supports both Tablo 4th Gen (LighthouseTV Cloud & HMAC-MD5 signing)
 * and legacy 2nd/3rd Gen Tablo units.
 */
class TabloRepository(
    private val apiService: TabloApiService = createDefaultApiService(),
    private val discoveryManager: TabloDiscoveryManager = TabloDiscoveryManager(apiService)
) {
    private val rawHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BATCH_SIZE = 100
        private const val MAX_BATCH_CHUNKS = 40

        fun createDefaultApiService(): TabloApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
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
        val trimmedEmail = email.trim()
        val trimmedPass = password.trim()
        if (trimmedEmail.isBlank() || trimmedPass.isBlank()) {
            return@withContext TabloResult.Error("Please enter both email and password.")
        }

        // 1. Try Tablo 4th Gen LighthouseTV authentication (lighthousetv.ewscloud.com)
        try {
            Log.d("TabloRepository", "Attempting Tablo Gen 4 Lighthouse cloud login for $trimmedEmail")
            val loginResp = apiService.loginGen4(
                url = "${TabloGen4Auth.CLOUD_HOST}/api/v2/login/",
                userAgent = TabloGen4Auth.USER_AGENT_CLOUD,
                body = TabloGen4LoginRequest(email = trimmedEmail, password = trimmedPass)
            )

            val accessToken = loginResp.accessToken
            if (!accessToken.isNullOrBlank()) {
                val authHeader = "Bearer $accessToken"
                val accountResp = apiService.getGen4Account(
                    url = "${TabloGen4Auth.CLOUD_HOST}/api/v2/account/",
                    userAgent = TabloGen4Auth.USER_AGENT_CLOUD,
                    authorization = authHeader
                )

                val profiles = accountResp.profiles.orEmpty()
                val devices = accountResp.devices.orEmpty()

                if (profiles.isNotEmpty() && devices.isNotEmpty()) {
                    val primaryProfileId = profiles.first().identifier
                    val resultDevices = mutableListOf<TabloDevice>()

                    for (dev in devices) {
                        val sid = dev.serverId ?: continue
                        var lhToken: String? = null
                        try {
                            val selectResp = apiService.selectGen4Account(
                                url = "${TabloGen4Auth.CLOUD_HOST}/api/v2/account/select/",
                                userAgent = TabloGen4Auth.USER_AGENT_CLOUD,
                                authorization = authHeader,
                                body = TabloGen4SelectRequest(pid = primaryProfileId, sid = sid)
                            )
                            lhToken = selectResp.token
                        } catch (e: Exception) {
                            Log.w("TabloRepository", "Account select failed for device $sid: ${e.message}")
                        }

                        // Parse local URL (e.g. http://192.168.1.50:8885)
                        var host = "192.168.1.1"
                        var port = 8885
                        if (!dev.url.isNullOrBlank()) {
                            try {
                                val uri = URI(dev.url)
                                host = uri.host ?: host
                                port = if (uri.port > 0) uri.port else 8885
                            } catch (e: Exception) {
                                Log.w("TabloRepository", "Could not parse device URL: ${dev.url}")
                            }
                        }

                        val gen4Device = TabloDevice(
                            serverId = sid,
                            name = dev.name?.ifBlank { null } ?: "Tablo 4th Gen ($sid)",
                            model = "Tablo 4th Gen",
                            host = host,
                            port = port,
                            streamingPort = port,
                            tunerCount = 4,
                            activeTuners = 0,
                            isConnected = true,
                            firmware = "Gen4-2.0",
                            lighthouseToken = lhToken,
                            accountToken = accessToken,
                            clientId = UUID.randomUUID().toString(),
                            isGen4 = true
                        )
                        resultDevices.add(gen4Device)
                    }

                    if (resultDevices.isNotEmpty()) {
                        Log.i("TabloRepository", "Tablo Gen 4 login successful! Discovered ${resultDevices.size} devices.")
                        return@withContext TabloResult.Success(resultDevices)
                    }
                } else if (profiles.isEmpty()) {
                    return@withContext TabloResult.Error("No active profiles found on this Tablo account.")
                } else {
                    return@withContext TabloResult.Error("No Tablo Gen 4 devices are linked to this account.")
                }
            }
        } catch (e: Exception) {
            Log.d("TabloRepository", "Gen 4 login failed: ${e.message}. Trying legacy cloud endpoints.")
        }

        // 2. Secondary/Legacy Cloud Login Fallback
        try {
            val request = TabloCloudLoginRequest(email = trimmedEmail, password = trimmedPass)
            var response: TabloCloudLoginResponse? = null
            try {
                response = apiService.loginTabloCloud("https://api.tablotv.com/account/login", request)
            } catch (e2: Exception) {
                Log.d("TabloRepository", "Secondary legacy login failed: ${e2.message}")
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
                        isConnected = true,
                        firmware = cloudDev.serverVersion ?: ""
                    )
                }
                if (devices.isNotEmpty()) {
                    return@withContext TabloResult.Success(devices)
                }
            }
        } catch (e: Exception) {
            Log.d("TabloRepository", "Legacy cloud check failed: ${e.message}")
        }

        // 3. Local network auto-discovery as fallback for local setup
        val assocDevices = discoveryManager.discoverTablos()
        if (assocDevices.isNotEmpty()) {
            return@withContext TabloResult.Success(assocDevices)
        }

        TabloResult.Error("Login failed. Please verify your Tablo account email and password, and ensure your Tablo is powered on and connected to the internet.")
    }

    suspend fun fetchGuideStatus(device: TabloDevice): Boolean? = withContext(Dispatchers.IO) {
        if (device.isGen4) return@withContext true
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
            // If Tablo Gen 4, fetch cloud guide lineup using Lighthouse token
            if (device.isGen4 && !device.lighthouseToken.isNullOrBlank() && !device.accountToken.isNullOrBlank()) {
                try {
                    val channelsUrl = "${TabloGen4Auth.CLOUD_HOST}/api/v2/account/${device.lighthouseToken}/guide/channels/"
                    val cloudChannels = apiService.getGen4Channels(
                        url = channelsUrl,
                        userAgent = TabloGen4Auth.USER_AGENT_CLOUD,
                        authorization = "Bearer ${device.accountToken}",
                        lighthouse = device.lighthouseToken
                    )

                    if (cloudChannels.isNotEmpty()) {
                        val mapped = cloudChannels.map { ch ->
                            val isOta = ch.kind.equals("ota", ignoreCase = true)
                            val info = if (isOta) ch.ota else (ch.ott ?: ch.ota)
                            val major = info?.major ?: 0
                            val minor = info?.minor ?: 0
                            val callSign = info?.callSign ?: ch.name ?: (if (isOta) "OTA $major" else "FAST")
                            val network = info?.network ?: ch.name ?: ""

                            TabloChannel(
                                channelId = ch.identifier,
                                callSign = callSign,
                                majorNumber = major,
                                minorNumber = minor,
                                network = network,
                                resolution = if (isOta) "HD" else "720p",
                                channelPath = "/guide/channels/${ch.identifier}",
                                identifier = ch.identifier,
                                isOtt = !isOta
                            )
                        }

                        val sorted = mapped.sortedWith(
                            compareBy<TabloChannel> { it.isOtt }
                                .thenBy { it.majorNumber }
                                .thenBy { it.minorNumber }
                                .thenBy { it.callSign }
                        )
                        return@withContext TabloResult.Success(sorted)
                    }
                } catch (e: Exception) {
                    Log.w("TabloRepository", "Gen 4 cloud channels fetch failed, falling back to local: ${e.message}")
                }
            }

            // Local fallback (for 2nd/3rd Gen or local Gen 4)
            val baseUrl = device.localBaseUrl
            try {
                val channelPaths = apiService.getChannelPaths("$baseUrl/guide/channels")
                if (channelPaths.isEmpty()) {
                    return@withContext TabloResult.Error("No channels found on this Tablo.")
                }

                val channels = batchLoadChannels(baseUrl, channelPaths)
                if (channels.isEmpty()) {
                    return@withContext TabloResult.Error("Could not load channel details from the Tablo.")
                }
                return@withContext TabloResult.Success(
                    channels.sortedWith(compareBy({ it.majorNumber }, { it.minorNumber }, { it.callSign }))
                )
            } catch (e: Exception) {
                Log.d("TabloRepository", "Live channel fetch failed: ${e.message}")
                TabloResult.Error("The Tablo is not responding at ${device.host}. Make sure it is connected to the same Wi-Fi/LAN.")
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
     * Requests a live watch stream via either Gen 4 signed HMAC watch or legacy watch endpoint.
     * Returns the device-provided playlist URL (HLS .m3u8) or null on failure.
     */
    suspend fun fetchWatchStreamUrl(device: TabloDevice, channel: TabloChannel): String? =
        withContext(Dispatchers.IO) {
            // 1. Tablo Gen 4 HMAC-MD5 signed watch flow
            val channelIdentifier = channel.identifier ?: channel.channelId
            if (device.isGen4 || channel.identifier != null) {
                try {
                    val path = "/guide/channels/$channelIdentifier/watch"
                    val fullUrl = "${device.localBaseUrl}$path?lh"
                    val watchBody = TabloGen4Auth.makeWatchBody(device.clientId)
                    val (authHeader, dateHeader) = TabloGen4Auth.makeDeviceAuth("POST", path, watchBody)

                    val requestBody = watchBody.toRequestBody("application/x-www-form-urlencoded".toMediaType())
                    val request = Request.Builder()
                        .url(fullUrl)
                        .post(requestBody)
                        .header("Authorization", authHeader)
                        .header("Date", dateHeader)
                        .header("User-Agent", TabloGen4Auth.USER_AGENT_WATCH)
                        .header("Accept", "*/*")
                        .header("Connection", "keep-alive")
                        .build()

                    rawHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string()
                            if (!bodyStr.isNullOrBlank()) {
                                val json = JSONObject(bodyStr)
                                val playlistUrl = json.optString("playlist_url", "")
                                if (playlistUrl.isNotBlank()) {
                                    Log.i("TabloRepository", "Obtained Gen 4 live stream: $playlistUrl")
                                    return@withContext playlistUrl
                                }
                            }
                        } else {
                            Log.w("TabloRepository", "Gen 4 watch returned HTTP ${response.code}: ${response.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TabloRepository", "Gen 4 signed watch request failed: ${e.message}")
                }
            }

            // 2. Legacy Tablo 2nd/3rd Gen watch flow
            val watchUrl = "${device.localBaseUrl}${channel.channelPath}/watch"
            try {
                val emptyBody = "".toRequestBody("application/json".toMediaType())
                val response = apiService.postWatch(watchUrl, emptyBody)
                val playlist = response.playlistUrl
                if (!playlist.isNullOrBlank()) {
                    return@withContext if (playlist.startsWith("http")) playlist else "${device.streamingBaseUrl}$playlist"
                }
            } catch (e: Exception) {
                Log.d("TabloRepository", "Legacy watch stream request failed for ${channel.channelId}: ${e.message}")
            }
            null
        }

    suspend fun discoverDevices(): List<TabloDevice> = discoveryManager.discoverTablos()
}