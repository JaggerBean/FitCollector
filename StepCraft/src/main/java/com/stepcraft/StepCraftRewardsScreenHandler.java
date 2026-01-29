package com.stepcraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.List;

public class StepCraftRewardsScreenHandler extends GenericContainerScreenHandler {
    private static final int ROWS = 6;
    private static final int SLOT_VIEW = 20;
    private static final int SLOT_SEED = 22;
    private static final int SLOT_BACK = 49;
    private static final String DASHBOARD_URL = "https://stepcraft.org/dashboard";

    private final SimpleInventory inventory;

    public StepCraftRewardsScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.inventory = (SimpleInventory) this.getInventory();

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, pane.copy());
        }

        inventory.setStack(SLOT_VIEW, menuItem(Items.BOOK, "View Rewards Structure", 0x55AAFF));
        inventory.setStack(SLOT_SEED, menuItem(Items.COMPASS, "Open Web Dashboard", 0x55AAFF));
        inventory.setStack(SLOT_BACK, menuItem(Items.BOOK, "Back", 0xFFFFFF));
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

        if (slot == SLOT_VIEW) {
            StepCraftChestScreenHandler.sendBackendWithCallback(serverPlayer, BackendClient::getServerRewards,
                result -> {
                    List<String> lines;
                    try {
                        lines = formatRewardLines(result);
                    } catch (Exception e) {
                        lines = StepCraftResultScreenHandler.toDisplayLines("Error: " + e.getMessage());
                    }
                    StepCraftLecternHelper.openLectern(serverPlayer, "Rewards",
                        StepCraftResultScreenHandler.toPagesFromLines(lines));
                },
                error -> StepCraftLecternHelper.openLectern(serverPlayer, "Rewards",
                    StepCraftResultScreenHandler.toPagesFromLines(
                        StepCraftResultScreenHandler.toDisplayLines("Error: " + error)
                    ))
            );
            return;
        }

        if (slot == SLOT_SEED) {
            serverPlayer.closeHandledScreen();
            Text message = Text.literal("Open StepCraft Dashboard")
                    .setStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(0x55AAFF))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DASHBOARD_URL))
                            .withUnderline(true)
                            .withItalic(false));
            serverPlayer.sendMessage(message);
            return;
        }

        if (slot == SLOT_BACK) {
            StepCraftNav.goBack(serverPlayer, () -> StepCraftScreens.openSettings(serverPlayer));
            return;
        }

        return;
    }

    private static List<String> formatRewardLines(String rewardsJson) {
        List<StepCraftChestScreenHandler.RewardTier> tiers = StepCraftChestScreenHandler.parseRewardTiers(rewardsJson);
        List<String> lines = new ArrayList<>();
        if (tiers.isEmpty()) {
            lines.add("No reward tiers found.");
            return lines;
        }

        int index = 1;
        for (StepCraftChestScreenHandler.RewardTier tier : tiers) {
            String label = tier.label();
            String title = label == null || label.isBlank()
                ? "Tier " + index
                : "Tier " + index + " - " + label;
            lines.add(title);
            lines.add("Min steps: " + tier.minSteps());
            List<String> rewards = tier.rewards();
            if (rewards == null || rewards.isEmpty()) {
                lines.add("Rewards: (none)");
            } else {
                lines.add("Rewards:");
                for (String reward : rewards) {
                    lines.add(" - " + reward);
                }
            }
            lines.add("");
            index++;
        }
        return lines;
    }

    private static ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }
}
