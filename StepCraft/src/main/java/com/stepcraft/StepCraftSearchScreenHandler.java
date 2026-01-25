package com.stepcraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

public class StepCraftSearchScreenHandler extends AnvilScreenHandler {
    private final StepCraftPlayerAction action;

    public StepCraftSearchScreenHandler(int syncId, PlayerInventory playerInventory, StepCraftPlayerAction action) {
        super(syncId, playerInventory, ScreenHandlerContext.EMPTY);
        this.action = action == null ? StepCraftPlayerAction.NONE : action;

        ItemStack input = new ItemStack(Items.PAPER);
        this.getSlot(0).setStack(input);
        clearRenameText();
        this.sendContentUpdates();
    }

    @Override
    public void onTakeOutput(PlayerEntity player, ItemStack stack) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            String query = readRenameText();
            if (query == null || query.isBlank()) {
                StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, action);
            } else {
                StepCraftUIHelper.openPlayerSelectList(serverPlayer, query.trim(), 0, action);
            }
        }
    }

    @Override
    public boolean canTakeOutput(PlayerEntity player, boolean present) {
        return true;
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (slot == 2 && player instanceof ServerPlayerEntity serverPlayer) {
            String query = readRenameText();
            if (query == null || query.isBlank()) {
                StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, action);
            } else {
                StepCraftUIHelper.openPlayerSelectList(serverPlayer, query.trim(), 0, action);
            }
            return;
        }
        // Prevent moving items around in the anvil UI.
        return;
    }

    private String readRenameText() {
        forceUpdateResult();

        ItemStack input = this.getSlot(0).getStack();
        if (!input.isEmpty()) {
            String inputName = input.getName().getString();
            String defaultName = Items.PAPER.getName().getString();
            if (!inputName.equalsIgnoreCase(defaultName)) {
                return inputName;
            }
        }

        try {
            java.lang.reflect.Method getName = AnvilScreenHandler.class.getDeclaredMethod("getNewItemName");
            getName.setAccessible(true);
            Object value = getName.invoke(this);
            String text = value != null ? value.toString() : null;
            if (text != null && !text.isBlank()) {
                return text;
            }
        } catch (Exception ignored) {
            try {
                java.lang.reflect.Field field = AnvilScreenHandler.class.getDeclaredField("newItemName");
                field.setAccessible(true);
                Object value = field.get(this);
                String text = value != null ? value.toString() : null;
                if (text != null && !text.isBlank()) {
                    return text;
                }
            } catch (Exception ignoredAgain) {
                // fall through
            }
        }

        return null;
    }

    private void forceUpdateResult() {
        try {
            java.lang.reflect.Method update = AnvilScreenHandler.class.getDeclaredMethod("updateResult");
            update.setAccessible(true);
            update.invoke(this);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private void clearRenameText() {
        try {
            java.lang.reflect.Method setName = AnvilScreenHandler.class.getDeclaredMethod("setNewItemName", String.class);
            setName.setAccessible(true);
            setName.invoke(this, "");
        } catch (Exception ignored) {
            try {
                java.lang.reflect.Field field = AnvilScreenHandler.class.getDeclaredField("newItemName");
                field.setAccessible(true);
                field.set(this, "");
            } catch (Exception ignoredAgain) {
                // ignore
            }
        }
    }
}
