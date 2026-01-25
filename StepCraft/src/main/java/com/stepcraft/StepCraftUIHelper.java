package com.stepcraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;

import java.util.List;

public class StepCraftUIHelper {

    // Open the admin chest GUI (server-only safe; vanilla client compatible)
    public static void openPlayersList(ServerPlayerEntity player) {
        try {
            // If you use this later, keep it. For now it can remain unused.
            // String playersJson = BackendClient.getPlayersList();

            DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);

            Object[][] commandItems = new Object[][]{
                    // Info / system
                    {Items.BOOK, "Info", "Show backend + server info", Formatting.AQUA},
                    {Items.HEART_OF_THE_SEA, "Health Check", "Ping backend health", Formatting.LIGHT_PURPLE},

                    // Moderation (dangerous)
                    {Items.IRON_SWORD, "Ban Player", "Ban a player", Formatting.RED},
                    {Items.BARRIER, "Delete Player", "Delete player record", Formatting.DARK_RED},

                    // Moderation (reversal / safe)
                    {Items.PAPER, "Unban Player", "Unban a player", Formatting.GREEN},

                    // Rewards / economy
                    {Items.EMERALD, "Claim Reward", "Grant today's reward", Formatting.GREEN},
                    {Items.MAP, "Claim Status", "Check claim status", Formatting.GOLD},

                    // Player / server queries
                    {Items.PLAYER_HEAD, "Players List", "Show all players", Formatting.GOLD},
                    {Items.NAME_TAG, "All Server Bans", "List banned players", Formatting.GOLD},
                    {Items.WRITABLE_BOOK, "All Players", "Dump all player data", Formatting.GOLD},

                    // Stats / data
                    {Items.FEATHER, "Yesterday Steps", "Fetch yesterday step count", Formatting.AQUA},
            };

            int[] slotLayout = new int[]{
                    1, 3, 5, 7,
                    10, 12, 14, 16,
                    19, 21, 23
            };

            for (int i = 0; i < commandItems.length && i < slotLayout.length; i++) {
                Item item = (Item) commandItems[i][0];
                String name = (String) commandItems[i][1];
                String lore = (String) commandItems[i][2];
                Formatting color = (Formatting) commandItems[i][3];

                ItemStack stack = new ItemStack(item);

                // ✅ Force explicit RGB color (prevents vanilla rarity/lore colors from taking over)
                stack.set(DataComponentTypes.CUSTOM_NAME, menuName(name, color));
                stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(menuLore(lore))));

                items.set(slotLayout[i], stack);
            }

            StepCraftChestScreenHandler.open(player, items, Text.literal("Admin Commands"));
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error opening UI: " + e.getMessage()));
        }
    }

    private static Text menuName(String label, Formatting color) {
        Integer rgb = color.getColorValue();
        TextColor textColor = (rgb != null) ? TextColor.fromRgb(rgb) : TextColor.fromRgb(0xFFFFFF);

        return Text.literal(label).setStyle(
                Style.EMPTY.withColor(textColor).withItalic(false)
        );
    }

    private static Text menuLore(String line) {
        // Default lore is purple unless explicitly styled
        return Text.literal(line).setStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false) // gray
        );
    }
}
