package com.stepcraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TextColor;
import net.minecraft.text.Style;
import net.minecraft.util.Unit;

public class StepCraftPlayerMenuScreenHandler extends GenericContainerScreenHandler {
    private static final int ROWS = 6;
    private final String targetPlayer;
    private final SimpleInventory inventory;

    public StepCraftPlayerMenuScreenHandler(int syncId, PlayerInventory playerInventory, String targetPlayer) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.targetPlayer = targetPlayer;
        this.inventory = (SimpleInventory) this.getInventory();

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, pane.copy());
        }

        inventory.setStack(29, makeActionItem(new ItemStack(Items.EMERALD), "Claim Rewards", 0x55FF55));
        inventory.setStack(33, makeActionItem(new ItemStack(Items.FEATHER), "Day Steps", 0x55FFFF));
        inventory.setStack(49, makeActionItem(new ItemStack(Items.BOOK), "Back", 0xFFFFFF));
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }

    private ItemStack makeActionItem(ItemStack stack, String name, int rgb) {
        stack.set(DataComponentTypes.CUSTOM_NAME,
            net.minecraft.text.Text.literal(name)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            super.onSlotClick(slot, button, actionType, player);
            return;
        }

        if (slot < 0 || slot >= this.getInventory().size()) {
            super.onSlotClick(slot, button, actionType, player);
            return;
        }

        switch (slot) {
            case 29 -> {
                StepCraftScreens.openClaimRewards(serverPlayer, targetPlayer);
                return;
            }
            case 33 -> {
                StepCraftChestScreenHandler.sendBackendToLectern(serverPlayer, "Day Steps",
                        () -> BackendClient.getTodayStepsForPlayer(targetPlayer));
                return;
            }
            case 49 -> {
                StepCraftNav.goBack(serverPlayer, serverPlayer::closeHandledScreen);
                return;
            }
            default -> { return; }
        }
    }
}