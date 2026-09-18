package com.example.data.remote

import android.util.Log
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TabloApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    suspend fun getServerInfo(host: String, port: Int = 8885): TabloDevice? = withContext(Dispatchers.IO) {
        val url = "http://$host:$port/server/info"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val serverId = json.optString("server_id", "tablo-${host.replace(".", "-")}")
                val name = json.optString("name", "Tablo TV")
                val model = json.optString("model", "Tablo DUAL / QUAD")
                val tuners = json.optInt("tuners", 4)
                val version = json.optString("version", "2.2.44")
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
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d("TabloApiClient", "Could not fetch server info from $url: ${e.message}")
            null
        }
    }

    suspend fun getTunerStatus(device: TabloDevice): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val url = "${device.localBaseUrl}/server/tuners"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext Pair(0, device.tunerCount)
                val json = JSONObject(body)
                var active = 0
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val tunerObj = json.optJSONObject(key)
                    if (tunerObj?.optBoolean("in_use", false) == true) {
                        active++
                    }
                }
                Pair(active, device.tunerCount)
            } else {
                Pair(0, device.tunerCount)
            }
        } catch (e: Exception) {
            Pair(0, device.tunerCount)
        }
    }

    suspend fun getChannels(device: TabloDevice): List<TabloChannel> = withContext(Dispatchers.IO) {
        val url = "${device.localBaseUrl}/guide/channels"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                val channels = mutableListOf<TabloChannel>()
                for (i in 0 until jsonArray.length()) {
                    val path = jsonArray.optString(i)
                    if (path.isNotEmpty()) {
                        val channelDetail = fetchChannelDetail(device, path)
                        if (channelDetail != null) {
                            channels.add(channelDetail)
                        }
                    }
                }
                if (channels.isNotEmpty()) return@withContext channels
            }
        } catch (e: Exception) {
            Log.d("TabloApiClient", "Error loading channels from Tablo: ${e.message}")
        }
        emptyList()
    }

    private suspend fun fetchChannelDetail(device: TabloDevice, path: String): TabloChannel? = withContext(Dispatchers.IO) {
        val fullUrl = if (path.startsWith("http")) path else "${device.localBaseUrl}$path"
        try {
            val request = Request.Builder().url(fullUrl).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val channelObj = json.optJSONObject("channel") ?: json
                val callSign = channelObj.optString("call_sign", "OTA")
                val major = channelObj.optInt("major", 1)
                val minor = channelObj.optInt("minor", 1)
                val network = channelObj.optString("network", callSign)
                val resolution = channelObj.optString("resolution", "1080i")
                val id = path.substringAfterLast("/")
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
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getWatchStreamUrl(device: TabloDevice, channel: TabloChannel): String = withContext(Dispatchers.IO) {
        val watchUrl = "${device.localBaseUrl}${channel.channelPath}/watch"
        try {
            val emptyBody = "".toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(watchUrl).post(emptyBody).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val playlistUrl = json.optString("playlist_url")
                if (playlistUrl.isNotEmpty()) {
                    return@withContext if (playlistUrl.startsWith("http")) {
                        playlistUrl
                    } else {
                        "http://${device.host}:${device.streamingPort}$playlistUrl"
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("TabloApiClient", "Failed to start watch stream: ${e.message}")
        }
        // Fallback live stream URL formatted for Tablo standard port 80
        "http://${device.host}:${device.streamingPort}/stream/pl.m3u8?channel=${channel.channelId}"
    }

    suspend fun getGuideAirings(device: TabloDevice, channelIds: List<String>): List<TabloAiring> = withContext(Dispatchers.IO) {
        val airings = mutableListOf<TabloAiring>()
        val url = "${device.localBaseUrl}/guide/airings"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                val count = minOf(jsonArray.length(), 20)
                for (i in 0 until count) {
                    val path = jsonArray.optString(i)
                    if (path.isNotEmpty()) {
                        val airingDetail = fetchAiringDetail(device, path)
                        if (airingDetail != null) {
                            airings.add(airingDetail)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("TabloApiClient", "Error fetching airings: ${e.message}")
        }
        airings
    }

    private suspend fun fetchAiringDetail(device: TabloDevice, path: String): TabloAiring? = withContext(Dispatchers.IO) {
        val fullUrl = if (path.startsWith("http")) path else "${device.localBaseUrl}$path"
        try {
            val request = Request.Builder().url(fullUrl).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val airingObj = json.optJSONObject("airing_details") ?: json
                val showObj = json.optJSONObject("show")
                val title = showObj?.optString("title") ?: airingObj.optString("title", "Live Broadcast")
                val episodeTitle = json.optJSONObject("episode")?.optString("title")
                val description = json.optJSONObject("episode")?.optString("description")
                    ?: json.optString("description", "")
                val duration = airingObj.optLong("duration", 1800L)
                val channelPath = json.optString("channel_path", "")
                val channelId = channelPath.substringAfterLast("/")
                TabloAiring(
                    airingId = path.substringAfterLast("/"),
                    channelId = channelId,
                    title = title,
                    episodeTitle = episodeTitle,
                    description = description,
                    startTimeMillis = System.currentTimeMillis() - 10 * 60 * 1000L,
                    durationSeconds = duration,
                    category = "Network Live",
                    isLive = true
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
