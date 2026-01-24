package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public class StepCraftChestScreenHandler extends GenericContainerScreenHandler {
    private final SimpleInventory inventory;

    public StepCraftChestScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.inventory = inventory;
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (slot >= 0 && slot < inventory.size()) {
            ItemStack clicked = inventory.getStack(slot);
            if (!clicked.isEmpty() && player instanceof ServerPlayerEntity serverPlayer && serverPlayer.hasPermissionLevel(4)) {
                // Use slot index to determine command
                switch (slot) {
                    case 0:
                        com.stepcraft.BackendClient.sendInfoCommand(serverPlayer, "");
                        return;
                    case 1:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Ban command selected (implement logic)"));
                        return;
                    case 2:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Unban command selected (implement logic)"));
                        return;
                    case 3:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Delete command selected (implement logic)"));
                        return;
                    case 4:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Yesterday Steps command selected (implement logic)"));
                        return;
                    case 5:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Claim Status command selected (implement logic)"));
                        return;
                    case 6:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Claim Reward command selected (implement logic)"));
                        return;
                    case 7:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Players List command selected (implement logic)"));
                        return;
                    case 8:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("All Server Bans command selected (implement logic)"));
                        return;
                    case 9:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("All Players command selected (implement logic)"));
                        return;
                    case 10:
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Health Check command selected (implement logic)"));
                        return;
                }
            }
        }
        super.onSlotClick(slot, button, actionType, player);
    }

    // Factory method for opening the GUI
    public static void open(ServerPlayerEntity player, DefaultedList<ItemStack> items, Text title) {
        SimpleInventory inv = new SimpleInventory(27);
        for (int i = 0; i < items.size() && i < 27; i++) {
            inv.setStack(i, items.get(i));
        }
        player.openHandledScreen(new StepCraftChestScreenFactory(inv, title));
    }
}
