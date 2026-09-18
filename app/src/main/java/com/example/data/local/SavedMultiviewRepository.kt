package com.example.data.local

import com.example.model.MultiviewLayoutType
import com.example.model.SavedMultiviewItem
import com.example.model.TabloChannel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SavedMultiviewRepository(private val dao: SavedMultiviewDao) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val channelListType = Types.newParameterizedType(List::class.java, TabloChannel::class.java)
    private val channelListAdapter = moshi.adapter<List<TabloChannel>>(channelListType)

    val savedMultiviews: Flow<List<SavedMultiviewItem>> = dao.getAllSavedMultiviews().map { entities ->
        entities.map { entity ->
            val channels = try {
                channelListAdapter.fromJson(entity.channelsJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val layoutType = try {
                MultiviewLayoutType.valueOf(entity.layoutType)
            } catch (e: Exception) {
                MultiviewLayoutType.GRID_2X2
            }
            SavedMultiviewItem(
                id = entity.id,
                name = entity.name,
                layoutType = layoutType,
                channels = channels,
                preferredAudioChannelId = entity.preferredAudioChannelId,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun saveMultiview(item: SavedMultiviewItem): Long {
        val json = channelListAdapter.toJson(item.channels)
        val entity = SavedMultiviewEntity(
            id = item.id,
            name = item.name,
            layoutType = item.layoutType.name,
            channelsJson = json,
            preferredAudioChannelId = item.preferredAudioChannelId,
            createdAt = item.createdAt
        )
        return dao.insert(entity)
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun rename(id: Long, newName: String) {
        dao.updateName(id, newName)
    }
}
