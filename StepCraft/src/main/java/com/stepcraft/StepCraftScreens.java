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
        SimpleInventory inv = new SimpleInventory(27);
        for (int i = 0; i < 27; i++) {
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

    public static void openConfirm(ServerPlayerEntity player, StepCraftPlayerAction action, String targetPlayer) {
        player.openHandledScreen(new StepCraftConfirmScreenFactory(
                action,
                targetPlayer,
                Text.literal("Confirm " + action.getLabel())
        ));
    }
}
