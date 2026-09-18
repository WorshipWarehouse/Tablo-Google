package com.example.data.remote

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface TabloApiService {

    @GET
    suspend fun getServerInfo(
        @Url url: String
    ): TabloServerInfoResponse

    @GET
    suspend fun getTuners(
        @Url url: String
    ): Map<String, Any?>

    @GET
    suspend fun getChannelPaths(
        @Url url: String
    ): List<String>

    @GET
    suspend fun getChannelDetail(
        @Url url: String
    ): TabloChannelDetailResponse

    @GET
    suspend fun getAiringPaths(
        @Url url: String
    ): List<String>

    @GET
    suspend fun getAiringDetail(
        @Url url: String
    ): TabloAiringDetailResponse

    @POST
    suspend fun postWatch(
        @Url url: String,
        @Body body: RequestBody
    ): TabloWatchResponse

    @GET
    suspend fun getAssociationServerInfo(
        @Url url: String = "https://api.tablotv.com/assocserver/getserverinfo"
    ): TabloAssocServerResponse
}
