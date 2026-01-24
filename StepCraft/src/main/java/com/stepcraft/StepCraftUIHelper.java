package com.stepcraft;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public class StepCraftUIHelper {
    // Example: Open a chest GUI showing all players
    public static void openPlayersList(ServerPlayerEntity player) {
        try {
            String playersJson = BackendClient.getPlayersList();
            // For demo, just show 5 dummy items. You would parse playersJson and create items per player.
            DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
            // Define items and names for each command
            Object[][] commandItems = new Object[][] {
                {Items.BOOK, "Info"},
                {Items.IRON_SWORD, "Ban"},
                {Items.PAPER, "Unban"},
                {Items.BARRIER, "Delete"},
                {Items.FEATHER, "Yesterday Steps"},
                {Items.MAP, "Claim Status"},
                {Items.EMERALD, "Claim Reward"},
                {Items.PLAYER_HEAD, "Players List"},
                {Items.NAME_TAG, "All Server Bans"},
                {Items.WRITABLE_BOOK, "All Players"},
                {Items.HEART_OF_THE_SEA, "Health Check"}
            };
            for (int i = 0; i < commandItems.length; i++) {
                ItemStack stack = new ItemStack((net.minecraft.item.Item)commandItems[i][0]);
                net.minecraft.nbt.NbtCompound display = new net.minecraft.nbt.NbtCompound();
                display.putString("Name", "{\"text\":\"" + commandItems[i][1] + "\"}");
                net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
                tag.put("display", display);
                try {
                    java.lang.reflect.Method setTag = ItemStack.class.getMethod("setTag", net.minecraft.nbt.NbtCompound.class);
                    setTag.invoke(stack, tag);
                } catch (Exception e) {
                    // fallback: do nothing or log
                }
                items.set(i, stack);
            }
            StepCraftChestScreenHandler.open(player, items, Text.literal("Admin Commands"));
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error opening UI: " + e.getMessage()));
        }
    }
}
