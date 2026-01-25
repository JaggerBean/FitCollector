package com.stepcraft;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.message.MessageType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StepCraftChatSearch {
    private static final ConcurrentHashMap<UUID, StepCraftPlayerAction> PENDING = new ConcurrentHashMap<>();

    private StepCraftChatSearch() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) -> {
            StepCraftPlayerAction action = PENDING.remove(sender.getUuid());
            if (action == null) {
                return true;
            }

            String query = message.getContent().getString().trim();
            if (query.isEmpty()) {
                sender.sendMessage(Text.literal("Search cancelled (empty query)."));
                return false;
            }

            sender.sendMessage(Text.literal("Searching players for: " + query));
            sender.getServer().execute(() ->
                    StepCraftUIHelper.openPlayerSelectList(sender, query, 0, action)
            );
            return false;
        });
    }

    public static void beginSearch(ServerPlayerEntity player, StepCraftPlayerAction action) {
        PENDING.put(player.getUuid(), action == null ? StepCraftPlayerAction.NONE : action);
        player.sendMessage(Text.literal("Type a search term in chat to filter players."));
    }
}
