package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class StepCraftSearchScreenFactory implements NamedScreenHandlerFactory {
    private final StepCraftPlayerAction action;
    private final Text title;

    public StepCraftSearchScreenFactory(StepCraftPlayerAction action, Text title) {
        this.action = action;
        this.title = title;
    }

    @Override
    public Text getDisplayName() {
        return title;
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new StepCraftSearchScreenHandler(syncId, playerInventory, action);
    }
}
