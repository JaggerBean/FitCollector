package com.stepcraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StepCraftClaimRewardsScreenHandler extends GenericContainerScreenHandler {
    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 46;
    private static final int SLOT_PAGE = 48;
    private static final int SLOT_CLAIM_ALL = 49;
    private static final int SLOT_REFRESH = 50;
    private static final int SLOT_NEXT = 53;

    private static final ExecutorService BACKEND_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "stepcraft-claim-ui");
        t.setDaemon(true);
        return t;
    });

    private final SimpleInventory inventory;
    private final String targetPlayer;
    private final ServerPlayerEntity viewer;
    private final Map<Integer, StepCraftChestScreenHandler.ClaimableItem> slotToItem = new HashMap<>();

    private List<StepCraftChestScreenHandler.ClaimableItem> items = new ArrayList<>();
    private int page = 0;
    private int totalPages = 1;
    private boolean loading = true;
    private String debugJson;

    public StepCraftClaimRewardsScreenHandler(int syncId, PlayerInventory playerInventory, String targetPlayer) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.inventory = (SimpleInventory) this.getInventory();
        this.targetPlayer = targetPlayer;
        this.viewer = (ServerPlayerEntity) playerInventory.player;

        buildPage();
        loadClaimables();
    }

    private void loadClaimables() {
        loading = true;
        debugJson = null;
        buildPage();

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    String json = BackendClient.getClaimAvailableForPlayer(targetPlayer);
                    List<StepCraftChestScreenHandler.ClaimableItem> parsed = StepCraftChestScreenHandler.parseClaimableItems(json);
                    if (parsed.isEmpty()) {
                        debugJson = BackendClient.getClaimAvailableForPlayer(targetPlayer, true);
                    }
                    return parsed;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, BACKEND_EXECUTOR)
            .whenComplete((result, error) -> viewer.getServer().execute(() -> {
                if (error != null) {
                    loading = false;
                    items = new ArrayList<>();
                    viewer.sendMessage(Text.literal("Error loading claimables: " + error.getMessage()));
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
            ItemStack empty = menuItem(Items.BARRIER, "No claimable rewards", 0xFF5555);
            if (debugJson != null && !debugJson.isBlank()) {
                empty.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Click for details").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false))
                )));
            }
            inventory.setStack(22, empty);
        } else {
            int start = page * PAGE_SIZE;
            for (int i = 0; i < PAGE_SIZE; i++) {
                int index = start + i;
                if (index >= items.size()) break;
                StepCraftChestScreenHandler.ClaimableItem item = items.get(index);

                String title = (item.label() == null || item.label().isBlank())
                    ? ("Tier " + item.minSteps())
                    : item.label();

                ItemStack stack = menuItem(resolveItem(item.itemId()), title, 0x55FF55);
                stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Day: " + item.day()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)),
                    Text.literal("Min Steps: " + item.minSteps()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false))
                )));
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

        inventory.setStack(SLOT_CLAIM_ALL, menuItem(Items.EMERALD_BLOCK, "Claim All", 0x55FF55));
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

        if (slotToItem.containsKey(slot)) {
            StepCraftChestScreenHandler.ClaimableItem item = slotToItem.get(slot);
            StepCraftChestScreenHandler.claimSpecificRewardWithCommands(
                serverPlayer,
                targetPlayer,
                item.day(),
                item.minSteps(),
                result -> {
                    StepCraftLecternHelper.openLectern(serverPlayer, "Result",
                        StepCraftResultScreenHandler.toPagesFromLines(
                            StepCraftResultScreenHandler.toDisplayLines("Claim reward: " + result)
                        )
                    );
                    loadClaimables();
                },
                error -> StepCraftLecternHelper.openLectern(serverPlayer, "Result",
                    StepCraftResultScreenHandler.toPagesFromLines(
                        StepCraftResultScreenHandler.toDisplayLines("Error: " + error)
                    )
                )
            );
            return;
        }

        if (items.isEmpty() && slot == 22 && debugJson != null && !debugJson.isBlank()) {
            StepCraftLecternHelper.openLectern(serverPlayer, "Claim Debug",
                StepCraftResultScreenHandler.toPagesFromLines(
                    StepCraftResultScreenHandler.toDisplayLines(debugJson)
                )
            );
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
            StepCraftScreens.openActionMenu(serverPlayer, targetPlayer);
            return;
        }

        if (slot == SLOT_REFRESH) {
            loadClaimables();
            return;
        }

        if (slot == SLOT_CLAIM_ALL) {
            StepCraftChestScreenHandler.claimRewardWithCommands(
                serverPlayer,
                targetPlayer,
                result -> {
                    StepCraftLecternHelper.openLectern(serverPlayer, "Result",
                        StepCraftResultScreenHandler.toPagesFromLines(
                            StepCraftResultScreenHandler.toDisplayLines("Claim reward: " + result)
                        )
                    );
                    loadClaimables();
                },
                error -> StepCraftLecternHelper.openLectern(serverPlayer, "Result",
                    StepCraftResultScreenHandler.toPagesFromLines(
                        StepCraftResultScreenHandler.toDisplayLines("Error: " + error)
                    )
                )
            );
        }
    }

    private static ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }

    private static net.minecraft.item.Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Items.EMERALD;
        }
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) {
                return Items.EMERALD;
            }
            return Registries.ITEM.get(id);
        } catch (Exception ignored) {
            return Items.EMERALD;
        }
    }
}
