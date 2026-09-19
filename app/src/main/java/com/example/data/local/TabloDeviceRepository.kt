package com.example.data.local

import com.example.model.TabloDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TabloDeviceRepository(private val dao: TabloDeviceDao) {

    val connectedDevice: Flow<TabloDevice?> = dao.observeDevice().map { it?.toModel() }

    suspend fun load(): TabloDevice? = dao.getDevice()?.toModel()

    suspend fun save(device: TabloDevice) {
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
                lighthouseToken = device.lighthouseToken,
                accountToken = device.accountToken,
                clientId = device.clientId,
                isGen4 = device.isGen4
            )
        )
    }

    suspend fun clear() {
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
    lighthouseToken = lighthouseToken,
    accountToken = accountToken,
    clientId = clientId,
    isGen4 = isGen4
)