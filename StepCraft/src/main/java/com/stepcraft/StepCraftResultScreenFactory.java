package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class StepCraftResultScreenFactory implements NamedScreenHandlerFactory {
    private final String message;
    private final Text title;

    public StepCraftResultScreenFactory(String message, Text title) {
        this.message = message;
        this.title = title;
    }

    @Override
    public Text getDisplayName() {
        return title;
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new StepCraftResultScreenHandler(syncId, playerInventory, message);
    }
}
