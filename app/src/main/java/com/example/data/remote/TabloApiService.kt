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

    @POST
    suspend fun loginTabloCloud(
        @Url url: String,
        @Body body: TabloCloudLoginRequest
    ): TabloCloudLoginResponse

    @POST
    suspend fun loginGen4(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/2.0.0 (Mobile; iPhone; iOS 16.6)",
        @Body body: TabloGen4LoginRequest
    ): TabloGen4LoginResponse

    @GET
    suspend fun getGen4Account(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/2.0.0 (Mobile; iPhone; iOS 16.6)",
        @retrofit2.http.Header("Authorization") authorization: String
    ): TabloGen4AccountResponse

    @POST
    suspend fun selectGen4Account(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/2.0.0 (Mobile; iPhone; iOS 16.6)",
        @retrofit2.http.Header("Authorization") authorization: String,
        @Body body: TabloGen4SelectRequest
    ): TabloGen4SelectResponse

    @GET
    suspend fun getGen4Channels(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/2.0.0 (Mobile; iPhone; iOS 16.6)",
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Header("Lighthouse") lighthouse: String,
        @retrofit2.http.Header("Accept") accept: String = "*/*"
    ): List<TabloGen4CloudChannel>

    @GET
    suspend fun getGen4CloudAirings(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/2.0.0 (Mobile; iPhone; iOS 16.6)",
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Header("Lighthouse") lighthouse: String
    ): List<TabloGen4CloudAiring>

    @GET
    suspend fun getGen4LocalAirings(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/1.7.0",
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Header("Date") date: String,
        @retrofit2.http.Header("Lighthouse") lighthouse: String
    ): List<TabloGen4CloudAiring>

    @POST
    suspend fun postGen4Watch(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/1.7.0",
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Header("Date") date: String,
        @retrofit2.http.Header("Lighthouse") lighthouse: String,
        @Body body: RequestBody
    ): TabloGen4WatchResponse

    @POST
    suspend fun postGen4Keepalive(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/1.7.0",
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Header("Date") date: String,
        @retrofit2.http.Header("Lighthouse") lighthouse: String,
        @Body body: RequestBody
    ): okhttp3.ResponseBody

    @retrofit2.http.DELETE
    suspend fun deleteGen4Session(
        @Url url: String,
        @retrofit2.http.Header("User-Agent") userAgent: String = "Tablo-FAST/1.7.0",
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Header("Date") date: String,
        @retrofit2.http.Header("Lighthouse") lighthouse: String
    ): okhttp3.ResponseBody
}
