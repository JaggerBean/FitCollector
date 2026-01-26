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
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Unit;

public class StepCraftRewardsScreenHandler extends GenericContainerScreenHandler {
    private static final int ROWS = 1;
    private static final int SLOT_VIEW = 2;
    private static final int SLOT_SEED = 4;
    private static final int SLOT_BACK = 6;

    private final SimpleInventory inventory;

    public StepCraftRewardsScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.inventory = (SimpleInventory) this.getInventory();

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, pane.copy());
        }

        inventory.setStack(SLOT_VIEW, menuItem(Items.BOOK, "View Rewards", 0x55AAFF));
        inventory.setStack(SLOT_SEED, menuItem(Items.EMERALD, "Use Default", 0x55FF55));
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
            StepCraftChestScreenHandler.sendBackendToLectern(serverPlayer, "Rewards", BackendClient::getServerRewards);
            return;
        }

        if (slot == SLOT_SEED) {
            StepCraftChestScreenHandler.sendBackendToLectern(serverPlayer, "Rewards", BackendClient::seedServerRewards);
            return;
        }

        if (slot == SLOT_BACK) {
            StepCraftScreens.openSettings(serverPlayer);
            return;
        }

        return;
    }

    private static ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }
}
