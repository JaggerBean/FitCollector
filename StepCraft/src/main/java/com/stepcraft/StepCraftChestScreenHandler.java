package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.collection.DefaultedList;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
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
    private static final ExecutorService BACKEND_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "stepcraft-backend");
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
                    case 1 -> { sendBackendToLectern(serverPlayer, "Server info", BackendClient::getServerInfo); return; }
                    case 3 -> { sendBackendToLectern(serverPlayer, "Health check", BackendClient::healthCheck); return; }
                    case 5 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.BAN); return; }
                    case 7 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.DELETE); return; }
                    case 10 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.UNBAN); return; }
                    case 12 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.CLAIM_REWARD); return; }
                    case 14 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.CLAIM_STATUS); return; }
                    case 16 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.NONE); return; }
                    case 19 -> { sendBackendToLectern(serverPlayer, "Server bans", BackendClient::getAllServerBans); return; }
                    case 21 -> { sendBackendToLectern(serverPlayer, "All players", BackendClient::getAllPlayers); return; }
                    case 23 -> { StepCraftUIHelper.openPlayerSelectList(serverPlayer, null, 0, StepCraftPlayerAction.YESTERDAY_STEPS); return; }
                    case 25 -> {
                        serverPlayer.closeHandledScreen();
                        Text message = Text.literal("Click to paste: /stepcraft set_api_key ")
                                .setStyle(Style.EMPTY
                                        .withColor(TextColor.fromRgb(0x55FF55))
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stepcraft set_api_key "))
                                        .withItalic(false));
                        serverPlayer.sendMessage(message);
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

    public interface BackendCall {
        String run() throws Exception;
    }

    public static void sendBackend(ServerPlayerEntity player, String prefix, BackendCall call) {
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
                }, BACKEND_EXECUTOR)
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

        public static void sendBackendToLectern(ServerPlayerEntity player, String title, BackendCall call) {
        openLecternMessage(player, title + ": (processing...)");
        sendBackendWithCallback(player, call,
            result -> openLecternMessage(player, title + ": " + result),
            error -> openLecternMessage(player, "Error: " + error)
        );
        }

        private static void openLecternMessage(ServerPlayerEntity player, String message) {
        StepCraftLecternHelper.openLectern(player, "Result",
            StepCraftResultScreenHandler.toPagesFromLines(
                StepCraftResultScreenHandler.toDisplayLines(message)
            )
        );
        }

    public static void sendBackendWithCallback(ServerPlayerEntity player, BackendCall call,
                                                java.util.function.Consumer<String> onSuccess,
                                                java.util.function.Consumer<String> onError) {
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return call.run();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, BACKEND_EXECUTOR)
                .whenComplete((result, error) -> player.getServer().execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        if (onError != null) {
                            onError.accept(cause.getMessage());
                        }
                    } else {
                        if (onSuccess != null) {
                            onSuccess.accept(result);
                        }
                    }
                }));
    }

}
