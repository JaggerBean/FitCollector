package com.stepcraft;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.concurrent.TimeUnit;

    


public class BackendClient {
    private static final Gson gson = new Gson();

    private static String executeRequest(Request request, boolean retryable) throws IOException {
        int maxAttempts = retryable ? 3 : 1;
        long backoffMs = 250L;
        IOException lastIo = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    return body.isEmpty() ? "No response body" : body;
                }

                if (retryable && (code == 502 || code == 503 || code == 504) && attempt < maxAttempts) {
                    backoffSleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }

                String message = "Error: " + code + " - " + response.message();
                if (!body.isBlank()) {
                    message += " - " + body;
                }
                return message;
            } catch (IOException e) {
                lastIo = e;
                if (retryable && attempt < maxAttempts) {
                    backoffSleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                throw e;
            }
        }

        throw lastIo != null ? lastIo : new IOException("Unknown network error");
    }

    private static void backoffSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
                            // Server endpoint: mark reward as claimed for a player
                            public static String claimRewardForPlayer(String username) throws IOException {
                                Request request = new Request.Builder()
                                        .url(BASE_URL + "/v1/servers/players/" + username + "/claim-reward")
                                        .header("X-API-Key", StepCraftConfig.getApiKey())
                                        .post(okhttp3.RequestBody.create(new byte[0]))
                                        .build();
                                return executeRequest(request, false);
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
                                return executeRequest(request, false);
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
                                return executeRequest(request, true);
                            }

                            // Server endpoint: unban a player
                            public static String unbanPlayer(String username) throws IOException {
                                Request request = new Request.Builder()
                                        .url(BASE_URL + "/v1/servers/players/" + username + "/ban")
                                        .header("X-API-Key", StepCraftConfig.getApiKey())
                                        .delete()
                                        .build();
                                return executeRequest(request, true);
                            }
                        // Server endpoint: list all registered players (paginated)
                        public static String getPlayersList() throws IOException {
                            Request request = new Request.Builder()
                                    .url(BASE_URL + "/v1/servers/players/list")
                                    .header("X-API-Key", StepCraftConfig.getApiKey())
                                    .build();
                            return executeRequest(request, true);
                        }
                    public static List<String> getRegisteredPlayerNames() throws IOException {
                        String json = getPlayersList();
                        List<String> names = new ArrayList<>();

                        try {
                            JsonObject root = gson.fromJson(json, JsonObject.class);
                            if (root != null && root.has("players") && root.get("players").isJsonArray()) {
                                JsonArray players = root.getAsJsonArray("players");
                                for (JsonElement element : players) {
                                    if (element != null && element.isJsonObject()) {
                                        JsonObject player = element.getAsJsonObject();
                                        if (player.has("minecraft_username")) {
                                            names.add(player.get("minecraft_username").getAsString());
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                            // Fallback: return empty list on parse errors
                        }

                        return names;
                    }
                    // Server endpoint: get all bans for this server
                    public static String getAllServerBans() throws IOException {
                        Request request = new Request.Builder()
                                .url(BASE_URL + "/v1/servers/bans")
                                .header("X-API-Key", StepCraftConfig.getApiKey())
                                .build();
                        return executeRequest(request, true);
                    }
                // Server endpoint: get yesterday's step count for a player
                public static String getYesterdayStepsForPlayer(String username) throws IOException {
                    Request request = new Request.Builder()
                            .url(BASE_URL + "/v1/servers/players/" + username + "/yesterday-steps")
                            .header("X-API-Key", StepCraftConfig.getApiKey())
                            .build();
                    return executeRequest(request, true);
                }
            // Server endpoint: get claim status for a player
            public static String getClaimStatusForPlayer(String username) throws IOException {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/v1/servers/players/" + username + "/claim-status")
                        .header("X-API-Key", StepCraftConfig.getApiKey())
                        .build();
                return executeRequest(request, true);
            }
        // Server endpoint: get server info
        public static String getServerInfo() throws IOException {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/v1/servers/info")
                    .header("X-API-Key", StepCraftConfig.getApiKey())
                    .build();
            return executeRequest(request, true);
        }
        private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build();
    private static final String BASE_URL = "https://api.stepcraft.org";
    public static String healthCheck() throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/health")
                .build();
        return executeRequest(request, true);
    }

    // Server endpoint: get all players for this server
    public static String getAllPlayers() throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/v1/servers/players")
                .header("X-API-Key", StepCraftConfig.getApiKey())
                .build();
        return executeRequest(request, true);
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
