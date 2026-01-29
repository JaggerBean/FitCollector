package com.stepcraft;

import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.concurrent.TimeUnit;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;

    


public class BackendClient {
    private static final Gson gson = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger("stepcraft-backend");
    private static final long DNS_TTL_MS = 60_000;
    private static final ConcurrentHashMap<String, DnsEntry> DNS_CACHE = new ConcurrentHashMap<>();

    private static String executeRequest(Request request, boolean retryable) throws IOException {
        int maxAttempts = retryable ? 3 : 1;
        long backoffMs = 250L;
        IOException lastIo = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startNs = System.nanoTime();
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "";
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
                LOGGER.info("HTTP {} {} -> {} in {} ms (attempt {}/{})",
                        request.method(), request.url(), code, elapsedMs, attempt, maxAttempts);

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
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
                LOGGER.warn("HTTP {} {} failed in {} ms (attempt {}/{}): {}",
                        request.method(), request.url(), elapsedMs, attempt, maxAttempts, e.getMessage());
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
                            public static String claimRewardForPlayer(String username, long minSteps, String day) throws IOException {
                                String url = BASE_URL + "/v1/servers/players/" + username + "/claim-reward?min_steps=" + minSteps;
                                if (day != null && !day.isBlank()) {
                                    url += "&day=" + java.net.URLEncoder.encode(day, java.nio.charset.StandardCharsets.UTF_8);
                                }
                                Request request = new Request.Builder()
                                        .url(url)
                                        .header("X-API-Key", StepCraftConfig.getApiKey())
                                        .post(okhttp3.RequestBody.create(new byte[0]))
                                        .build();
                                return executeRequest(request, false);
                            }

                            // Server endpoint: get rewards configuration
                            public static String getServerRewards() throws IOException {
                                Request request = new Request.Builder()
                                        .url(BASE_URL + "/v1/servers/rewards")
                                        .header("X-API-Key", StepCraftConfig.getApiKey())
                                        .build();
                                return executeRequest(request, true);
                            }

                            // Server endpoint: seed default rewards configuration
                            public static String seedServerRewards() throws IOException {
                                Request request = new Request.Builder()
                                        .url(BASE_URL + "/v1/servers/rewards/default")
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

                        public static String getPlayersList(int limit, int offset, String query) throws IOException {
                            StringBuilder url = new StringBuilder(BASE_URL + "/v1/servers/players/list")
                                    .append("?limit=").append(limit)
                                    .append("&offset=").append(offset);
                            if (query != null && !query.isBlank()) {
                                url.append("&q=").append(java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8));
                            }

                            Request request = new Request.Builder()
                                    .url(url.toString())
                                    .header("X-API-Key", StepCraftConfig.getApiKey())
                                    .build();
                            return executeRequest(request, true);
                        }
                    public static PlayerListPage getRegisteredPlayerNamesPage(int limit, int offset, String query) throws IOException {
                        String json = getPlayersList(limit, offset, query);
                        List<String> names = new ArrayList<>();
                        int total = 0;

                        try {
                            JsonObject root = gson.fromJson(json, JsonObject.class);
                            if (root != null) {
                                if (root.has("total_players")) {
                                    total = root.get("total_players").getAsInt();
                                }
                                if (root.has("players") && root.get("players").isJsonArray()) {
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
                            }
                        } catch (Exception ignored) {
                            // Fallback: return empty list on parse errors
                        }

                        return new PlayerListPage(names, total);
                    }

                    public static class PlayerListPage {
                        public final List<String> names;
                        public final int total;

                        public PlayerListPage(List<String> names, int total) {
                            this.names = names;
                            this.total = total;
                        }
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
            public static String getClaimStatusForPlayer(String username, long minSteps, String day) throws IOException {
                String url = BASE_URL + "/v1/servers/players/" + username + "/claim-status?min_steps=" + minSteps;
                if (day != null && !day.isBlank()) {
                    url += "&day=" + java.net.URLEncoder.encode(day, java.nio.charset.StandardCharsets.UTF_8);
                }
                Request request = new Request.Builder()
                        .url(url)
                        .header("X-API-Key", StepCraftConfig.getApiKey())
                        .build();
                return executeRequest(request, true);
            }
        // Server endpoint: list claimable tiers for a player
        public static String getClaimAvailableForPlayer(String username) throws IOException {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/v1/servers/players/" + username + "/claim-available")
                    .header("X-API-Key", StepCraftConfig.getApiKey())
                    .build();
            return executeRequest(request, true);
        }

        public static String getClaimAvailableForPlayer(String username, boolean debug) throws IOException {
            String url = BASE_URL + "/v1/servers/players/" + username + "/claim-available";
            if (debug) {
                url += "?debug=true";
            }
            Request request = new Request.Builder()
                    .url(url)
                    .header("X-API-Key", StepCraftConfig.getApiKey())
                    .build();
            return executeRequest(request, true);
        }

        public static String getClaimStatusListForPlayer(String username) throws IOException {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/v1/servers/players/" + username + "/claim-status-list")
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
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .dispatcher(buildDispatcher())
                .cache(new Cache(new File("config/stepcraft-http-cache"), 10L * 1024L * 1024L))
                .dns(new Dns() {
                    @Override
                    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                        long now = System.currentTimeMillis();
                        DnsEntry cached = DNS_CACHE.get(hostname);
                        if (cached != null && (now - cached.cachedAt) <= DNS_TTL_MS) {
                            return cached.addresses;
                        }

                        List<InetAddress> all = Dns.SYSTEM.lookup(hostname);
                        List<InetAddress> v4 = new ArrayList<>();
                        List<InetAddress> v6 = new ArrayList<>();
                        for (InetAddress addr : all) {
                            if (addr instanceof Inet4Address) {
                                v4.add(addr);
                            } else {
                                v6.add(addr);
                            }
                        }
                        v4.addAll(v6);
                        DNS_CACHE.put(hostname, new DnsEntry(v4, now));
                        return v4;
                    }
                })
            .build();

        private static Dispatcher buildDispatcher() {
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequests(64);
            dispatcher.setMaxRequestsPerHost(16);
            return dispatcher;
        }

        private static class DnsEntry {
            private final List<InetAddress> addresses;
            private final long cachedAt;

            private DnsEntry(List<InetAddress> addresses, long cachedAt) {
                this.addresses = addresses;
                this.cachedAt = cachedAt;
            }
        }
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
