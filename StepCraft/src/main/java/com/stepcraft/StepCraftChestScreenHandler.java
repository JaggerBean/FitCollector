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
    public StepCraftChestScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
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
