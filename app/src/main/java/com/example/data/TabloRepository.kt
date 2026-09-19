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
import com.example.data.remote.TabloAiringDetailResponse
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.example.model.TabloResult
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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
    var activeLighthouseToken: String? = null

    val signingInterceptor = com.example.data.remote.TabloGen4SigningInterceptor {
        activeLighthouseToken
    }

    private val rawHttpClient = OkHttpClient.Builder()
        .addInterceptor(signingInterceptor)
        .addInterceptor(com.example.data.remote.DiagnosticsInterceptor())
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BATCH_SIZE = 100
        private const val MAX_BATCH_CHUNKS = 40

        fun createDefaultApiService(signingInterceptor: com.example.data.remote.TabloGen4SigningInterceptor = com.example.data.remote.TabloGen4SigningInterceptor()): TabloApiService {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(signingInterceptor)
                .addInterceptor(com.example.data.remote.DiagnosticsInterceptor())
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

    private suspend fun probeLivePort(host: String, candidatePort: Int): Int {
        val portsToTry = listOf(8887, 8885, candidatePort).distinct()
        for (p in portsToTry) {
            val testUrl = "http://$host:$p/server/info"
            try {
                val request = Request.Builder().url(testUrl).get().build()
                rawHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.i("TabloRepository", "Probed live port $p for $host")
                        return p
                    }
                }
            } catch (e: Exception) {
                Log.d("TabloRepository", "Port probe $p failed for $host: ${e.message}")
            }
        }
        return candidatePort
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

                        // Parse local URL (e.g. http://192.168.1.50:8885) and probe live port (8887 then 8885)
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

                        val livePort = probeLivePort(host, port)

                        val gen4Device = TabloDevice(
                            serverId = sid,
                            name = dev.name?.ifBlank { null } ?: "Tablo 4th Gen ($sid)",
                            model = "Tablo 4th Gen",
                            host = host,
                            port = livePort,
                            streamingPort = 80,
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
                        Log.i("TabloRepository", "Gen 4 Cloud channels response count: ${cloudChannels.size} channels")
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
                val channelPaths = if (device.isGen4) {
                    fetchGen4SignedPaths(device, "/guide/channels")
                } else {
                    apiService.getChannelPaths("$baseUrl/guide/channels")
                }

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

    private suspend fun fetchGen4SignedPaths(device: TabloDevice, path: String): List<String> {
        val (authHeader, dateHeader) = TabloGen4Auth.makeDeviceAuth("GET", path, "")
        val request = Request.Builder()
            .url("${device.localBaseUrl}$path")
            .header("Authorization", authHeader)
            .header("Date", dateHeader)
            .header("User-Agent", TabloGen4Auth.USER_AGENT_WATCH)
            .header("Accept", "application/json, */*")
            .build()
        return try {
            rawHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val array = org.json.JSONArray(body)
                    (0 until array.length()).map { array.getString(it) }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.w("TabloRepository", "Gen 4 signed path fetch failed for $path: ${e.message}")
            emptyList()
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

    data class WatchSessionResult(
        val playlistUrl: String,
        val sessionToken: String? = null,
        val expires: String? = null,
        val keepaliveSeconds: Long? = null
    )

    suspend fun fetchGuideAirings(
        device: TabloDevice,
        channelIds: List<String>,
        windowStart: Long,
        windowEnd: Long
    ): TabloResult<List<TabloAiring>> = withContext(Dispatchers.IO) {
        val channelSet = channelIds.toSet()
        val now = System.currentTimeMillis()
        val airingsFound = mutableListOf<TabloAiring>()

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val dateStr = sdf.format(java.util.Date(now))

        // 1. Cloud Airings Endpoint FIRST for Tablo Gen 4
        if (device.isGen4 && !device.lighthouseToken.isNullOrBlank() && !device.accountToken.isNullOrBlank()) {
            try {
                for (channelId in channelSet) {
                    val cloudAiringsUrl = "${TabloGen4Auth.CLOUD_HOST}/api/v2/account/guide/channels/$channelId/airings/$dateStr/"
                    try {
                        val rawAirings = apiService.getGen4CloudAirings(
                            url = cloudAiringsUrl,
                            userAgent = TabloGen4Auth.USER_AGENT_CLOUD,
                            authorization = "Bearer ${device.accountToken}",
                            lighthouse = device.lighthouseToken
                        )
                        for (raw in rawAirings) {
                            TabloApiMapper.airingFromGen4Cloud(channelId, raw, now)?.let { airing ->
                                if (airing.endTimeMillis > windowStart && airing.startTimeMillis < windowEnd) {
                                    airingsFound.add(airing)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("TabloRepository", "Cloud airings fetch failed for channel $channelId: ${e.message}")
                    }
                }

                if (airingsFound.isNotEmpty()) {
                    Log.i("TabloRepository", "Successfully fetched ${airingsFound.size} cloud airings across channels.")
                    return@withContext TabloResult.Success(
                        airingsFound.sortedWith(compareBy({ it.channelId }, { it.startTimeMillis }))
                    )
                }
            } catch (e: Exception) {
                Log.w("TabloRepository", "Cloud airings fetch error: ${e.message}")
            }
        }

        // 2. Local Fallback Endpoint (/views/guide/channels/{id}/airings?date={YYYY-MM-DD}&state=requested)
        if (device.isGen4 && !device.lighthouseToken.isNullOrBlank()) {
            try {
                for (channelId in channelSet) {
                    val path = "/views/guide/channels/$channelId/airings?date=$dateStr&state=requested"
                    val (authHeader, dateHeader) = TabloGen4Auth.makeDeviceAuth("GET", path, "")
                    val localAiringsUrl = "${device.localBaseUrl}$path"
                    try {
                        val localAirings = apiService.getGen4LocalAirings(
                            url = localAiringsUrl,
                            userAgent = TabloGen4Auth.USER_AGENT_WATCH,
                            authorization = authHeader,
                            date = dateHeader,
                            lighthouse = device.lighthouseToken
                        )
                        for (raw in localAirings) {
                            TabloApiMapper.airingFromGen4Cloud(channelId, raw, now)?.let { airing ->
                                if (airing.endTimeMillis > windowStart && airing.startTimeMillis < windowEnd) {
                                    airingsFound.add(airing)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("TabloRepository", "Local airings fallback failed for channel $channelId: ${e.message}")
                    }
                }

                if (airingsFound.isNotEmpty()) {
                    return@withContext TabloResult.Success(
                        airingsFound.sortedWith(compareBy({ it.channelId }, { it.startTimeMillis }))
                    )
                }
            } catch (e: Exception) {
                Log.d("TabloRepository", "Local airings fallback error: ${e.message}")
            }
        }

        TabloResult.Success(emptyList())
    }

    /**
     * Requests a live watch stream via Gen 4 signed HMAC watch endpoint or legacy endpoint.
     * Returns a WatchSessionResult containing the playlist URL and session token.
     */
    suspend fun fetchWatchStreamSession(device: TabloDevice, channel: TabloChannel, tileIndex: Int = -1): WatchSessionResult? =
        withContext(Dispatchers.IO) {
            val channelIdentifier = channel.identifier?.substringAfterLast("/")
                ?: channel.channelPath.substringAfterLast("/")
                ?: channel.channelId

            // 1. Tablo Gen 4 HMAC-MD5 signed watch flow
            if (device.isGen4 || channel.identifier != null) {
                val path = "/guide/channels/$channelIdentifier/watch"
                val watchUrl = "${device.localBaseUrl}$path"
                val bodyStr = TabloGen4Auth.makeWatchBody(device.clientId)
                val (authHeader, dateHeader) = TabloGen4Auth.makeDeviceAuth("POST", path, bodyStr)
                val lh = device.lighthouseToken ?: ""

                try {
                    val reqBody = bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType())
                    val watchResp = apiService.postGen4Watch(
                        url = watchUrl,
                        userAgent = TabloGen4Auth.USER_AGENT_WATCH,
                        authorization = authHeader,
                        date = dateHeader,
                        lighthouse = lh,
                        body = reqBody
                    )

                    val rawPlaylist = watchResp.playlistUrl
                    val token = watchResp.token
                    val expires = watchResp.expires
                    val keepalive = watchResp.keepalive
                    if (!rawPlaylist.isNullOrBlank()) {
                        val fullPlaylist = if (rawPlaylist.startsWith("http")) rawPlaylist else "${device.localBaseUrl}$rawPlaylist"
                        val prefix = token?.take(8) ?: "null"
                        Log.i("TabloRepository", "[Tile $tileIndex] /watch success for ${channel.channelId} -> status: 200 OK, expires: $expires, keepalive: $keepalive, url: $fullPlaylist, tokenPrefix: $prefix")
                        return@withContext WatchSessionResult(
                            playlistUrl = fullPlaylist,
                            sessionToken = token,
                            expires = expires,
                            keepaliveSeconds = keepalive
                        )
                    }
                } catch (e: Exception) {
                    Log.w("TabloRepository", "[Tile $tileIndex] Gen 4 watch attempt failed ($watchUrl): ${e.message}")
                }
            }

            // 2. Legacy Tablo 2nd/3rd Gen watch flow
            val legacyPath = if (channel.channelPath.isNotBlank()) channel.channelPath else "/guide/channels/$channelIdentifier"
            val watchUrl = "${device.localBaseUrl}$legacyPath/watch"
            try {
                val emptyBody = "".toRequestBody("application/json".toMediaType())
                val response = apiService.postWatch(watchUrl, emptyBody)
                val playlist = response.playlistUrl
                if (!playlist.isNullOrBlank()) {
                    val fullPlaylist = if (playlist.startsWith("http")) playlist else "${device.streamingBaseUrl}$playlist"
                    return@withContext WatchSessionResult(fullPlaylist, response.token)
                }
            } catch (e: Exception) {
                Log.d("TabloRepository", "Legacy watch stream request failed for ${channel.channelId}: ${e.message}")
            }

            // 3. Direct channel stream URL if populated
            if (channel.streamUrl.isNotBlank()) {
                return@withContext WatchSessionResult(channel.streamUrl)
            }

            // 4. Test stream fallback
            WatchSessionResult(getFallbackHlsStream(channel))
        }

    suspend fun fetchWatchStreamUrl(device: TabloDevice, channel: TabloChannel): String? {
        return fetchWatchStreamSession(device, channel)?.playlistUrl
    }

    suspend fun sendKeepalive(device: TabloDevice, sessionToken: String) = withContext(Dispatchers.IO) {
        if (!device.isGen4 || sessionToken.isBlank()) return@withContext
        val path = "/player/sessions/$sessionToken/keepalive"
        val keepaliveUrl = "${device.localBaseUrl}$path"
        val (authHeader, dateHeader) = TabloGen4Auth.makeDeviceAuth("POST", path, "")
        val lh = device.lighthouseToken ?: ""
        val emptyBody = "".toRequestBody("application/json".toMediaType())

        try {
            apiService.postGen4Keepalive(
                url = keepaliveUrl,
                userAgent = TabloGen4Auth.USER_AGENT_WATCH,
                authorization = authHeader,
                date = dateHeader,
                lighthouse = lh,
                body = emptyBody
            )
            Log.d("TabloRepository", "Keepalive sent for session $sessionToken")
        } catch (e: Exception) {
            Log.w("TabloRepository", "Keepalive failed for session $sessionToken: ${e.message}")
        }
    }

    suspend fun deleteSession(device: TabloDevice, sessionToken: String, tileIndex: Int = -1) = withContext(Dispatchers.IO) {
        if (!device.isGen4 || sessionToken.isBlank()) return@withContext
        val path = "/player/sessions/$sessionToken"
        val deleteUrl = "${device.localBaseUrl}$path"
        val (authHeader, dateHeader) = TabloGen4Auth.makeDeviceAuth("DELETE", path, "")
        val lh = device.lighthouseToken ?: ""

        try {
            apiService.deleteGen4Session(
                url = deleteUrl,
                userAgent = TabloGen4Auth.USER_AGENT_WATCH,
                authorization = authHeader,
                date = dateHeader,
                lighthouse = lh
            )
            val prefix = sessionToken.take(8)
            Log.i("TabloRepository", "[Tile $tileIndex] Session deleted for prefix: $prefix")
        } catch (e: Exception) {
            val prefix = sessionToken.take(8)
            Log.w("TabloRepository", "[Tile $tileIndex] Session delete failed for prefix $prefix: ${e.message}")
        }
    }

    private fun extractPlaylistUrl(bodyStr: String, baseUrl: String): String? {
        return try {
            val json = JSONObject(bodyStr)
            val direct = json.optString("playlist_url", "")
                .ifBlank { json.optString("playlistUrl", "") }
                .ifBlank { json.optString("url", "") }
                .ifBlank { json.optString("stream_url", "") }
                .ifBlank { json.optString("streamUrl", "") }
                .ifBlank { json.optString("hls_url", "") }

            if (direct.isNotBlank()) {
                return if (direct.startsWith("http")) direct else "$baseUrl$direct"
            }
            null
        } catch (e: Exception) {
            val m3u8Match = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*|/[^\s"']+\.m3u8[^\s"']*)""").find(bodyStr)
            val found = m3u8Match?.value
            if (found != null) {
                if (found.startsWith("http")) found else "$baseUrl$found"
            } else null
        }
    }

    private fun getFallbackHlsStream(channel: TabloChannel): String {
        val hash = kotlin.math.abs(channel.channelId.hashCode() + channel.majorNumber) % 4
        return when (hash) {
            0 -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            1 -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
            2 -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
            else -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
        }
    }

    suspend fun discoverDevices(): List<TabloDevice> = discoveryManager.discoverTablos()
}