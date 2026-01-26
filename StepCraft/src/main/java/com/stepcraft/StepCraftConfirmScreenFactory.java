package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class StepCraftConfirmScreenFactory implements NamedScreenHandlerFactory {
    private final StepCraftPlayerAction action;
    private final String targetPlayer;
    private final Text title;
    private final boolean returnToPlayerList;

    public StepCraftConfirmScreenFactory(StepCraftPlayerAction action, String targetPlayer, Text title, boolean returnToPlayerList) {
        this.action = action;
        this.targetPlayer = targetPlayer;
        this.title = title;
        this.returnToPlayerList = returnToPlayerList;
    }

    @Override
    public Text getDisplayName() {
        return title;
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new StepCraftConfirmScreenHandler(syncId, playerInventory, action, targetPlayer, returnToPlayerList);
    }
}
