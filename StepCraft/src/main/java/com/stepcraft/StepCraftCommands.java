package com.stepcraft;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class StepCraftCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("stepcraft_getall")
            .requires(source -> source.hasPermissionLevel(4)) // admin only
            .executes(context -> {
                try {
                    String result = BackendClient.getAllPlayers();
                    context.getSource().sendFeedback(() -> Text.literal("Backend get all: " + result), false);
                } catch (Exception e) {
                    context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                }
                return Command.SINGLE_SUCCESS;
            })
        );
    }
}
