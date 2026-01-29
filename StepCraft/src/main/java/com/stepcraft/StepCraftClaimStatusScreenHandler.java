package com.stepcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StepCraftClaimStatusScreenHandler extends GenericContainerScreenHandler {
    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_PAGE = 48;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_REFRESH = 50;
    private static final int SLOT_NEXT = 53;

    private static final ExecutorService BACKEND_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "stepcraft-claim-status-ui");
        t.setDaemon(true);
        return t;
    });

    private final SimpleInventory inventory;
    private final String targetPlayer;
    private final ServerPlayerEntity viewer;
    private final Map<Integer, ClaimStatusItem> slotToItem = new HashMap<>();

    private List<ClaimStatusItem> items = new ArrayList<>();
    private int page = 0;
    private int totalPages = 1;
    private boolean loading = true;

    public StepCraftClaimStatusScreenHandler(int syncId, PlayerInventory playerInventory, String targetPlayer) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.inventory = (SimpleInventory) this.getInventory();
        this.targetPlayer = targetPlayer;
        this.viewer = (ServerPlayerEntity) playerInventory.player;

        buildPage();
        loadStatus();
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }

    private void loadStatus() {
        loading = true;
        buildPage();

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    String json = BackendClient.getClaimStatusListForPlayer(targetPlayer);
                    return parseItems(json);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, BACKEND_EXECUTOR)
            .whenComplete((result, error) -> viewer.getServer().execute(() -> {
                if (error != null) {
                    loading = false;
                    items = new ArrayList<>();
                    viewer.sendMessage(Text.literal("Error loading claim status: " + error.getMessage()));
                    buildPage();
                    return;
                }

                items = (result == null) ? new ArrayList<>() : new ArrayList<>(result);
                page = 0;
                totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
                loading = false;
                buildPage();
            }));
    }

    private void buildPage() {
        slotToItem.clear();
        inventory.clear();

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, pane.copy());
        }

        if (loading) {
            inventory.setStack(22, menuItem(Items.CLOCK, "Loading...", 0xAAAAAA));
        } else if (items.isEmpty()) {
            inventory.setStack(22, menuItem(Items.BARRIER, "No eligible rewards", 0xFF5555));
        } else {
            int start = page * PAGE_SIZE;
            for (int i = 0; i < PAGE_SIZE; i++) {
                int index = start + i;
                if (index >= items.size()) break;
                ClaimStatusItem item = items.get(index);

                String title = (item.label == null || item.label.isBlank())
                    ? ("Tier " + item.minSteps)
                    : item.label;

                int rgb = item.claimed ? 0x55FF55 : 0xFF5555;
                ItemStack stack = menuItem(Items.MAP, title, rgb);

                List<Text> lore = new ArrayList<>();
                lore.add(Text.literal("Day: " + item.day)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)));
                lore.add(Text.literal("Min Steps: " + item.minSteps)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)));
                lore.add(Text.literal("Claimed: " + (item.claimed ? "yes" : "no"))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)));
                if (item.claimedAt != null && !item.claimedAt.isBlank()) {
                    lore.add(Text.literal("Claimed At: " + item.claimedAt)
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)));
                }

                stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
                inventory.setStack(i, stack);
                slotToItem.put(i, item);
            }
        }

        if (page > 0) {
            inventory.setStack(SLOT_PREV, menuItem(Items.ARROW, "Previous", 0xAAAAAA));
        }

        inventory.setStack(SLOT_BACK, menuItem(Items.BOOK, "Back", 0xFFFFFF));

        String pageLabel = "Page " + (page + 1) + " / " + totalPages;
        inventory.setStack(SLOT_PAGE, menuItem(Items.PAPER, pageLabel, 0xCCCCCC));

        inventory.setStack(SLOT_REFRESH, menuItem(Items.COMPASS, "Refresh", 0x55FFFF));

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

        if (slot == SLOT_PREV && page > 0) {
            page--;
            buildPage();
            return;
        }

        if (slot == SLOT_NEXT && page < totalPages - 1) {
            page++;
            buildPage();
            return;
        }

        if (slot == SLOT_BACK) {
            StepCraftNav.goBack(serverPlayer, () -> StepCraftScreens.openActionMenu(serverPlayer, targetPlayer));
            return;
        }

        if (slot == SLOT_REFRESH) {
            loadStatus();
        }
    }

    private static List<ClaimStatusItem> parseItems(String json) {
        List<ClaimStatusItem> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        if (json.startsWith("Error:")) {
            throw new RuntimeException(json);
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("items") || !root.get("items").isJsonArray()) return out;
        JsonArray items = root.getAsJsonArray("items");
        for (JsonElement el : items) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            ClaimStatusItem item = new ClaimStatusItem();
            item.day = obj.has("day") ? obj.get("day").getAsString() : "";
            item.minSteps = obj.has("min_steps") ? obj.get("min_steps").getAsLong() : 0;
            item.label = obj.has("label") ? obj.get("label").getAsString() : "";
            item.claimed = obj.has("claimed") && obj.get("claimed").getAsBoolean();
            item.claimedAt = obj.has("claimed_at") && !obj.get("claimed_at").isJsonNull()
                ? obj.get("claimed_at").getAsString()
                : null;
            out.add(item);
        }
        return out;
    }

    private static class ClaimStatusItem {
        String day;
        long minSteps;
        String label;
        boolean claimed;
        String claimedAt;
    }

    private static ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }
}
