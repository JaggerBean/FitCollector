package com.stepcraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Unit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.authlib.GameProfile;

public class StepCraftPlayerListScreenHandler extends GenericContainerScreenHandler {
    private static final int ROWS = 6;
    public static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 46;
    private static final int SLOT_PAGE = 48;
    private static final int SLOT_SEARCH = 49;
    private static final int SLOT_CLEAR = 50;
    private static final int SLOT_NEXT = 53;

    private final SimpleInventory inventory;
    private final List<String> players;
    private final String query;
    private final int page;
    private final int totalPages;
    private final int totalPlayers;
    private final ServerPlayerEntity viewer;
    private final Map<Integer, String> slotToPlayer = new HashMap<>();
    private final StepCraftPlayerAction action;

    public StepCraftPlayerListScreenHandler(int syncId, PlayerInventory playerInventory, List<String> players, String query, int page, int totalPlayers, StepCraftPlayerAction action) {
        this(syncId, playerInventory, new SimpleInventory(ROWS * 9), players, query, page, totalPlayers, action);
    }

    private StepCraftPlayerListScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, List<String> players, String query, int page, int totalPlayers, StepCraftPlayerAction action) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.players = players;
        this.query = query == null ? "" : query;
        this.totalPlayers = Math.max(totalPlayers, players.size());
        this.viewer = (ServerPlayerEntity) playerInventory.player;
        this.action = action == null ? StepCraftPlayerAction.NONE : action;

        int calculatedPages = Math.max(1, (int) Math.ceil(this.totalPlayers / (double) PAGE_SIZE));
        this.totalPages = calculatedPages;
        this.page = Math.max(0, Math.min(page, totalPages - 1));

        buildPage();
    }

    private void buildPage() {
        slotToPlayer.clear();
        inventory.clear();

        if (players.isEmpty() && totalPlayers == 0) {
            ItemStack loading = menuItem(Items.CLOCK, "Loading...", 0xAAAAAA);
            inventory.setStack(22, loading);

            ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
            pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (inventory.getStack(slot).isEmpty()) {
                    inventory.setStack(slot, pane.copy());
                }
            }
            return;
        }

        boolean useSkins = true;

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= players.size()) break;
            String name = players.get(index);
            ItemStack head = createPlayerHead(name, useSkins, viewer.getServer(), i, this);
            inventory.setStack(i, head);
            slotToPlayer.put(i, name);
        }

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int slot = PAGE_SIZE; slot < inventory.size(); slot++) {
            inventory.setStack(slot, pane.copy());
        }

        if (page > 0) {
            inventory.setStack(SLOT_PREV, menuItem(Items.ARROW, "Previous", 0xAAAAAA));
        }

        inventory.setStack(SLOT_BACK, menuItem(Items.BOOK, "Back", 0xFFFFFF));

        String pageLabel = "Page " + (page + 1) + " / " + totalPages;
        ItemStack pageItem = menuItem(Items.PAPER, pageLabel, 0xCCCCCC);
        if (!query.isEmpty()) {
            pageItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Filter: " + query).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false))
            )));
        }
        inventory.setStack(SLOT_PAGE, pageItem);

        inventory.setStack(SLOT_SEARCH, menuItem(Items.COMPASS, "Search", 0x55FFFF));
        if (!query.isEmpty()) {
            inventory.setStack(SLOT_CLEAR, menuItem(Items.BARRIER, "Clear Search", 0xFF5555));
        }

        if (page < totalPages - 1) {
            inventory.setStack(SLOT_NEXT, menuItem(Items.ARROW, "Next", 0xAAAAAA));
        }
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            super.onSlotClick(slot, button, actionType, player);
            return;
        }

        if (slot < 0 || slot >= inventory.size()) {
            super.onSlotClick(slot, button, actionType, player);
            return;
        }

        ItemStack clicked = inventory.getStack(slot);
        if (clicked.isEmpty()) return;

        if (slotToPlayer.containsKey(slot)) {
            String target = slotToPlayer.get(slot);
            if (action != StepCraftPlayerAction.NONE) {
                if (action == StepCraftPlayerAction.CLAIM_STATUS) {
                    StepCraftChestScreenHandler.sendBackendToLectern(serverPlayer, "Claim Status",
                            () -> StepCraftChestScreenHandler.getClaimStatusForYesterdayTier(target));
                } else if (action == StepCraftPlayerAction.YESTERDAY_STEPS) {
                    StepCraftChestScreenHandler.sendBackendToLectern(serverPlayer, "Yesterday's Steps",
                            () -> BackendClient.getYesterdayStepsForPlayer(target));
                } else if (action == StepCraftPlayerAction.CLAIM_REWARD) {
                    StepCraftScreens.openClaimRewards(serverPlayer, target);
                } else {
                    StepCraftScreens.openConfirm(serverPlayer, action, target, true);
                }
            } else {
                StepCraftScreens.openActionMenu(serverPlayer, target);
            }
            return;
        }

        if (slot == SLOT_PREV && page > 0) {
            StepCraftScreens.openPlayerList(serverPlayer, players, query, page - 1, totalPlayers, action);
            return;
        }

        if (slot == SLOT_NEXT && page < totalPages - 1) {
            StepCraftScreens.openPlayerList(serverPlayer, players, query, page + 1, totalPlayers, action);
            return;
        }

        if (slot == SLOT_BACK) {
            StepCraftUIHelper.openPlayersList(serverPlayer);
            return;
        }

        if (slot == SLOT_SEARCH) {
            StepCraftSignSearch.beginSearch(serverPlayer, action);
            return;
        }

        if (slot == SLOT_CLEAR) {
            StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, action);
            return;
        }
    }

    private static ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }

    private static ItemStack createPlayerHead(String username, boolean useSkins, MinecraftServer server, int slot, StepCraftPlayerListScreenHandler handler) {
        // Avoid reusing cached heads while skins are resolving to prevent shared texture state.

        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(username).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)).withItalic(false))
        );

        if (useSkins && server != null) {
            StepCraftSkinService.fetchProfile(username).thenAccept(resolved -> server.execute(() -> {
                if (resolved == null) {
                    return;
                }
                ItemStack refreshed = handler.getInventory().getStack(slot).copy();
                refreshed.set(DataComponentTypes.PROFILE, new ProfileComponent(resolved));
                handler.getInventory().setStack(slot, refreshed);
                handler.getInventory().markDirty();
                handler.sendContentUpdates();
            }));
        }

        head.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Click to manage").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55AAFF)).withItalic(false)),
            Text.literal("----------------").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x777777)).withItalic(false)),
            Text.literal("Steps: loading...").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA55)).withItalic(false)),
            Text.literal("Claim: loading...").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)).withItalic(false))
        )));

        if (server != null) {
            java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> fetchPlayerStats(username))
                    .whenComplete((stats, error) -> server.execute(() -> {
                        String current = handler.slotToPlayer.get(slot);
                        if (current == null || !current.equals(username)) {
                            return;
                        }

                        ItemStack refreshed = handler.getInventory().getStack(slot).copy();
                        List<Text> lore = new java.util.ArrayList<>();
                        lore.add(Text.literal("Click to manage")
                            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55AAFF)).withItalic(false)));
                        lore.add(Text.literal("----------------")
                            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x777777)).withItalic(false)));

                        if (error != null) {
                            lore.add(Text.literal("Steps: None")
                                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)));
                            lore.add(Text.literal("Claim: error")
                                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)).withItalic(false)));
                        } else {
                            if (stats.stepsError) {
                                lore.add(Text.literal("Steps: None")
                                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)));
                            } else {
                                long steps = stats.steps;
                                String stepsText = steps >= 0 ? String.valueOf(steps) : "n/a";
                                lore.add(Text.literal("Steps: " + stepsText)
                                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA55)).withItalic(false)));
                            }

                            if (stats.claimError || stats.claimStatus == null) {
                                lore.add(Text.literal("Claim: error")
                                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)).withItalic(false)));
                            } else {
                                boolean claimed = stats.claimStatus.claimed;
                                String claimText = claimed ? "claimed" : "not claimed";
                                int claimColor = claimed ? 0x55FF55 : 0xFF5555;
                                lore.add(Text.literal("Claim: " + claimText)
                                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(claimColor)).withItalic(false)));
                            }
                        }

                        refreshed.set(DataComponentTypes.LORE, new LoreComponent(lore));
                        handler.getInventory().setStack(slot, refreshed);
                        handler.getInventory().markDirty();
                        handler.sendContentUpdates();
                    }));
        }
        return head;
    }

    private static PlayerStats fetchPlayerStats(String username) {
        long steps = -1;
        boolean stepsError = false;
        boolean claimError = false;
        ClaimStatus claimStatus = null;

        try {
            String stepsJson = BackendClient.getYesterdayStepsForPlayer(username);
            steps = extractSteps(stepsJson);
        } catch (Exception e) {
            stepsError = true;
        }

        try {
            StepCraftChestScreenHandler.RewardTier tier = StepCraftChestScreenHandler.getTierForYesterday(username);
            if (tier == null) {
                claimError = true;
            } else {
                String claimJson = BackendClient.getClaimStatusForPlayer(
                        username,
                        tier.minSteps(),
                        StepCraftChestScreenHandler.getYesterdayDayParam()
                );
                claimStatus = extractClaimStatus(claimJson);
            }
        } catch (Exception e) {
            claimError = true;
        }

        return new PlayerStats(steps, claimStatus, stepsError, claimError);
    }

    private static long extractSteps(String stepsJson) {
        if (stepsJson == null) return -1;
        if (stepsJson.startsWith("Error:")) {
            throw new RuntimeException(stepsJson);
        }
        JsonObject obj = JsonParser.parseString(stepsJson).getAsJsonObject();
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
        JsonObject obj = JsonParser.parseString(claimJson).getAsJsonObject();
        if (obj == null) return new ClaimStatus(false, null);
        boolean claimed = obj.has("claimed") && obj.get("claimed").isJsonPrimitive() && obj.get("claimed").getAsBoolean();
        String claimedAt = null;
        if (obj.has("claimed_at") && obj.get("claimed_at").isJsonPrimitive()) {
            claimedAt = obj.get("claimed_at").getAsString();
        }
        return new ClaimStatus(claimed, claimedAt);
    }

    private static class PlayerStats {
        final long steps;
        final ClaimStatus claimStatus;
        final boolean stepsError;
        final boolean claimError;

        PlayerStats(long steps, ClaimStatus claimStatus, boolean stepsError, boolean claimError) {
            this.steps = steps;
            this.claimStatus = claimStatus;
            this.stepsError = stepsError;
            this.claimError = claimError;
        }
    }

    private record ClaimStatus(boolean claimed, String claimedAt) {}

}
