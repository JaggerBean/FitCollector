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
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Unit;

import java.util.List;

public class StepCraftSettingsScreenHandler extends GenericContainerScreenHandler {
    private static final int ROWS = 1;
    private static final int SLOT_REWARDS = 1;
    private static final int SLOT_SET_KEY = 3;
    private static final int SLOT_STATUS = 5;
    private static final int SLOT_BACK = 7;

    private final SimpleInventory inventory;
    private final ServerPlayerEntity viewer;

    public StepCraftSettingsScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.inventory = (SimpleInventory) this.getInventory();
        this.viewer = (ServerPlayerEntity) playerInventory.player;

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, pane.copy());
        }

        inventory.setStack(SLOT_REWARDS, menuItem(Items.EMERALD, "Rewards", 0x55FF55));
        inventory.setStack(SLOT_SET_KEY, menuItem(Items.COMPASS, "Set API Key", 0xAA88FF));
        inventory.setStack(SLOT_BACK, menuItem(Items.BOOK, "Back", 0xFFFFFF));

        setStatusChecking();
        checkApiKeyStatus();
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

        if (slot == SLOT_REWARDS) {
            StepCraftScreens.openRewards(serverPlayer);
            return;
        }

        if (slot == SLOT_SET_KEY) {
            serverPlayer.closeHandledScreen();
            Text message = Text.literal("Click to paste: /stepcraft set_api_key ")
                    .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(0x55FF55))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stepcraft set_api_key "))
                        .withItalic(false));
            serverPlayer.sendMessage(message);
            return;
        }

        if (slot == SLOT_BACK) {
            StepCraftNav.goBack(serverPlayer, () -> StepCraftUIHelper.openPlayersList(serverPlayer));
            return;
        }

        // Prevent item movement in settings UI.
        return;
    }

    private void checkApiKeyStatus() {
        String key = StepCraftConfig.getApiKey();
        if (key == null || key.isBlank() || key.equals("PUT_YOUR_API_KEY_HERE")) {
            setStatusNotSet();
            return;
        }

        StepCraftChestScreenHandler.sendBackendWithCallback(viewer, BackendClient::getServerInfo,
                result -> {
                    if (!isViewerHere()) return;
                    if (result != null && result.startsWith("Error:")) {
                        applyErrorStatus(result);
                    } else {
                        setStatusWorking();
                    }
                },
                error -> {
                    if (!isViewerHere()) return;
                    applyErrorStatus(error);
                });
    }

    private boolean isViewerHere() {
        return viewer != null && viewer.currentScreenHandler == this;
    }

    private void setStatusChecking() {
        ItemStack item = menuItem(Items.LIGHT_BLUE_WOOL, "API Key Status", 0x55AAFF);
        item.set(DataComponentTypes.LORE, new LoreComponent(List.of(loreLine("checking"))));
        setStatusItem(item);
    }

    private void setStatusWorking() {
        ItemStack item = menuItem(Items.LIME_WOOL, "API Key Status", 0x55FF55);
        item.set(DataComponentTypes.LORE, new LoreComponent(List.of(loreLine("Working"))));
        setStatusItem(item);
    }

    private void setStatusNotWorking() {
        ItemStack item = menuItem(Items.RED_WOOL, "API Key Status", 0xFF5555);
        item.set(DataComponentTypes.LORE, new LoreComponent(List.of(loreLine("Not Working"))));
        setStatusItem(item);
    }

    private void setStatusNotSet() {
        ItemStack item = menuItem(Items.GRAY_WOOL, "API Key Status", 0xAAAAAA);
        item.set(DataComponentTypes.LORE, new LoreComponent(List.of(loreLine("Not Set"))));
        setStatusItem(item);
    }

    private void setStatusMaybeWorking() {
        ItemStack item = menuItem(Items.YELLOW_WOOL, "API Key Status", 0xFFFF55);
        item.set(DataComponentTypes.LORE, new LoreComponent(List.of(loreLine("May Work (Backend Unreachable)"))));
        setStatusItem(item);
    }

    private void applyErrorStatus(String message) {
        String msg = message == null ? "" : message.toLowerCase();

        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized") || msg.contains("forbidden")) {
            setStatusNotWorking();
            return;
        }

        if (msg.contains("timeout")
                || msg.contains("timed out")
                || msg.contains("unknownhost")
                || msg.contains("failed to connect")
                || msg.contains("connection refused")
                || msg.contains("connection reset")
                || msg.contains("network")
                || msg.contains("ssl")) {
            setStatusMaybeWorking();
            return;
        }

        if (msg.contains("502") || msg.contains("503") || msg.contains("504") || msg.contains("500")) {
            setStatusMaybeWorking();
            return;
        }

        setStatusNotWorking();
    }

    private void setStatusItem(ItemStack item) {
        inventory.setStack(SLOT_STATUS, item);
        inventory.markDirty();
        sendContentUpdates();
    }

    private static ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }

    private static Text loreLine(String label) {
        return Text.literal(label).setStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false)
        );
    }
}
