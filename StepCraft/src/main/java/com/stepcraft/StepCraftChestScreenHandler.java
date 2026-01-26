package com.stepcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

public class StepCraftChestScreenHandler extends GenericContainerScreenHandler {
    private final SimpleInventory inventory;
    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stepcraft-ui");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService BACKEND_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "stepcraft-backend");
        t.setDaemon(true);
        return t;
    });

    // Server constructor only (client uses vanilla screen + vanilla type)
    public StepCraftChestScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.inventory = inventory;
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (slot >= 0 && slot < inventory.size()) {
            ItemStack clicked = inventory.getStack(slot);

            if (!clicked.isEmpty()
                    && player instanceof ServerPlayerEntity serverPlayer
                    && serverPlayer.hasPermissionLevel(4)) {

                if (clicked.isOf(net.minecraft.item.Items.PURPLE_STAINED_GLASS_PANE)) {
                    return;
                }

                switch (slot) {
                    case 1 -> { sendBackendToLectern(serverPlayer, "Server info", BackendClient::getServerInfo); return; }
                    case 3 -> { sendBackendToLectern(serverPlayer, "Health check", BackendClient::healthCheck); return; }
                    case 5 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.BAN); return; }
                    case 7 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.DELETE); return; }
                    case 10 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.UNBAN); return; }
                    case 12 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.CLAIM_REWARD); return; }
                    case 14 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.CLAIM_STATUS); return; }
                    case 16 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.NONE); return; }
                    case 19 -> { sendBackendToLectern(serverPlayer, "Server bans", BackendClient::getAllServerBans); return; }
                    case 21 -> { sendBackendToLectern(serverPlayer, "All players", BackendClient::getAllPlayers); return; }
                    case 23 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.YESTERDAY_STEPS); return; }
                    case 25 -> { StepCraftScreens.openSettings(serverPlayer); return; }
                }
            }
        }

        super.onSlotClick(slot, button, actionType, player);
    }

    // Convenience helper to open the GUI (server-only safe)
    public static void open(ServerPlayerEntity player, DefaultedList<ItemStack> items, Text title) {
        StepCraftScreens.openAdminChest(player, items, title);
    }

    public interface BackendCall {
        String run() throws Exception;
    }

    public static void sendBackend(ServerPlayerEntity player, String prefix, BackendCall call) {
        AtomicBoolean completed = new AtomicBoolean(false);
        ScheduledFuture<?> pendingMessage = SCHEDULER.schedule(() -> {
            if (!completed.get()) {
                player.getServer().execute(() ->
                        player.sendMessage(Text.literal(prefix + "(processing...)"))
                );
            }
        }, 1, TimeUnit.SECONDS);

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return call.run();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, BACKEND_EXECUTOR)
                .whenComplete((result, error) -> player.getServer().execute(() -> {
                    completed.set(true);
                    pendingMessage.cancel(false);
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        player.sendMessage(Text.literal("Error: " + cause.getMessage()));
                    } else {
                        player.sendMessage(Text.literal(prefix + result));
                    }
                }));
    }

        public static void sendBackendToLectern(ServerPlayerEntity player, String title, BackendCall call) {
        openLecternMessage(player, title + ": (processing...)");
        sendBackendWithCallback(player, call,
            result -> openLecternMessage(player, title + ": " + result),
            error -> openLecternMessage(player, "Error: " + error)
        );
        }

        private static void openLecternMessage(ServerPlayerEntity player, String message) {
        StepCraftLecternHelper.openLectern(player, "Result",
            StepCraftResultScreenHandler.toPagesFromLines(
                StepCraftResultScreenHandler.toDisplayLines(message)
            )
        );
        }

    public static void sendBackendWithCallback(ServerPlayerEntity player, BackendCall call,
                                                java.util.function.Consumer<String> onSuccess,
                                                java.util.function.Consumer<String> onError) {
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return call.run();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, BACKEND_EXECUTOR)
                .whenComplete((result, error) -> player.getServer().execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        if (onError != null) {
                            onError.accept(cause.getMessage());
                        }
                    } else {
                        if (onSuccess != null) {
                            onSuccess.accept(result);
                        }
                    }
                }));
    }

    public static void claimRewardWithCommands(ServerPlayerEntity player, String targetPlayer,
                                               java.util.function.Consumer<String> onSuccess,
                                               java.util.function.Consumer<String> onError) {
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        String claimJson = BackendClient.getClaimStatusForPlayer(targetPlayer);
                        ClaimStatus claimStatus = extractClaimStatus(claimJson);
                        if (claimStatus.claimed) {
                            return new ClaimContext(-1, null, true, claimStatus.claimedAt);
                        }
                        String stepsJson = BackendClient.getYesterdayStepsForPlayer(targetPlayer);
                        long steps = extractSteps(stepsJson);
                        if (steps < 0) {
                            throw new RuntimeException("No steps found for yesterday.");
                        }
                        String rewardsJson = BackendClient.getServerRewards();
                        RewardTier tier = pickTier(parseRewardTiers(rewardsJson), steps);
                        return new ClaimContext(steps, tier, false, null);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, BACKEND_EXECUTOR)
                .whenComplete((ctx, error) -> player.getServer().execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        if (onError != null) {
                            onError.accept(cause.getMessage());
                        }
                        return;
                    }

                    if (ctx != null && ctx.alreadyClaimed) {
                        if (onSuccess != null) {
                            if (ctx.claimedAt != null && !ctx.claimedAt.isBlank()) {
                                onSuccess.accept("Already claimed at " + ctx.claimedAt + ".");
                            } else {
                                onSuccess.accept("Already claimed.");
                            }
                        }
                        return;
                    }

                    if (ctx == null || ctx.tier == null) {
                        if (onSuccess != null) {
                            onSuccess.accept("No reward tier for " + (ctx == null ? "" : ctx.steps) + " steps.");
                        }
                        return;
                    }

                    CompletableFuture<Void> commandFuture = new CompletableFuture<>();
                    player.getServer().execute(() -> {
                        try {
                            runRewardCommands(player, targetPlayer, ctx.tier.rewards);
                            commandFuture.complete(null);
                        } catch (Exception e) {
                            commandFuture.completeExceptionally(e);
                        }
                    });

                    commandFuture.whenComplete((ignored, cmdError) -> {
                        if (cmdError != null) {
                            if (onError != null) {
                                onError.accept("Reward command failed: " + cmdError.getMessage());
                            }
                            return;
                        }

                        CompletableFuture
                                .supplyAsync(() -> {
                                    try {
                                        return BackendClient.claimRewardForPlayer(targetPlayer);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }, BACKEND_EXECUTOR)
                                .whenComplete((result, claimError) -> player.getServer().execute(() -> {
                                    if (claimError != null) {
                                        Throwable cause = claimError.getCause() != null ? claimError.getCause() : claimError;
                                        if (onError != null) {
                                            onError.accept(cause.getMessage());
                                        }
                                    } else if (onSuccess != null) {
                                        onSuccess.accept("Tier: " + ctx.tier.label + " (" + ctx.steps + " steps). Claim: " + result);
                                    }
                                }));
                    });
                }));
    }

    private static void runRewardCommands(ServerPlayerEntity player, String targetPlayer, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        ServerCommandSource source = player.getServer().getCommandSource()
                .withEntity(player)
                .withPosition(player.getPos())
                .withLevel(4);

        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String cmd = raw.replace("{player}", targetPlayer).trim();
            player.getServer().getCommandManager().executeWithPrefix(source, cmd);
        }
    }

    private static long extractSteps(String stepsJson) {
        if (stepsJson == null) return -1;
        if (stepsJson.startsWith("Error:")) {
            throw new RuntimeException(stepsJson);
        }
        JsonObject obj = GSON.fromJson(stepsJson, JsonObject.class);
        if (obj == null) return -1;
        if (obj.has("steps_yesterday") && obj.get("steps_yesterday").isJsonPrimitive()) {
            return obj.get("steps_yesterday").getAsLong();
        }
        return -1;
    }

    private static ClaimStatus extractClaimStatus(String claimJson) {
        if (claimJson == null) return new ClaimStatus(false, null);
        if (claimJson.startsWith("Error:")) {
            throw new RuntimeException(claimJson);
        }
        JsonObject obj = GSON.fromJson(claimJson, JsonObject.class);
        if (obj == null) return new ClaimStatus(false, null);
        boolean claimed = obj.has("claimed") && obj.get("claimed").isJsonPrimitive() && obj.get("claimed").getAsBoolean();
        String claimedAt = null;
        if (obj.has("claimed_at") && obj.get("claimed_at").isJsonPrimitive()) {
            claimedAt = obj.get("claimed_at").getAsString();
        }
        return new ClaimStatus(claimed, claimedAt);
    }

    private static List<RewardTier> parseRewardTiers(String rewardsJson) {
        List<RewardTier> tiers = new ArrayList<>();
        if (rewardsJson == null || rewardsJson.isBlank()) return tiers;
        if (rewardsJson.startsWith("Error:")) {
            throw new RuntimeException(rewardsJson);
        }
        JsonObject root = GSON.fromJson(rewardsJson, JsonObject.class);
        if (root == null || !root.has("tiers") || !root.get("tiers").isJsonArray()) return tiers;
        JsonArray arr = root.getAsJsonArray("tiers");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            long minSteps = obj.has("min_steps") ? obj.get("min_steps").getAsLong() : 0;
            String label = obj.has("label") ? obj.get("label").getAsString() : "";
            List<String> rewards = new ArrayList<>();
            if (obj.has("rewards") && obj.get("rewards").isJsonArray()) {
                for (JsonElement r : obj.getAsJsonArray("rewards")) {
                    if (r.isJsonPrimitive()) {
                        rewards.add(r.getAsString());
                    }
                }
            }
            tiers.add(new RewardTier(minSteps, label, rewards));
        }
        return tiers;
    }

    private static RewardTier pickTier(List<RewardTier> tiers, long steps) {
        RewardTier best = null;
        for (RewardTier tier : tiers) {
            if (tier.minSteps <= steps) {
                if (best == null || tier.minSteps > best.minSteps) {
                    best = tier;
                }
            }
        }
        return best;
    }

    private record RewardTier(long minSteps, String label, List<String> rewards) {}

    private record ClaimContext(long steps, RewardTier tier, boolean alreadyClaimed, String claimedAt) {}

    private record ClaimStatus(boolean claimed, String claimedAt) {}

}
