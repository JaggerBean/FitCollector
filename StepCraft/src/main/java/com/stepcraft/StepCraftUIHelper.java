package com.stepcraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

import java.util.List;

public class StepCraftUIHelper {

    // Open the admin chest GUI (server-only safe; vanilla client compatible)
    public static void openPlayersList(ServerPlayerEntity player) {
        try {
            String playersJson = BackendClient.getPlayersList(); // still unused for now

            DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);

            Object[][] commandItems = new Object[][]{
                    {Items.BOOK, "Info", "Show backend + server info"},
                    {Items.IRON_SWORD, "Ban Player", "Ban a player (stub)"},
                    {Items.PAPER, "Unban Player", "Unban a player (stub)"},
                    {Items.BARRIER, "Delete Player", "Delete player record (stub)"},
                    {Items.FEATHER, "Yesterday Steps", "Fetch yesterday steps"},
                    {Items.MAP, "Claim Status", "Check claim status"},
                    {Items.EMERALD, "Claim Reward", "Grant today's reward"},
                    {Items.PLAYER_HEAD, "Players List", "Show all players"},
                    {Items.NAME_TAG, "All Server Bans", "List bans"},
                    {Items.WRITABLE_BOOK, "All Players", "Dump all players"},
                    {Items.HEART_OF_THE_SEA, "Health Check", "Ping backend health"}
            };

            for (int i = 0; i < commandItems.length; i++) {
                Item item = (Item) commandItems[i][0];
                String name = (String) commandItems[i][1];
                String lore = (String) commandItems[i][2];

                ItemStack stack = new ItemStack(item);

                // ✅ 1.20.6+ correct way: Data Components (no NBT hacks, no reflection)
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
                stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(lore))));

                items.set(i, stack);
            }

            StepCraftChestScreenHandler.open(player, items, Text.literal("Admin Commands"));
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error opening UI: " + e.getMessage()));
        }
    }
}
