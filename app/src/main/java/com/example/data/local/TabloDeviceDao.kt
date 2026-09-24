package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TabloDeviceDao {
    @Query("SELECT * FROM tablo_device WHERE key = 'default' LIMIT 1")
    fun observeDevice(): Flow<TabloDeviceEntity?>

    @Query("SELECT * FROM tablo_device WHERE key = 'default' LIMIT 1")
    suspend fun getDevice(): TabloDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TabloDeviceEntity)

    @Query("DELETE FROM tablo_device")
    suspend fun clear()
}