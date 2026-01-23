package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class StepCraftChestScreenFactory implements NamedScreenHandlerFactory {
    private final SimpleInventory inventory;
    private final Text title;

    public StepCraftChestScreenFactory(SimpleInventory inventory, Text title) {
        this.inventory = inventory;
        this.title = title;
    }

    @Override
    public Text getDisplayName() {
        return title;
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, PlayerEntity player) {
        return new StepCraftChestScreenHandler(syncId, playerInventory, inventory);
    }
}
