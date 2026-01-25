package com.stepcraft;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StepCraftSignSearch {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private StepCraftSignSearch() {}

    public static void beginSearch(ServerPlayerEntity player, StepCraftPlayerAction action) {
        World world = player.getWorld();
        BlockPos base = player.getBlockPos();
        BlockPos pos = findAir(world, base.up(2));

        BlockState originalState = world.getBlockState(pos);
        // We only place signs in air, so no block entity state is expected.

        world.setBlockState(pos, Blocks.OAK_SIGN.getDefaultState(), 3);
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof SignBlockEntity sign) {
            try {
                java.lang.reflect.Method setFront = SignBlockEntity.class.getDeclaredMethod(
                        "setFrontText", sign.getFrontText().getClass());
                setFront.setAccessible(true);
                setFront.invoke(sign, sign.getFrontText().withMessage(0, Text.literal("Search")));
            } catch (Exception ignored) {
                // ignore if method is inaccessible
            }
            sign.markDirty();
            player.openEditSignScreen(sign, true);
            PENDING.put(player.getUuid(), new Pending(pos, originalState, action));
        } else {
            player.sendMessage(Text.literal("Could not open search input."));
        }
    }

    public static boolean handleSignUpdate(ServerPlayerEntity player, Object packet) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending == null) {
            return false;
        }

        String query = extractQuery(packet);
        restore(player.getWorld(), pending);

        if (query == null || query.isBlank()) {
            player.sendMessage(Text.literal("Search cancelled (empty)."));
            StepCraftUIHelper.openPlayerSelectList(player, null, 0, pending.action);
        } else {
            StepCraftUIHelper.openPlayerSelectList(player, query.trim(), 0, pending.action);
        }
        return true;
    }


    private static void restore(World world, Pending pending) {
        world.setBlockState(pending.pos, pending.originalState, 3);
    }

    private static BlockPos findAir(World world, BlockPos start) {
        for (int i = 0; i < 5; i++) {
            BlockPos pos = start.up(i);
            if (world.isAir(pos)) {
                return pos;
            }
        }
        return start;
    }

    private static String extractQuery(Object packet) {
        String query;

        query = extractFromMethod(packet, "getText");
        if (!query.isBlank()) return query;

        query = extractFromMethod(packet, "getLines");
        if (!query.isBlank()) return query;

        query = extractFromMethod(packet, "getFrontText");
        if (!query.isBlank()) return query;

        query = extractFromMethod(packet, "getBackText");
        if (!query.isBlank()) return query;

        query = extractFromFields(packet);
        if (!query.isBlank()) return query;

        return "";
    }

    private static String extractFromFields(Object packet) {
        try {
            java.lang.reflect.Field[] fields = packet.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(packet);
                String extracted = extractFromValue(value);
                if (!extracted.isBlank()) {
                    return extracted;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "";
    }

    private static String extractFromMethod(Object packet, String methodName) {
        try {
            java.lang.reflect.Method method = packet.getClass().getMethod(methodName);
            Object value = method.invoke(packet);
            return extractFromValue(value);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractFromValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof String[] lines) {
            return joinLines(lines);
        }

        if (value instanceof net.minecraft.text.Text[] texts) {
            return joinTextLines(texts);
        }

        if (value instanceof java.util.List<?> list) {
            return joinList(list);
        }

        // SignText handling via reflection
        if (value.getClass().getName().endsWith("SignText")) {
            try {
                java.lang.reflect.Method getMessages = value.getClass().getMethod("getMessages", boolean.class);
                Object messages = getMessages.invoke(value, true);
                return extractFromValue(messages);
            } catch (Exception ignored) {
                try {
                    java.lang.reflect.Method getMessages = value.getClass().getMethod("getMessages");
                    Object messages = getMessages.invoke(value);
                    return extractFromValue(messages);
                } catch (Exception ignoredAgain) {
                    return "";
                }
            }
        }

        return "";
    }

    private static String joinLines(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(line.trim());
            }
        }
        return sb.toString();
    }

    private static String joinTextLines(net.minecraft.text.Text[] texts) {
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.text.Text text : texts) {
            if (text != null) {
                String line = text.getString();
                if (!line.isBlank()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(line.trim());
                }
            }
        }
        return sb.toString();
    }

    private static String joinList(java.util.List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (item == null) continue;
            String line;
            if (item instanceof net.minecraft.text.Text text) {
                line = text.getString();
            } else {
                line = item.toString();
            }
            if (!line.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(line.trim());
            }
        }
        return sb.toString();
    }

    private record Pending(BlockPos pos, BlockState originalState, StepCraftPlayerAction action) {}
}
