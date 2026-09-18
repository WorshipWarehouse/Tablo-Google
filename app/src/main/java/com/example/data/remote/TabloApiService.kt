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
    suspend fun getGuideStatus(
        @Url url: String
    ): TabloGuideStatusResponse

    @GET
    suspend fun getTuners(
        @Url url: String
    ): List<TabloTunerResponse>

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

    /**
     * Batch load a set of airing/show paths in a single request. Responses are
     * keyed by the submitted path and may contain nulls for unresolved paths.
     */
    @POST
    suspend fun postBatch(
        @Url url: String,
        @Body body: List<String>
    ): Map<String, TabloAiringDetailResponse?>

    /**
     * Batch load a set of channel paths in a single request.
     */
    @POST
    suspend fun postChannelBatch(
        @Url url: String,
        @Body body: List<String>
    ): Map<String, TabloChannelDetailResponse?>

    @POST
    suspend fun postWatch(
        @Url url: String,
        @Body body: RequestBody
    ): TabloWatchResponse

    @GET
    suspend fun getAssociationServerInfo(
        @Url url: String = "https://api.tablotv.com/assocserver/getipinfo/"
    ): TabloAssocInfoResponse
}