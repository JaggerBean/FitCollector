package com.example.fitcollector

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import okhttp3.Interceptor

data class IngestPayload(
    val minecraft_username: String,
    val device_id: String,
    val steps_today: Long,
    val player_api_key: String,
    val day: String,
    val source: String = "health_connect",
    val timestamp: String
)

data class IngestResponse(
    val ok: Boolean,
    val device_id: String,
    val day: String,
    val steps_today: Long
)

data class LatestResponse(
    val minecraft_username: String? = null,
    val device_id: String,
    val day: String,
    val steps_today: Long,
    val source: String? = null,
    val created_at: String? = null
)

data class HealthResp(val ok: Boolean)

data class ServerInfo(
    val server_name: String,
    val created_at: String? = null
)

data class AvailableServersResponse(
    val total_servers: Int,
    val servers: List<ServerInfo>
)

data class RegisterPayload(
    val minecraft_username: String,
    val device_id: String,
    val server_name: String
)

data class RegisterResponse(
    val player_api_key: String,
    val minecraft_username: String,
    val device_id: String,
    val server_name: String,
    val message: String
)

interface FitApi {
    @GET("health")
    suspend fun health(): HealthResp

    @POST("v1/ingest")
    suspend fun ingest(@Body payload: IngestPayload): IngestResponse

    @GET("v1/latest/{deviceId}")
    suspend fun latest(@Path("deviceId") deviceId: String): LatestResponse

    @GET("v1/servers/available")
    suspend fun getAvailableServers(): AvailableServersResponse

    @POST("v1/players/register")
    suspend fun register(@Body payload: RegisterPayload): RegisterResponse
}

fun buildApi(baseUrl: String, apiKey: String): FitApi {
    val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val authInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("X-API-Key", apiKey)
            .build()
        chain.proceed(req)
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logger)
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl) // MUST end with /
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(FitApi::class.java)
}
