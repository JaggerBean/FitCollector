package com.stepcraft;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import com.google.gson.Gson;

    


public class BackendClient {
    private static final Gson gson = new Gson();
                            // Server endpoint: mark reward as claimed for a player
                            public static String claimRewardForPlayer(String username) throws IOException {
                                Request request = new Request.Builder()
                                        .url(BASE_URL + "/v1/servers/players/" + username + "/claim-reward")
                                        .header("X-API-Key", StepCraftConfig.getApiKey())
                                        .post(okhttp3.RequestBody.create(new byte[0]))
                                        .build();
                                try (Response response = client.newCall(request).execute()) {
                                    if (!response.isSuccessful()) {
                                        return "Error: " + response.code() + " - " + response.message();
                                    }
                                    return response.body() != null ? response.body().string() : "No response body";
                                }
                            }

                            // Server endpoint: ban a player (with optional reason)
                            public static String banPlayer(String username, String reason) throws IOException {
                                okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
                                String json = gson.toJson(new BanReason(reason));
                                Request request = new Request.Builder()
                                        .url(BASE_URL + "/v1/servers/players/" + username + "/ban")
                                        .header("X-API-Key", StepCraftConfig.getApiKey())
                                        .post(okhttp3.RequestBody.create(json, JSON))
                                        .build();
                                try (Response response = client.newCall(request).execute()) {
                                    if (!response.isSuccessful()) {
                                        return "Error: " + response.code() + " - " + response.message();
                                    }
                                    return response.body() != null ? response.body().string() : "No response body";
                                }
                            }

                            private static class BanReason {
                                String reason;
                                BanReason(String reason) { this.reason = reason; }
                            }

                            // Server endpoint: delete a player's data
                            public static String deletePlayer(String username) throws IOException {
                                Request request = new Request.Builder()
                                        .url(BASE_URL + "/v1/servers/players/" + username)
                                        .header("X-API-Key", StepCraftConfig.getApiKey())
                                        .delete()
                                        .build();
                                try (Response response = client.newCall(request).execute()) {
                                    if (!response.isSuccessful()) {
                                        return "Error: " + response.code() + " - " + response.message();
                                    }
                                    return response.body() != null ? response.body().string() : "No response body";
                                }
                            }

                            // Server endpoint: unban a player
                            public static String unbanPlayer(String username) throws IOException {
                                Request request = new Request.Builder()
                                        .url(BASE_URL + "/v1/servers/players/" + username + "/ban")
                                        .header("X-API-Key", StepCraftConfig.getApiKey())
                                        .delete()
                                        .build();
                                try (Response response = client.newCall(request).execute()) {
                                    if (!response.isSuccessful()) {
                                        return "Error: " + response.code() + " - " + response.message();
                                    }
                                    return response.body() != null ? response.body().string() : "No response body";
                                }
                            }
                        // Server endpoint: list all registered players (paginated)
                        public static String getPlayersList() throws IOException {
                            Request request = new Request.Builder()
                                    .url(BASE_URL + "/v1/servers/players/list")
                                    .header("X-API-Key", StepCraftConfig.getApiKey())
                                    .build();
                            try (Response response = client.newCall(request).execute()) {
                                if (!response.isSuccessful()) {
                                    return "Error: " + response.code() + " - " + response.message();
                                }
                                return response.body() != null ? response.body().string() : "No response body";
                            }
                        }
                    // Server endpoint: get all bans for this server
                    public static String getAllServerBans() throws IOException {
                        Request request = new Request.Builder()
                                .url(BASE_URL + "/v1/servers/bans")
                                .header("X-API-Key", StepCraftConfig.getApiKey())
                                .build();
                        try (Response response = client.newCall(request).execute()) {
                            if (!response.isSuccessful()) {
                                return "Error: " + response.code() + " - " + response.message();
                            }
                            return response.body() != null ? response.body().string() : "No response body";
                        }
                    }
                // Server endpoint: get yesterday's step count for a player
                public static String getYesterdayStepsForPlayer(String username) throws IOException {
                    Request request = new Request.Builder()
                            .url(BASE_URL + "/v1/servers/players/" + username + "/yesterday-steps")
                            .header("X-API-Key", StepCraftConfig.getApiKey())
                            .build();
                    try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            return "Error: " + response.code() + " - " + response.message();
                        }
                        return response.body() != null ? response.body().string() : "No response body";
                    }
                }
            // Server endpoint: get claim status for a player
            public static String getClaimStatusForPlayer(String username) throws IOException {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/v1/servers/players/" + username + "/claim-status")
                        .header("X-API-Key", StepCraftConfig.getApiKey())
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        return "Error: " + response.code() + " - " + response.message();
                    }
                    return response.body() != null ? response.body().string() : "No response body";
                }
            }
        // Server endpoint: get server info
        public static String getServerInfo() throws IOException {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/v1/servers/info")
                    .header("X-API-Key", StepCraftConfig.getApiKey())
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "Error: " + response.code();
                }
                return response.body() != null ? response.body().string() : "No response body";
            }
        }
    private static final OkHttpClient client = new OkHttpClient();
    private static final String BASE_URL = "https://api.stepcraft.org";
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
        // Called from the chest UI to send server info to the admin
    public static void sendInfoCommand(net.minecraft.server.network.ServerPlayerEntity admin, String playerName) {
        // For now, just call getServerInfo and send the result to the admin
        // (You can later change this to a player-specific info endpoint if available)
        try {
            String result = getServerInfo();
            admin.sendMessage(net.minecraft.text.Text.literal("Server info: " + result));
        } catch (Exception e) {
            admin.sendMessage(net.minecraft.text.Text.literal("Error: " + e.getMessage()));
        }
    }
}
