package com.example.data.remote

import android.util.Log
import com.example.model.TabloDevice
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Discovers Tablo DVRs on the local network using the two mechanisms documented
 * in the Tablo API docs:
 *
 * 1. Tablo's hosted discovery service (https://api.tablotv.com/assocserver/getipinfo/)
 * 2. A UDP broadcast of "BnGr" to port 8881, listening for the fixed-format
 *    reply packet on port 8882.
 *
 * No device is ever fabricated: entries are included only when the association
 * service or the network responses report them.
 */
class TabloDiscoveryManager(
    private val apiService: TabloApiService = createDefaultApiService()
) {

    companion object {
        private const val UDP_DISCOVERY_PORT = 8881
        private const val UDP_REPLY_PORT = 8882
        private const val UDP_TIMEOUT_MS = 600

        fun createDefaultApiService(): TabloApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
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

    suspend fun discoverTablos(): List<TabloDevice> = withContext(Dispatchers.IO) {
        val byHost = mutableMapOf<String, TabloDevice>()

        // 1. Hosted association-server discovery (documented getipinfo endpoint).
        try {
            val assoc = apiService.getAssociationServerInfo()
            if (assoc.success != false) {
                assoc.cpes?.forEach { cpe ->
                    val host = cpe.privateIp?.trim().orEmpty()
                    if (host.isEmpty()) return@forEach
                    val verified = verifyServerInfo(host)
                    byHost[host] = verified ?: TabloDevice(
                        serverId = cpe.serverId ?: "tablo-$host",
                        name = cpe.name?.ifBlank { null } ?: cpe.host?.ifBlank { null } ?: "Tablo ($host)",
                        model = cpe.board ?: "Tablo",
                        host = host,
                        tunerCount = 4,
                        isConnected = false,
                        firmware = cpe.serverVersion ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("TabloDiscoveryManager", "Association server lookup failed: ${e.message}")
        }

        // 2. Local UDP broadcast discovery.
        if (byHost.isEmpty()) {
            discoverViaUdp().forEach { device ->
                if (device.host.isNotEmpty() && byHost.put(device.host, device) != null) {
                    Log.d("TabloDiscoveryManager", "Tablo found via UDP at ${device.host}")
                }
            }
        }

        byHost.values.toList()
    }

    private suspend fun verifyServerInfo(host: String): TabloDevice? {
        return try {
            val response = apiService.getServerInfo("http://$host:8885/server/info")
            val model = response.model
            TabloDevice(
                serverId = response.serverId ?: "tablo-$host",
                name = response.name?.ifBlank { null } ?: model?.name?.ifBlank { null } ?: "Tablo ($host)",
                model = model?.name ?: model?.type ?: "Tablo",
                host = host,
                port = 8885,
                streamingPort = 80,
                tunerCount = model?.tuners ?: 4,
                activeTuners = 0,
                isConnected = true,
                firmware = response.version ?: ""
            )
        } catch (e: Exception) {
            Log.d("TabloDiscoveryManager", "Server info verification failed for $host: ${e.message}")
            null
        }
    }

    private suspend fun discoverViaUdp(): List<TabloDevice> {
        val found = mutableListOf<TabloDevice>()
        val listenSocket = DatagramSocket()
        try {
            listenSocket.broadcast = true
            listenSocket.soTimeout = UDP_TIMEOUT_MS

            // Send the documented "BnGr" discovery packet to the broadcast address.
            val message = "BnGr".toByteArray(StandardCharsets.US_ASCII)
            val packet = DatagramPacket(
                message,
                message.size,
                InetAddress.getByName("255.255.255.255"),
                UDP_DISCOVERY_PORT
            )
            listenSocket.send(packet)

            val buffer = ByteArray(1024)
            val endTime = System.currentTimeMillis() + UDP_TIMEOUT_MS
            while (System.currentTimeMillis() < endTime) {
                val receivePacket = DatagramPacket(buffer, buffer.size)
                try {
                    listenSocket.receive(receivePacket)
                } catch (e: Exception) {
                    break
                }
                val data = receivePacket.data
                val length = receivePacket.length
                val sourceIp = receivePacket.address.hostAddress ?: continue
                if (length < 140) continue

                val host = readFixedString(data, 4, 64)
                val privateIp = readFixedString(data, 68, 32).ifEmpty { sourceIp }
                val serverId = readFixedString(data, 100, 20)
                val devType = readFixedString(data, 120, 10)
                val board = readFixedString(data, 130, 10)
                if (devType.isNotEmpty() && devType != "tablo") continue

                val verified = verifyServerInfo(privateIp)
                found.add(
                    verified ?: TabloDevice(
                        serverId = serverId.ifEmpty { "tablo-$privateIp" },
                        name = host.ifBlank { "Tablo ($privateIp)" },
                        model = board.ifBlank { "Tablo" },
                        host = privateIp,
                        isConnected = false
                    )
                )
            }
        } catch (e: Exception) {
            Log.d("TabloDiscoveryManager", "UDP discovery failed: ${e.message}")
        } finally {
            runCatching { listenSocket.close() }
        }
        return found.distinctBy { it.host }
    }

    private fun readFixedString(data: ByteArray, offset: Int, length: Int): String {
        if (offset + length > data.size) return ""
        val end = (offset until offset + length).indexOfFirst { data[it] == 0.toByte() }
        val actualEnd = if (end == -1) offset + length else offset + end
        return String(data, offset, actualEnd - offset, StandardCharsets.UTF_8)
    }
}