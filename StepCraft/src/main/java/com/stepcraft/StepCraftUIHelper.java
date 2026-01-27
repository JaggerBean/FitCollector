package com.stepcraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StepCraftUIHelper {
    private static final long PLAYER_LIST_TTL_MS = 10_000;
    private static final java.util.Map<String, CachedPage> PLAYER_PAGE_CACHE = new java.util.HashMap<>();

    // Open the admin chest GUI (server-only safe; vanilla client compatible)
    public static void openPlayersList(ServerPlayerEntity player) {
        try {
            // If you use this later, keep it. For now it can remain unused.
            // String playersJson = BackendClient.getPlayersList();

            DefaultedList<ItemStack> items = DefaultedList.ofSize(54, ItemStack.EMPTY);

                Object[][] commandItems = new Object[][]{
                    // Info / system
                    {Items.BOOK, "Info", "Show backend + server info", Formatting.AQUA},
                    {Items.HEART_OF_THE_SEA, "Health Check", "Ping backend health", Formatting.LIGHT_PURPLE},

                    // Player / server queries
                    {Items.PLAYER_HEAD, "Players List", "Show all players", Formatting.GOLD},
                    {Items.WRITABLE_BOOK, "All Players", "Dump all player data", Formatting.GOLD},

                    // Stats / rewards
                    {Items.FEATHER, "Yesterday's Steps", "Fetch yesterday's step count for a player", Formatting.AQUA},
                    {Items.POTION, "Claim Status", "Check claim status", Formatting.GOLD},
                    {Items.EMERALD, "Claim Reward", "Grant today's reward", Formatting.GREEN},
                    {Items.NAME_TAG, "All Server Bans", "List banned players", Formatting.GOLD},

                    // Moderation
                    {Items.IRON_SWORD, "Ban Player", "Ban a player", Formatting.RED},
                    {Items.PAPER, "Unban Player", "Unban a player", Formatting.GREEN},
                    {Items.BARRIER, "Delete Player", "Delete player record", Formatting.DARK_RED},
                };

                            int[] slotLayout = new int[]{
                                10, 12, 14, 16,
                                19, 21, 23, 25,
                                28, 30, 32, 34
                            };

            for (int i = 0; i < commandItems.length && i < slotLayout.length; i++) {
                Item item = (Item) commandItems[i][0];
                String name = (String) commandItems[i][1];
                String lore = (String) commandItems[i][2];
                Formatting color = (Formatting) commandItems[i][3];

                ItemStack stack = new ItemStack(item);

                if (item == Items.POTION && "Claim Status".equals(name)) {
                    stack.set(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.of(Potions.POISON));
                }

                // ✅ Force explicit RGB color (prevents vanilla rarity/lore colors from taking over)
                stack.set(DataComponentTypes.CUSTOM_NAME, menuName(name, color));
                stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(menuLore(lore))));

                items.set(slotLayout[i], stack);
            }

            ItemStack settings = new ItemStack(Items.COMPASS);
            settings.set(DataComponentTypes.CUSTOM_NAME, menuName("Settings", Formatting.LIGHT_PURPLE));
            settings.set(DataComponentTypes.LORE, new LoreComponent(List.of(menuLore("API key + Rewards"))));
            items.set(49, settings);

                ItemStack glass = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
                glass.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);

            for (int slot = 0; slot < items.size(); slot++) {
                if (items.get(slot).isEmpty()) {
                    items.set(slot, glass.copy());
                }
            }

            StepCraftChestScreenHandler.open(player, items, Text.literal("StepCraft Admin Commands"));
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error opening UI: " + e.getMessage()));
        }
    }

    public static void openPlayerSelectList(ServerPlayerEntity player, String query, int page, StepCraftPlayerAction action) {
        String trimmedQuery = (query == null) ? "" : query.trim();
        int limit = StepCraftPlayerListScreenHandler.PAGE_SIZE;
        int offset = Math.max(0, page) * limit;
        String cacheKey = trimmedQuery.toLowerCase() + "::" + page;


        CachedPage cached = PLAYER_PAGE_CACHE.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.cachedAt) <= PLAYER_LIST_TTL_MS) {
            player.getServer().execute(() -> renderPlayerList(player, trimmedQuery, page, cached.names, cached.total, action));
            return;
        }

        player.getServer().execute(() -> renderPlayerList(player, trimmedQuery, page, List.of(), 0, action));

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return BackendClient.getRegisteredPlayerNamesPage(limit, offset, trimmedQuery);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((pageData, error) -> player.getServer().execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        player.sendMessage(Text.literal("Error loading players: " + cause.getMessage()));
                        return;
                    }

                    CachedPage entry = new CachedPage(pageData.names, pageData.total, System.currentTimeMillis());
                    PLAYER_PAGE_CACHE.put(cacheKey, entry);
                    renderPlayerList(player, trimmedQuery, page, pageData.names, pageData.total, action);
                }));
    }

    private static void renderPlayerList(ServerPlayerEntity player, String query, int page, List<String> names, int total, StepCraftPlayerAction action) {
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(Comparator.naturalOrder());

        if (!query.isBlank()) {
            List<String> filtered = new ArrayList<>();
            String q = query.toLowerCase();
            for (String name : sorted) {
                if (name.toLowerCase().contains(q)) {
                    filtered.add(name);
                }
            }
            // Fallback in case backend query isn't deployed or applied.
            sorted = filtered;
            total = filtered.size();
            page = 0;
        }

        StepCraftScreens.openPlayerList(player, sorted, query, page, total, action);
    }

    private static class CachedPage {
        private final List<String> names;
        private final int total;
        private final long cachedAt;

        private CachedPage(List<String> names, int total, long cachedAt) {
            this.names = new ArrayList<>(names);
            this.total = total;
            this.cachedAt = cachedAt;
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
