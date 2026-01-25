package com.stepcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StepCraftMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("stepcraft");

    @Override
    public void onInitialize() {
        LOGGER.info("Hello Fabric world!");

        // Load config (API key)
        StepCraftConfig.load();

        // Test backend communication
        try {
            String health = BackendClient.healthCheck();
            LOGGER.info("Backend health: {}", health);
        } catch (Exception e) {
            LOGGER.error("Failed to contact backend: ", e);
        }

        // Register admin command(s)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            StepCraftCommands.register(dispatcher);
        });
    }
}
