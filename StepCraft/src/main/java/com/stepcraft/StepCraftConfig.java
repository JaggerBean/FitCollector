package com.stepcraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class StepCraftConfig {
    private static final String CONFIG_FILE = "config/stepcraft.properties";
    private static final String API_KEY_PROPERTY = "api_key";
    private static String apiKey = null;

    public static void load() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
                props.setProperty(API_KEY_PROPERTY, "PUT_YOUR_API_KEY_HERE");
                try (FileWriter writer = new FileWriter(file)) {
                    props.store(writer, "StepCraft Mod Configuration");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
            apiKey = props.getProperty(API_KEY_PROPERTY, "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getApiKey() {
        return apiKey;
    }

    public static boolean isApiKeyConfigured() {
        String key = apiKey;
        return key != null && !key.isBlank() && !"PUT_YOUR_API_KEY_HERE".equals(key);
    }

    public static synchronized void setApiKey(String newKey) {
        apiKey = newKey;
        save();
    }

    private static synchronized void save() {
        Properties props = new Properties();
        props.setProperty(API_KEY_PROPERTY, apiKey == null ? "" : apiKey);

        File file = new File(CONFIG_FILE);
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, "StepCraft Mod Configuration");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
