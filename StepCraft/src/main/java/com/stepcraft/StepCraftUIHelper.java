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
            for (int i = 0; i < 5; i++) {
                ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
                net.minecraft.nbt.NbtCompound display = new net.minecraft.nbt.NbtCompound();
                display.putString("Name", "{\"text\":\"Player" + (i+1) + "\"}");
                net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
                tag.put("display", display);
                try {
                    java.lang.reflect.Method setTag = ItemStack.class.getMethod("setTag", net.minecraft.nbt.NbtCompound.class);
                    setTag.invoke(skull, tag);
                } catch (Exception e) {
                    // fallback: do nothing or log
                }
                items.set(i, skull);
            }
            StepCraftChestScreenHandler.open(player, items, Text.literal("Players List"));
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error opening UI: " + e.getMessage()));
        }
    }
}
