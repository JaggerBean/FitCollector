package com.stepcraft;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class StepCraftAutoClaimService {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stepcraft-autoclaim");
        t.setDaemon(true);
        return t;
    });

    private StepCraftAutoClaimService() {}

    public static void scheduleAutoClaim(ServerPlayerEntity player) {
        if (player == null) return;
        if (!StepCraftConfig.isApiKeyConfigured()) return;

        String username = player.getName().getString();
        if (!StepCraftAutoClaimStore.hasAnyEnabled(username)) return;

        SCHEDULER.schedule(() -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> attemptAutoClaim(player));
        }, 5, TimeUnit.SECONDS);
    }

    private static void attemptAutoClaim(ServerPlayerEntity player) {
        if (player == null) return;
        String username = player.getName().getString();
        Set<Long> enabled = StepCraftAutoClaimStore.getEnabledTiers(username);
        if (enabled.isEmpty()) return;

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    String json = BackendClient.getClaimAvailableForPlayer(username);
                    return StepCraftChestScreenHandler.parseClaimableItems(json);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .whenComplete((items, error) -> {
                if (error != null || items == null || items.isEmpty()) {
                    return;
                }

                List<StepCraftChestScreenHandler.ClaimableItem> toClaim = new ArrayList<>();
                for (StepCraftChestScreenHandler.ClaimableItem item : items) {
                    if (enabled.contains(item.minSteps())) {
                        toClaim.add(item);
                    }
                }

                if (toClaim.isEmpty()) return;

                for (StepCraftChestScreenHandler.ClaimableItem item : toClaim) {
                    StepCraftChestScreenHandler.claimSpecificRewardWithCommands(
                        player,
                        username,
                        item.day(),
                        item.minSteps(),
                        null,
                        null
                    );
                }
            });
    }
}