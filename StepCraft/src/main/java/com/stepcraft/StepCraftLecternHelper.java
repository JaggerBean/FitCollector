package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public final class StepCraftLecternHelper {
    private StepCraftLecternHelper() {}

    public static void openLectern(ServerPlayerEntity player, String title, List<Text> pages) {
        ItemStack book = StepCraftBookHelper.createWrittenBookText(title, "StepCraft", pages);

        SimpleInventory inventory = new SimpleInventory(1);
        inventory.setStack(0, book);

        ArrayPropertyDelegate properties = new ArrayPropertyDelegate(1);
        properties.set(0, 0);

        NamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory(
                (int syncId, PlayerInventory playerInv, PlayerEntity p) -> {
                    try {
                        return new StepCraftLecternScreenHandler(syncId, inventory, properties);
                    } catch (Throwable ignored) {
                        StepCraftBookHelper.openBook(player, title, pages);
                        return new StepCraftResultScreenHandler(syncId, playerInv, joinTextPages(pages));
                    }
                },
                Text.literal(title)
        );

        player.openHandledScreen(factory);
    }

    private static String joinTextPages(List<Text> pages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(pages.get(i).getString());
        }
        return sb.toString();
    }
}
