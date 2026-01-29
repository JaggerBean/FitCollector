package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.Style;
import net.minecraft.util.Unit;

public class StepCraftActionMenuScreenHandler extends GenericContainerScreenHandler {
    private final String targetPlayer;
    private final SimpleInventory inventory;
    private static final int ROWS = 2;

    public StepCraftActionMenuScreenHandler(int syncId, PlayerInventory playerInventory, String targetPlayer) {
        super(ScreenHandlerType.GENERIC_9X2, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.targetPlayer = targetPlayer;
        this.inventory = (SimpleInventory) this.getInventory();

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, pane.copy());
        }

        inventory.setStack(0, makeActionItem(new ItemStack(Items.IRON_SWORD), "Ban Player", 0xFF5555));
        inventory.setStack(2, makeActionItem(new ItemStack(Items.PAPER), "Unban Player", 0x55FF55));
        inventory.setStack(4, makeActionItem(new ItemStack(Items.BARRIER), "Delete Player", 0xAA0000));

        inventory.setStack(9, makeActionItem(new ItemStack(Items.EMERALD), "Claim Reward", 0x55FF55));
        inventory.setStack(11, makeActionItem(new ItemStack(Items.MAP), "Claim Status", 0xFFAA00));
        inventory.setStack(13, makeActionItem(new ItemStack(Items.FEATHER), "Yesterday's Steps", 0x55FFFF));
        inventory.setStack(16, makeActionItem(new ItemStack(Items.BOOK), "Back", 0xFFFFFF));
    }

    private ItemStack makeActionItem(ItemStack stack, String name, int rgb) {
        stack.set(DataComponentTypes.CUSTOM_NAME,
            net.minecraft.text.Text.literal(name)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }

    public String getTargetPlayer() {
        return targetPlayer;
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
            case 0 -> {
                StepCraftScreens.openConfirm(serverPlayer, StepCraftPlayerAction.BAN, targetPlayer);
                return;
            }
            case 2 -> {
                StepCraftScreens.openConfirm(serverPlayer, StepCraftPlayerAction.UNBAN, targetPlayer);
                return;
            }
            case 4 -> {
                StepCraftScreens.openConfirm(serverPlayer, StepCraftPlayerAction.DELETE, targetPlayer);
                return;
            }
            case 9 -> {
                StepCraftScreens.openClaimRewards(serverPlayer, targetPlayer);
                return;
            }
            case 11 -> {
                StepCraftChestScreenHandler.sendBackendToLectern(serverPlayer, "Claim Status",
                        () -> StepCraftChestScreenHandler.getClaimStatusForYesterdayTier(targetPlayer));
                return;
            }
            case 13 -> {
                StepCraftChestScreenHandler.sendBackendToLectern(serverPlayer, "Yesterday's Steps",
                        () -> BackendClient.getYesterdayStepsForPlayer(targetPlayer));
                return;
            }
            case 16 -> {
                StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.NONE);
                return;
            }
            default -> { return; }
        }
    }
}
