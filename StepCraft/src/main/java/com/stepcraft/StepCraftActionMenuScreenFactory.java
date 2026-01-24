package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class StepCraftActionMenuScreenFactory implements NamedScreenHandlerFactory {
    private final String targetPlayer;
    private final Text title;

    public StepCraftActionMenuScreenFactory(String targetPlayer, Text title) {
        this.targetPlayer = targetPlayer;
        this.title = title;
    }

    @Override
    public Text getDisplayName() {
        return title;
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, PlayerEntity player) {
        return new StepCraftActionMenuScreenHandler(syncId, playerInventory, targetPlayer);
    }
}
