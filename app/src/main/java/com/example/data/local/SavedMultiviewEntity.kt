package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_multiviews")
data class SavedMultiviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val layoutType: String,
    val channelsJson: String,
    val preferredAudioChannelId: String,
    val createdAt: Long = System.currentTimeMillis()
)
