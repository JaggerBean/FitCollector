package com.stepcraft;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

public class BackendClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final String BASE_URL = "http://74.208.73.134"; // Change to your backend URL

    public static String healthCheck() throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/health")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "Error: " + response.code();
            }
            return response.body() != null ? response.body().string() : "No response body";
        }
    }

    // Server endpoint: get all players for this server
    public static String getAllPlayers() throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/v1/servers/players")
                .header("X-API-Key", StepCraftConfig.getApiKey())
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "Error: " + response.code();
            }
            return response.body() != null ? response.body().string() : "No response body";
        }
    }
}
