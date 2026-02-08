package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.LecternScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.slot.SlotActionType;

public class StepCraftLecternScreenHandler extends LecternScreenHandler {
    private final long openedAtMs;
    public StepCraftLecternScreenHandler(int syncId, Inventory inventory, PropertyDelegate propertyDelegate) {
        super(syncId, inventory, propertyDelegate);
        this.openedAtMs = System.currentTimeMillis();
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        // Use "Take Book" button (typically id 3) as exit
        if (id == 3) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                serverPlayer.closeHandledScreen();
            }
            return true;
        }
        return super.onButtonClick(player, id);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Clicking the book slot exits the GUI
        if (slotIndex == 0) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                serverPlayer.closeHandledScreen();
            }
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (System.currentTimeMillis() - openedAtMs < 400) {
            return;
        }
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            serverPlayer.getServer().execute(() ->
                StepCraftNav.goBack(serverPlayer, serverPlayer::closeHandledScreen)
            );
        }
    }
}
