package com.example.data.local

import android.content.Context
import com.example.model.TabloDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TabloDeviceRepository(
    context: Context,
    private val dao: TabloDeviceDao
) {
    private val tokenStore = SecureTabloTokenStore(context.applicationContext)

    val connectedDevice: Flow<TabloDevice?> = dao.observeDevice().map { it?.toModel() }

    suspend fun load(): TabloDevice? = dao.getDevice()?.toModel()?.let { device ->
        tokenStore.restore(device)
    }

    suspend fun save(device: TabloDevice) {
        tokenStore.save(device)
        dao.upsert(
            TabloDeviceEntity(
                serverId = device.serverId,
                name = device.name,
                model = device.model,
                host = device.host,
                port = device.port,
                streamingPort = device.streamingPort,
                tunerCount = device.tunerCount,
                isConnected = device.isConnected,
                firmware = device.firmware,
                clientId = device.clientId,
                isGen4 = device.isGen4
            )
        )
    }

    suspend fun clear() {
        tokenStore.clear()
        dao.clear()
    }
}

private fun TabloDeviceEntity.toModel(): TabloDevice = TabloDevice(
    serverId = serverId,
    name = name,
    model = model,
    host = host,
    port = port,
    streamingPort = streamingPort,
    tunerCount = tunerCount,
    activeTuners = 0,
    isConnected = isConnected,
    firmware = firmware,
    clientId = clientId,
    isGen4 = isGen4
)
