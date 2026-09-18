package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMultiviewDao {
    @Query("SELECT * FROM saved_multiviews ORDER BY createdAt DESC")
    fun getAllSavedMultiviews(): Flow<List<SavedMultiviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(multiview: SavedMultiviewEntity): Long

    @Update
    suspend fun update(multiview: SavedMultiviewEntity)

    @Query("DELETE FROM saved_multiviews WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE saved_multiviews SET name = :newName WHERE id = :id")
    suspend fun updateName(id: Long, newName: String)
}
