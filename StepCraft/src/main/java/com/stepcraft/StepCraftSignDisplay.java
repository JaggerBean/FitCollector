package com.stepcraft;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StepCraftSignDisplay {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private StepCraftSignDisplay() {}

    public static void show(ServerPlayerEntity player, List<String> lines) {
        World world = player.getWorld();
        BlockPos base = player.getBlockPos();
        BlockPos pos = findAir(world, base.up(2));

        BlockState originalState = world.getBlockState(pos);

        world.setBlockState(pos, Blocks.OAK_SIGN.getDefaultState(), 3);
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof SignBlockEntity sign) {
            try {
                java.lang.reflect.Method setFront = SignBlockEntity.class.getDeclaredMethod(
                        "setFrontText", sign.getFrontText().getClass());
                setFront.setAccessible(true);

                var text = sign.getFrontText();
                for (int i = 0; i < 4; i++) {
                    String line = i < lines.size() ? lines.get(i) : "";
                    text = text.withMessage(i, Text.literal(line));
                }
                setFront.invoke(sign, text);
            } catch (Exception ignored) {
                // ignore if method is inaccessible
            }

            sign.markDirty();
            player.openEditSignScreen(sign, true);
            PENDING.put(player.getUuid(), new Pending(pos, originalState));
        } else {
            player.sendMessage(Text.literal("Could not open result display."));
        }
    }

    public static boolean handleSignUpdate(ServerPlayerEntity player) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending == null) {
            return false;
        }

        restore(player.getWorld(), pending);
        StepCraftUIHelper.openPlayersList(player);
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

    private record Pending(BlockPos pos, BlockState originalState) {}
}