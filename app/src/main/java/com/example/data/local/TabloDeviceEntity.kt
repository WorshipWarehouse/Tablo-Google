package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tablo_device")
data class TabloDeviceEntity(
    @PrimaryKey val key: String = "default",
    val serverId: String,
    val name: String,
    val model: String,
    val host: String,
    val port: Int,
    val streamingPort: Int,
    val tunerCount: Int,
    val isConnected: Boolean,
    val firmware: String,
    val clientId: String = "",
    val isGen4: Boolean = false
)
