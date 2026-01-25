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
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Unit;

public class StepCraftConfirmScreenHandler extends GenericContainerScreenHandler {
    private final StepCraftPlayerAction action;
    private final String targetPlayer;

    public StepCraftConfirmScreenHandler(int syncId, PlayerInventory playerInventory, StepCraftPlayerAction action, String targetPlayer) {
        super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, new SimpleInventory(9), 1);
        this.action = action;
        this.targetPlayer = targetPlayer;

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        for (int i = 0; i < this.getInventory().size(); i++) {
            this.getInventory().setStack(i, pane.copy());
        }

        this.getInventory().setStack(3, menuItem(Items.RED_CONCRETE, "Cancel", 0xFF5555));
        this.getInventory().setStack(5, menuItem(Items.LIME_CONCRETE, "Confirm", 0x55FF55));
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            super.onSlotClick(slot, button, actionType, player);
            return;
        }

        if (slot == 3) {
            StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, action);
            return;
        }

        if (slot == 5) {
            switch (action) {
                case BAN -> StepCraftChestScreenHandler.sendBackend(serverPlayer,
                        "Ban player " + targetPlayer + ": ",
                        () -> BackendClient.banPlayer(targetPlayer, "broke code of conduct"));
                case UNBAN -> StepCraftChestScreenHandler.sendBackend(serverPlayer,
                        "Unban player " + targetPlayer + ": ",
                        () -> BackendClient.unbanPlayer(targetPlayer));
                case DELETE -> StepCraftChestScreenHandler.sendBackend(serverPlayer,
                        "Delete player " + targetPlayer + ": ",
                        () -> BackendClient.deletePlayer(targetPlayer));
                case CLAIM_REWARD -> StepCraftChestScreenHandler.sendBackend(serverPlayer,
                        "Claim reward for " + targetPlayer + ": ",
                        () -> BackendClient.claimRewardForPlayer(targetPlayer));
                case CLAIM_STATUS -> StepCraftChestScreenHandler.sendBackend(serverPlayer,
                        "Claim status for " + targetPlayer + ": ",
                        () -> BackendClient.getClaimStatusForPlayer(targetPlayer));
                case YESTERDAY_STEPS -> StepCraftChestScreenHandler.sendBackend(serverPlayer,
                        "Yesterday steps for " + targetPlayer + ": ",
                        () -> BackendClient.getYesterdayStepsForPlayer(targetPlayer));
                default -> {}
            }
            StepCraftUIHelper.openPlayersList(serverPlayer);
            return;
        }
    }

    private ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }
}
