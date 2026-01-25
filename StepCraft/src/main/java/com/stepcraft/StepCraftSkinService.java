package com.stepcraft;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class StepCraftSkinService {
    private static final Gson GSON = new Gson();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final long CACHE_TTL_MS = 60 * 60 * 1000L;
    private static final long NEGATIVE_TTL_MS = 5 * 60 * 1000L;
    private static final ConcurrentHashMap<String, CachedProfile> CACHE = new ConcurrentHashMap<>();

    private StepCraftSkinService() {}

    public static CompletableFuture<GameProfile> fetchProfile(String username) {
        String key = username.toLowerCase();
        long now = System.currentTimeMillis();
        CachedProfile cached = CACHE.get(key);
        if (cached != null && (now - cached.cachedAt) <= cached.ttlMs) {
            return CompletableFuture.completedFuture(cached.profile);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                GameProfile profile = resolveProfile(username);
                if (profile != null) {
                    CACHE.put(key, new CachedProfile(profile, now, CACHE_TTL_MS));
                } else {
                    CACHE.put(key, new CachedProfile(null, now, NEGATIVE_TTL_MS));
                }
                return profile;
            } catch (Exception e) {
                CACHE.put(key, new CachedProfile(null, now, NEGATIVE_TTL_MS));
                return null;
            }
        });
    }

    private static GameProfile resolveProfile(String username) throws IOException {
        String uuid = fetchUuid(username);
        if (uuid == null) {
            return null;
        }

        String profileJson = fetchProfileJson(uuid);
        if (profileJson == null) {
            return null;
        }

        JsonObject root = GSON.fromJson(profileJson, JsonObject.class);
        if (root == null || !root.has("id")) {
            return null;
        }

        UUID id = parseUuid(root.get("id").getAsString());
        String name = root.has("name") ? root.get("name").getAsString() : username;
        GameProfile profile = new GameProfile(id, name);

        if (root.has("properties") && root.get("properties").isJsonArray()) {
            root.getAsJsonArray("properties").forEach(el -> {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    String propName = obj.has("name") ? obj.get("name").getAsString() : null;
                    String value = obj.has("value") ? obj.get("value").getAsString() : null;
                    String signature = obj.has("signature") ? obj.get("signature").getAsString() : null;
                    if (propName != null && value != null) {
                        if (signature != null && !signature.isBlank()) {
                            profile.getProperties().put(propName, new Property(propName, value, signature));
                        } else {
                            profile.getProperties().put(propName, new Property(propName, value));
                        }
                    }
                }
            });
        }

        return profile;
    }

    private static String fetchUuid(String username) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.mojang.com/users/profiles/minecraft/" + username)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            String body = response.body() != null ? response.body().string() : "";
            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root != null && root.has("id")) {
                return root.get("id").getAsString();
            }
            return null;
        }
    }

    private static String fetchProfileJson(String uuid) throws IOException {
        Request request = new Request.Builder()
                .url("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            return response.body() != null ? response.body().string() : null;
        }
    }

    private static UUID parseUuid(String raw) {
        String s = raw.replace("-", "");
        return UUID.fromString(s.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"
        ));
    }

    private static class CachedProfile {
        private final GameProfile profile;
        private final long cachedAt;
        private final long ttlMs;

        private CachedProfile(GameProfile profile, long cachedAt, long ttlMs) {
            this.profile = profile;
            this.cachedAt = cachedAt;
            this.ttlMs = ttlMs;
        }
    }
}
