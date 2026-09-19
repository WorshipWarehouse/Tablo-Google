package com.example.data.local

import kotlinx.coroutines.flow.Flow

class FavoriteChannelRepository(private val dao: FavoriteChannelDao) {
    val favoriteChannelIds: Flow<List<String>> = dao.getAllFavoriteChannelIds()

    suspend fun addFavorite(channelId: String) {
        dao.insertFavorite(FavoriteChannelEntity(channelId))
    }

    suspend fun removeFavorite(channelId: String) {
        dao.deleteFavorite(channelId)
    }
}
