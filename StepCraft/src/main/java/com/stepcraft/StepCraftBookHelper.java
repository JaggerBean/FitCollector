package com.stepcraft;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;

import java.util.ArrayList;
import java.util.List;

public final class StepCraftBookHelper {
    private StepCraftBookHelper() {}

    public static ItemStack createWrittenBook(String title, String author, List<String> pages) {
        List<RawFilteredPair<String>> rawPages = new ArrayList<>();
        for (String page : pages) {
            rawPages.add(RawFilteredPair.of(page));
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        try {
            Object content = buildWrittenBookContent(title, author, pages, false);
            if (content instanceof WrittenBookContentComponent written) {
                book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, written);
                return book;
            }
        } catch (Throwable ignored) {
            // Fallback to writable book if written content fails
        }

        book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponentTypes.WRITABLE_BOOK_CONTENT,
                new WritableBookContentComponent(rawPages)
        );
        return book;
    }

    public static ItemStack createWrittenBookText(String title, String author, List<Text> pages) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        try {
            Object content = buildWrittenBookContent(title, author, pages, true);
            if (content instanceof WrittenBookContentComponent written) {
                book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, written);
                return book;
            }
        } catch (Throwable ignored) {
            // Fallback to writable book if written content fails
        }

        List<RawFilteredPair<String>> rawPages = new ArrayList<>();
        for (Text page : pages) {
            rawPages.add(RawFilteredPair.of(page.getString()));
        }
        book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponentTypes.WRITABLE_BOOK_CONTENT,
                new WritableBookContentComponent(rawPages)
        );
        return book;
    }

    public static void openBook(ServerPlayerEntity player, String title, List<Text> pages) {
        List<String> stringPages = new ArrayList<>();
        for (Text page : pages) {
            stringPages.add(page.getString());
        }

        List<RawFilteredPair<String>> rawPages = new ArrayList<>();
        for (String page : stringPages) {
            rawPages.add(RawFilteredPair.of(page));
        }

        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        // Store pages as strings in a writable book
        book.set(net.minecraft.component.DataComponentTypes.WRITABLE_BOOK_CONTENT,
            new net.minecraft.component.type.WritableBookContentComponent(rawPages)
        );

        ItemStack original = player.getStackInHand(Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, book);

        boolean opened = tryOpenViaPlayerMethod(player) || tryOpenViaPacket(player);

        if (!opened) {
            player.giveItemStack(book.copy());
        }

        player.setStackInHand(Hand.MAIN_HAND, original);
    }

    private static boolean tryOpenViaPlayerMethod(ServerPlayerEntity player) {
        try {
            for (java.lang.reflect.Method method : player.getClass().getMethods()) {
                if (method.getName().equalsIgnoreCase("openBook")) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 1 && params[0].getName().endsWith("Hand")) {
                        method.invoke(player, Hand.MAIN_HAND);
                        return true;
                    }
                    if (params.length == 1 && params[0].getName().endsWith("ItemStack")) {
                        method.invoke(player, player.getStackInHand(Hand.MAIN_HAND));
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return false;
    }

    private static boolean tryOpenViaPacket(ServerPlayerEntity player) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.packet.s2c.play.OpenBookS2CPacket");
            Object packet = packetClass.getConstructor(Hand.class).newInstance(Hand.MAIN_HAND);
            player.networkHandler.sendPacket((net.minecraft.network.packet.Packet<?>) packet);
            return true;
        } catch (Exception ignored) {
            // ignore
        }

        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket");
            Object packet = packetClass.getConstructor(Hand.class).newInstance(Hand.MAIN_HAND);
            player.networkHandler.sendPacket((net.minecraft.network.packet.Packet<?>) packet);
            return true;
        } catch (Exception ignored) {
            // ignore
        }

        return false;
    }

    private static Object buildWrittenBookContent(String title, String author, List<?> pages, boolean pagesAreText) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.component.type.WrittenBookContentComponent");

            // Try constructor with (RawFilteredPair<String>, RawFilteredPair<String>, int, List<RawFilteredPair<String>>, boolean)
            try {
                Class<?> rawFilteredPair = Class.forName("net.minecraft.text.RawFilteredPair");
                java.lang.reflect.Method of = rawFilteredPair.getMethod("of", Object.class);
                Object titlePair = of.invoke(null, pagesAreText ? Text.literal(title) : title);
                Object authorPair = of.invoke(null, pagesAreText ? Text.literal(author) : author);
                List<Object> rawPages = new ArrayList<>();
                for (Object page : pages) {
                    Object value = pagesAreText ? page : String.valueOf(page);
                    rawPages.add(of.invoke(null, value));
                }
                return clazz.getConstructor(rawFilteredPair, rawFilteredPair, int.class, List.class, boolean.class)
                        .newInstance(titlePair, authorPair, 0, rawPages, true);
            } catch (Exception ignored) {}

            // Try constructor with (String, String, int, List<String>, boolean)
            try {
                List<String> stringPages = new ArrayList<>();
                for (Object page : pages) {
                    stringPages.add(pagesAreText && page instanceof Text t ? t.getString() : String.valueOf(page));
                }
                return clazz.getConstructor(String.class, String.class, int.class, List.class, boolean.class)
                        .newInstance(title, author, 0, stringPages, true);
            } catch (Exception ignored) {}

            // Try constructor with (RawFilteredPair<Text>, RawFilteredPair<Text>, int, List<RawFilteredPair<Text>>, boolean)
            try {
                Class<?> rawFilteredPair = Class.forName("net.minecraft.text.RawFilteredPair");
                java.lang.reflect.Method of = rawFilteredPair.getMethod("of", Object.class);
                Object titlePair = of.invoke(null, Text.literal(title));
                Object authorPair = of.invoke(null, Text.literal(author));
                List<Object> rawPages = new ArrayList<>();
                for (Object page : pages) {
                    Text value = (pagesAreText && page instanceof Text t) ? t : Text.literal(String.valueOf(page));
                    rawPages.add(of.invoke(null, value));
                }
                return clazz.getConstructor(rawFilteredPair, rawFilteredPair, int.class, List.class, boolean.class)
                        .newInstance(titlePair, authorPair, 0, rawPages, true);
            } catch (Exception ignored) {}

            // Try constructor with (String, String, int, List<Text>, boolean)
            try {
                List<Text> textPages = new ArrayList<>();
                for (Object page : pages) {
                    textPages.add(pagesAreText && page instanceof Text t ? t : Text.literal(String.valueOf(page)));
                }
                return clazz.getConstructor(String.class, String.class, int.class, List.class, boolean.class)
                        .newInstance(title, author, 0, textPages, true);
            } catch (Exception ignored) {}

        } catch (Exception ignored) {
            // ignore
        }

        return null;
    }
}