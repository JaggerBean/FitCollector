package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public class StepCraftChestScreenHandler extends GenericContainerScreenHandler {
    private final SimpleInventory inventory;

    // Server constructor only (client uses vanilla screen + vanilla type)
    public StepCraftChestScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.inventory = inventory;
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (slot >= 0 && slot < inventory.size()) {
            ItemStack clicked = inventory.getStack(slot);

            if (!clicked.isEmpty()
                    && player instanceof ServerPlayerEntity serverPlayer
                    && serverPlayer.hasPermissionLevel(4)) {

                switch (slot) {
                    case 0 -> { BackendClient.sendInfoCommand(serverPlayer, ""); return; }
                    case 1 -> { serverPlayer.sendMessage(Text.literal("Ban command selected (implement logic)")); return; }
                    case 2 -> { serverPlayer.sendMessage(Text.literal("Unban command selected (implement logic)")); return; }
                    case 3 -> { serverPlayer.sendMessage(Text.literal("Delete command selected (implement logic)")); return; }
                    case 4 -> { serverPlayer.sendMessage(Text.literal("Yesterday Steps command selected (implement logic)")); return; }
                    case 5 -> { serverPlayer.sendMessage(Text.literal("Claim Status command selected (implement logic)")); return; }
                    case 6 -> { serverPlayer.sendMessage(Text.literal("Claim Reward command selected (implement logic)")); return; }
                    case 7 -> { serverPlayer.sendMessage(Text.literal("Players List command selected (implement logic)")); return; }
                    case 8 -> { serverPlayer.sendMessage(Text.literal("All Server Bans command selected (implement logic)")); return; }
                    case 9 -> { serverPlayer.sendMessage(Text.literal("All Players command selected (implement logic)")); return; }
                    case 10 -> { serverPlayer.sendMessage(Text.literal("Health Check command selected (implement logic)")); return; }
                }
            }
        }

        super.onSlotClick(slot, button, actionType, player);
    }

    // Convenience helper to open the GUI (server-only safe)
    public static void open(ServerPlayerEntity player, DefaultedList<ItemStack> items, Text title) {
        StepCraftScreens.openAdminChest(player, items, title);
    }
}
