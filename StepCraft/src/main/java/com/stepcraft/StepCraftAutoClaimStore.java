package com.stepcraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class StepCraftAutoClaimStore {
    private static final String CONFIG_FILE = "config/stepcraft-autoclaim.json";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Set<Long>>>() {}.getType();
    private static Map<String, Set<Long>> enabledByPlayer = new HashMap<>();

    static {
        load();
    }

    private StepCraftAutoClaimStore() {}

    public static synchronized Set<Long> getEnabledTiers(String username) {
        if (username == null) return Collections.emptySet();
        String key = normalize(username);
        Set<Long> set = enabledByPlayer.get(key);
        if (set == null) return Collections.emptySet();
        return new LinkedHashSet<>(set);
    }

    public static synchronized boolean isTierEnabled(String username, long minSteps) {
        if (username == null) return false;
        String key = normalize(username);
        Set<Long> set = enabledByPlayer.get(key);
        return set != null && set.contains(minSteps);
    }

    public static synchronized void setTierEnabled(String username, long minSteps, boolean enabled) {
        if (username == null) return;
        String key = normalize(username);
        Set<Long> set = enabledByPlayer.computeIfAbsent(key, k -> new LinkedHashSet<>());
        if (enabled) {
            set.add(minSteps);
        } else {
            set.remove(minSteps);
            if (set.isEmpty()) {
                enabledByPlayer.remove(key);
            }
        }
        save();
    }

    public static synchronized boolean hasAnyEnabled(String username) {
        if (username == null) return false;
        String key = normalize(username);
        Set<Long> set = enabledByPlayer.get(key);
        return set != null && !set.isEmpty();
    }

    private static synchronized void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            enabledByPlayer = new HashMap<>();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Map<String, Set<Long>> data = GSON.fromJson(reader, MAP_TYPE);
            enabledByPlayer = (data != null) ? new HashMap<>(data) : new HashMap<>();
        } catch (IOException e) {
            enabledByPlayer = new HashMap<>();
        }
    }

    private static synchronized void save() {
        File file = new File(CONFIG_FILE);
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(enabledByPlayer, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase();
    }
}