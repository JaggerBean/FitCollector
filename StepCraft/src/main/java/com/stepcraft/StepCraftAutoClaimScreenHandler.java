package com.stepcraft;

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

public class StepCraftAutoClaimScreenHandler extends GenericContainerScreenHandler {
    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_REFRESH = 50;
    private static final int SLOT_NEXT = 53;

    private static final ExecutorService BACKEND_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "stepcraft-autoclaim-ui");
        t.setDaemon(true);
        return t;
    });

    private final SimpleInventory inventory;
    private final String targetPlayer;
    private final ServerPlayerEntity viewer;
    private final Map<Integer, StepCraftChestScreenHandler.RewardTier> slotToTier = new HashMap<>();

    private List<StepCraftChestScreenHandler.RewardTier> tiers = new ArrayList<>();
    private int page = 0;
    private int totalPages = 1;
    private boolean loading = true;

    public StepCraftAutoClaimScreenHandler(int syncId, PlayerInventory playerInventory, String targetPlayer) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.inventory = (SimpleInventory) this.getInventory();
        this.targetPlayer = targetPlayer;
        this.viewer = (ServerPlayerEntity) playerInventory.player;

        buildPage();
        loadTiers();
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }

    private void loadTiers() {
        loading = true;
        buildPage();

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    String json = BackendClient.getServerRewards();
                    return StepCraftChestScreenHandler.parseRewardTiers(json);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, BACKEND_EXECUTOR)
            .whenComplete((result, error) -> viewer.getServer().execute(() -> {
                if (error != null) {
                    loading = false;
                    tiers = new ArrayList<>();
                    viewer.sendMessage(Text.literal("Error loading tiers: " + error.getMessage()));
                    buildPage();
                    return;
                }

                tiers = (result == null) ? new ArrayList<>() : new ArrayList<>(result);
                page = 0;
                totalPages = Math.max(1, (int) Math.ceil(tiers.size() / (double) PAGE_SIZE));
                loading = false;
                buildPage();
            }));
    }

    private void buildPage() {
        slotToTier.clear();
        inventory.clear();

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, pane.copy());
        }

        if (loading) {
            inventory.setStack(22, menuItem(Items.CLOCK, "Loading...", 0xAAAAAA));
        } else if (tiers.isEmpty()) {
            inventory.setStack(22, menuItem(Items.BARRIER, "No reward tiers", 0xFF5555));
        } else {
            int start = page * PAGE_SIZE;
            for (int i = 0; i < PAGE_SIZE; i++) {
                int index = start + i;
                if (index >= tiers.size()) break;
                StepCraftChestScreenHandler.RewardTier tier = tiers.get(index);

                boolean enabled = StepCraftAutoClaimStore.isTierEnabled(targetPlayer, tier.minSteps());
                String label = (tier.label() == null || tier.label().isBlank())
                    ? ("Tier " + tier.minSteps())
                    : tier.label();

                ItemStack stack = menuItem(
                    enabled ? Items.EMERALD_BLOCK : Items.REDSTONE_BLOCK,
                    label,
                    enabled ? 0x55FF55 : 0xFF5555
                );

                String status = enabled ? "ON" : "OFF";
                int statusColor = enabled ? 0x55FF55 : 0xFF5555;
                stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Min Steps: " + tier.minSteps())
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)),
                    Text.literal("Auto-claim: " + status)
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(statusColor)).withItalic(false)),
                    Text.literal("Claims after 5s on join")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x777777)).withItalic(false))
                )));

                inventory.setStack(i, stack);
                slotToTier.put(i, tier);
            }
        }

        if (page > 0) {
            inventory.setStack(SLOT_PREV, menuItem(Items.ARROW, "Previous", 0xAAAAAA));
        }

        inventory.setStack(SLOT_BACK, menuItem(Items.BOOK, "Back", 0xFFFFFF));
        inventory.setStack(SLOT_REFRESH, menuItem(Items.COMPASS, "Refresh", 0x55FFFF));

        String pageLabel = "Page " + (page + 1) + " / " + totalPages;
        inventory.setStack(48, menuItem(Items.PAPER, pageLabel, 0xCCCCCC));

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

        if (slotToTier.containsKey(slot)) {
            StepCraftChestScreenHandler.RewardTier tier = slotToTier.get(slot);
            boolean enabled = StepCraftAutoClaimStore.isTierEnabled(targetPlayer, tier.minSteps());
            StepCraftAutoClaimStore.setTierEnabled(targetPlayer, tier.minSteps(), !enabled);
            buildPage();
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

        if (slot == SLOT_REFRESH) {
            loadTiers();
            return;
        }

        if (slot == SLOT_BACK) {
            StepCraftNav.goBack(serverPlayer, serverPlayer::closeHandledScreen);
            return;
        }
    }

    private ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }
}