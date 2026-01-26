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
    private final boolean returnToPlayerList;

    public StepCraftConfirmScreenHandler(int syncId, PlayerInventory playerInventory, StepCraftPlayerAction action, String targetPlayer, boolean returnToPlayerList) {
        super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, new SimpleInventory(9), 1);
        this.action = action;
        this.targetPlayer = targetPlayer;
        this.returnToPlayerList = returnToPlayerList;

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
            if (returnToPlayerList) {
                StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, action);
            } else {
                StepCraftScreens.openActionMenu(serverPlayer, targetPlayer);
            }
            return;
        }

        if (slot == 5) {
            serverPlayer.getServer().execute(() -> StepCraftLecternHelper.openLectern(serverPlayer, "Result",
                StepCraftResultScreenHandler.toPagesFromLines(
                    StepCraftResultScreenHandler.toDisplayLines("Processing...")
                )
            ));
            switch (action) {
            case BAN -> StepCraftChestScreenHandler.sendBackendWithCallback(serverPlayer,
                () -> BackendClient.banPlayer(targetPlayer, "broke code of conduct"),
                result -> updateResult(serverPlayer, "Ban: " + result),
                error -> updateResult(serverPlayer, "Error: " + error));
            case UNBAN -> StepCraftChestScreenHandler.sendBackendWithCallback(serverPlayer,
                () -> BackendClient.unbanPlayer(targetPlayer),
                result -> updateResult(serverPlayer, "Unban: " + result),
                error -> updateResult(serverPlayer, "Error: " + error));
            case DELETE -> StepCraftChestScreenHandler.sendBackendWithCallback(serverPlayer,
                () -> BackendClient.deletePlayer(targetPlayer),
                result -> updateResult(serverPlayer, "Delete: " + result),
                error -> updateResult(serverPlayer, "Error: " + error));
            case CLAIM_REWARD -> StepCraftChestScreenHandler.claimRewardWithCommands(serverPlayer,
                targetPlayer,
                result -> updateResult(serverPlayer, "Claim reward: " + result),
                error -> updateResult(serverPlayer, "Error: " + error));
            case CLAIM_STATUS -> StepCraftChestScreenHandler.sendBackendWithCallback(serverPlayer,
                () -> BackendClient.getClaimStatusForPlayer(targetPlayer),
                result -> updateResult(serverPlayer, "Claim status: " + result),
                error -> updateResult(serverPlayer, "Error: " + error));
            case YESTERDAY_STEPS -> StepCraftChestScreenHandler.sendBackendWithCallback(serverPlayer,
                () -> BackendClient.getYesterdayStepsForPlayer(targetPlayer),
                result -> updateResult(serverPlayer, "Yesterday steps: " + result),
                error -> updateResult(serverPlayer, "Error: " + error));
                default -> {}
            }
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

    private void updateResult(ServerPlayerEntity player, String message) {
        StepCraftLecternHelper.openLectern(player, "Result",
            StepCraftResultScreenHandler.toPagesFromLines(
                StepCraftResultScreenHandler.toDisplayLines(message)
            )
        );
    }
}
