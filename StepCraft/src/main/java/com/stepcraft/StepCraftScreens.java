package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

import java.util.List;

public final class StepCraftScreens {
    private StepCraftScreens() {}

    public static void openAdminChest(ServerPlayerEntity player, DefaultedList<ItemStack> items, Text title) {
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 54; i++) {
            ItemStack stack = (items != null && i < items.size()) ? items.get(i) : ItemStack.EMPTY;
            inv.setStack(i, stack);
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (int syncId, PlayerInventory playerInv, PlayerEntity p) ->
                        new StepCraftChestScreenHandler(syncId, playerInv, inv),
                title
        ));
    }

            public static void openPlayerList(ServerPlayerEntity player, List<String> players, String query, int page, int totalPlayers, StepCraftPlayerAction action) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (int syncId, PlayerInventory playerInv, PlayerEntity p) ->
                    new StepCraftPlayerListScreenHandler(syncId, playerInv, players, query, page, totalPlayers, action),
                Text.literal("Select Player")
        ));
    }

    public static void openActionMenu(ServerPlayerEntity player, String targetPlayer) {
        player.openHandledScreen(new StepCraftActionMenuScreenFactory(
                targetPlayer,
                Text.literal("Manage " + targetPlayer)
        ));
    }

        public static void openClaimRewards(ServerPlayerEntity player, String targetPlayer) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (int syncId, PlayerInventory playerInv, PlayerEntity p) ->
                new StepCraftClaimRewardsScreenHandler(syncId, playerInv, targetPlayer),
            Text.literal("Claim Rewards")
        ));
        }

    public static void openConfirm(ServerPlayerEntity player, StepCraftPlayerAction action, String targetPlayer) {
        player.openHandledScreen(new StepCraftConfirmScreenFactory(
                action,
                targetPlayer,
                Text.literal("Confirm " + action.getLabel()),
                false
        ));
    }

    public static void openConfirm(ServerPlayerEntity player, StepCraftPlayerAction action, String targetPlayer, boolean returnToPlayerList) {
        player.openHandledScreen(new StepCraftConfirmScreenFactory(
                action,
                targetPlayer,
                Text.literal("Confirm " + action.getLabel()),
                returnToPlayerList
        ));
    }

    public static void openResult(ServerPlayerEntity player, String message) {
        player.openHandledScreen(new StepCraftResultScreenFactory(
                message,
                Text.literal("Result")
        ));
    }

    public static void openSettings(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (int syncId, PlayerInventory playerInv, PlayerEntity p) ->
                        new StepCraftSettingsScreenHandler(syncId, playerInv),
                Text.literal("Settings")
        ));
    }

        public static void openRewards(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (int syncId, PlayerInventory playerInv, PlayerEntity p) ->
                new StepCraftRewardsScreenHandler(syncId, playerInv),
            Text.literal("Rewards")
        ));
        }

}
