package com.example.fitcollector

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class IngestPayload(
    val device_id: String,
    val steps_today: Long,
    val source: String = "health_connect"
)

data class IngestResponse(
    val ok: Boolean,
    val device_id: String,
    val day: String,
    val steps_today: Long
)

data class LatestResponse(
    val device_id: String,
    val day: String,
    val steps_today: Long,
    val source: String?,
    val created_at: String
)

interface FitApi {
    @GET("/health")
    suspend fun health(): Map<String, Any>

    @POST("/v1/ingest")
    suspend fun ingest(@Body payload: IngestPayload): IngestResponse

    @GET("/v1/latest/{deviceId}")
    suspend fun latest(@Path("deviceId") deviceId: String): LatestResponse
}

fun buildApi(baseUrl: String): FitApi {
    val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl) // must end with /
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(FitApi::class.java)
}
