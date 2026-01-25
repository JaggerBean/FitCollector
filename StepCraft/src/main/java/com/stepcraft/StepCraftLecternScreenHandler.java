package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.LecternScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.slot.SlotActionType;

public class StepCraftLecternScreenHandler extends LecternScreenHandler {
    public StepCraftLecternScreenHandler(int syncId, Inventory inventory, PropertyDelegate propertyDelegate) {
        super(syncId, inventory, propertyDelegate);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        // Block "Take Book" button (typically id 3)
        if (id == 3) {
            return false;
        }
        return super.onButtonClick(player, id);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Prevent taking the book from the lectern slot
        if (slotIndex == 0) {
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }
}
