package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class StepCraftChestScreenHandler extends GenericContainerScreenHandler {
    private final SimpleInventory inventory;
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stepcraft-ui");
        t.setDaemon(true);
        return t;
    });

    // Server constructor only (client uses vanilla screen + vanilla type)
    public StepCraftChestScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.inventory = inventory;
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (slot >= 0 && slot < inventory.size()) {
            ItemStack clicked = inventory.getStack(slot);

            if (!clicked.isEmpty()
                    && player instanceof ServerPlayerEntity serverPlayer
                    && serverPlayer.hasPermissionLevel(4)) {

                if (clicked.isOf(net.minecraft.item.Items.PURPLE_STAINED_GLASS_PANE)) {
                    return;
                }

                switch (slot) {
                    case 1 -> { sendBackend(serverPlayer, "Server info: ", BackendClient::getServerInfo); return; }
                    case 3 -> { sendBackend(serverPlayer, "Health check: ", BackendClient::healthCheck); return; }
                    case 5 -> {
                        String target = resolveSingleOtherPlayer(serverPlayer, "ban");
                        if (target == null) return;
                        sendBackend(serverPlayer, "Ban player " + target + ": ",
                                () -> BackendClient.banPlayer(target, "broke code of conduct"));
                        return;
                    }
                    case 7 -> {
                        String target = resolveSingleOtherPlayer(serverPlayer, "delete_player");
                        if (target == null) return;
                        sendBackend(serverPlayer, "Delete player " + target + ": ",
                                () -> BackendClient.deletePlayer(target));
                        return;
                    }
                    case 10 -> {
                        String target = resolveSingleOtherPlayer(serverPlayer, "unban");
                        if (target == null) return;
                        sendBackend(serverPlayer, "Unban player " + target + ": ",
                                () -> BackendClient.unbanPlayer(target));
                        return;
                    }
                    case 12 -> {
                        String username = serverPlayer.getName().getString();
                        sendBackend(serverPlayer, "Claim reward for " + username + ": ",
                                () -> BackendClient.claimRewardForPlayer(username));
                        return;
                    }
                    case 14 -> {
                        String username = serverPlayer.getName().getString();
                        sendBackend(serverPlayer, "Claim status for " + username + ": ",
                                () -> BackendClient.getClaimStatusForPlayer(username));
                        return;
                    }
                    case 16 -> { sendBackend(serverPlayer, "Players list: ", BackendClient::getPlayersList); return; }
                    case 19 -> { sendBackend(serverPlayer, "Bans: ", BackendClient::getAllServerBans); return; }
                    case 21 -> { sendBackend(serverPlayer, "All players: ", BackendClient::getAllPlayers); return; }
                    case 23 -> {
                        String username = serverPlayer.getName().getString();
                        sendBackend(serverPlayer, "Yesterday steps for " + username + ": ",
                                () -> BackendClient.getYesterdayStepsForPlayer(username));
                        return;
                    }
                }
            }
        }

        super.onSlotClick(slot, button, actionType, player);
    }

    // Convenience helper to open the GUI (server-only safe)
    public static void open(ServerPlayerEntity player, DefaultedList<ItemStack> items, Text title) {
        StepCraftScreens.openAdminChest(player, items, title);
    }

    private interface BackendCall {
        String run() throws Exception;
    }

    private static void sendBackend(ServerPlayerEntity player, String prefix, BackendCall call) {
        AtomicBoolean completed = new AtomicBoolean(false);
        ScheduledFuture<?> pendingMessage = SCHEDULER.schedule(() -> {
            if (!completed.get()) {
                player.getServer().execute(() ->
                        player.sendMessage(Text.literal(prefix + "(processing...)"))
                );
            }
        }, 1, TimeUnit.SECONDS);

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return call.run();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((result, error) -> player.getServer().execute(() -> {
                    completed.set(true);
                    pendingMessage.cancel(false);
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        player.sendMessage(Text.literal("Error: " + cause.getMessage()));
                    } else {
                        player.sendMessage(Text.literal(prefix + result));
                    }
                }));
    }

    private static String resolveSingleOtherPlayer(ServerPlayerEntity admin, String action) {
        List<ServerPlayerEntity> others = new ArrayList<>();
        for (ServerPlayerEntity p : admin.getServer().getPlayerManager().getPlayerList()) {
            if (!p.getUuid().equals(admin.getUuid())) {
                others.add(p);
            }
        }

        if (others.isEmpty()) {
            admin.sendMessage(Text.literal("No target player online. Use /stepcraft " + action + " <username>."));
            return null;
        }

        if (others.size() > 1) {
            admin.sendMessage(Text.literal("Multiple players online. Use /stepcraft " + action + " <username>."));
            return null;
        }

        return others.get(0).getName().getString();
    }
}
