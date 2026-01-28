package com.example.fitcollector

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.Interceptor
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val BASE_URL = "https://api.stepcraft.org/"
const val MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/"
const val GLOBAL_API_KEY = "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a"

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
    val server_name: String,
    val invite_code: String? = null
)

data class RegisterResponse(
    val player_api_key: String,
    val minecraft_username: String,
    val device_id: String,
    val server_name: String,
    val message: String
)

data class ClaimStatusResponse(
    val claimed: Boolean,
    val claimed_at: String?,
    val steps_claimed: Long?
)

data class StepsYesterdayResponse(
    val minecraft_username: String,
    val server_name: String,
    val steps_yesterday: Long,
    val day: String
)

data class PushNextResponse(
    val id: Long? = null,
    val server_name: String? = null,
    val message: String? = null,
    val scheduled_at: String? = null
)

data class MojangProfile(
    val id: String? = null,
    val name: String? = null,
    val errorMessage: String? = null
)

interface FitApi {
    @GET("health")
    suspend fun health(): HealthResp

    @POST("v1/ingest")
    suspend fun ingest(@Body payload: IngestPayload): IngestResponse

    @GET("v1/latest/{deviceId}")
    suspend fun latest(@Path("deviceId") deviceId: String): LatestResponse

    @GET("v1/servers/available")
    suspend fun getAvailableServers(@Query("invite_code") inviteCode: String? = null): AvailableServersResponse

    @POST("v1/players/register")
    suspend fun register(@Body payload: RegisterPayload): RegisterResponse

    @POST("v1/players/recover-key")
    suspend fun recoverKey(@Body payload: RegisterPayload): RegisterResponse

    @GET("v1/players/claim-status/{minecraft_username}")
    suspend fun getClaimStatus(
        @Path("minecraft_username") username: String,
        @Query("server_name") serverName: String
    ): ClaimStatusResponse

    @GET("v1/players/steps-yesterday")
    suspend fun getStepsYesterday(
        @Query("minecraft_username") username: String,
        @Query("player_api_key") apiKey: String
    ): StepsYesterdayResponse

    @GET("v1/players/push/next")
    suspend fun getNextPush(
        @Query("minecraft_username") username: String,
        @Query("device_id") deviceId: String,
        @Query("server_name") serverName: String,
        @Query("player_api_key") apiKey: String
    ): PushNextResponse
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

suspend fun validateMinecraftUsername(username: String): Boolean {
    return try {
        withContext(Dispatchers.IO) {
            val trimmed = username.trim()
            if (trimmed.isEmpty()) return@withContext false
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("$MOJANG_API_URL$encoded")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val responseCode = connection.responseCode

            val inputStream = if (responseCode in 200..399) connection.inputStream else connection.errorStream
            val response = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            android.util.Log.d("ValidateUsername", "URL=$url responseCode=$responseCode response=$response")
            val gson = com.google.gson.Gson()
            val profile = try { gson.fromJson(response, MojangProfile::class.java) } catch (e: Exception) { null }

            // Valid if it has an id field and no errorMessage
            profile?.id != null && profile.errorMessage == null
        }
    } catch (e: Exception) {
        android.util.Log.e("ValidateUsername", "Error validating username", e)
        false
    }
}

suspend fun fetchMinecraftProfile(username: String): MojangProfile? {
    return try {
        withContext(Dispatchers.IO) {
            val trimmed = username.trim()
            if (trimmed.isEmpty()) return@withContext null
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("$MOJANG_API_URL$encoded")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val responseCode = connection.responseCode

            val inputStream = if (responseCode in 200..399) connection.inputStream else connection.errorStream
            val response = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            android.util.Log.d("FetchMinecraftProfile", "URL=$url responseCode=$responseCode response=$response")
            val gson = com.google.gson.Gson()
            try { gson.fromJson(response, MojangProfile::class.java) } catch (e: Exception) { null }
        }
    } catch (e: Exception) {
        android.util.Log.e("FetchMinecraftProfile", "Error fetching profile", e)
        null
    }
}
