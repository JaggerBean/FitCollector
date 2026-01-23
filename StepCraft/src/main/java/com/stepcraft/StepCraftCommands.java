package com.stepcraft;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class StepCraftCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // All commands are now under /stepcraft <subcommand>
        dispatcher.register(CommandManager.literal("stepcraft")
            // /stepcraft info
            .then(CommandManager.literal("info")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    try {
                        String result = BackendClient.getServerInfo();
                        context.getSource().sendFeedback(() -> Text.literal("Server info: " + result), false);
                    } catch (Exception e) {
                        context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
            )
            // /stepcraft claim_status <username>
            .then(CommandManager.literal("claim_status")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        try {
                            String result = BackendClient.getClaimStatusForPlayer(username);
                            context.getSource().sendFeedback(() -> Text.literal("Claim status for " + username + ": " + result), false);
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft players
            .then(CommandManager.literal("players")
                .executes(context -> {
                    try {
                        String result = BackendClient.getAllPlayers();
                        context.getSource().sendFeedback(() -> Text.literal("Players: " + result), false);
                    } catch (Exception e) {
                        context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
            )
            // /stepcraft yesterday_steps <username>
            .then(CommandManager.literal("yesterday_steps")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        // TODO: Call backend to get yesterday's steps for username
                        String username = StringArgumentType.getString(context, "username");
                        context.getSource().sendFeedback(() -> Text.literal("[TODO] Yesterday's steps for " + username), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft bans
            .then(CommandManager.literal("bans")
                .executes(context -> {
                    // TODO: Call backend to get all bans for this server
                    context.getSource().sendFeedback(() -> Text.literal("[TODO] List all server bans"), false);
                    return Command.SINGLE_SUCCESS;
                })
            )
            // /stepcraft players_list
            .then(CommandManager.literal("players_list")
                .executes(context -> {
                    // TODO: Call backend to list all registered players (paginated)
                    context.getSource().sendFeedback(() -> Text.literal("[TODO] List registered players (paginated)"), false);
                    return Command.SINGLE_SUCCESS;
                })
            )
            // /stepcraft claim_reward <username>
            .then(CommandManager.literal("claim_reward")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        // TODO: Call backend to mark reward as claimed for username
                        String username = StringArgumentType.getString(context, "username");
                        context.getSource().sendFeedback(() -> Text.literal("[TODO] Claim reward for " + username), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft ban <username> [reason]
            .then(CommandManager.literal("ban")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .then(CommandManager.argument("reason", StringArgumentType.string())
                        .executes(context -> {
                            // TODO: Call backend to ban player with reason
                            String username = StringArgumentType.getString(context, "username");
                            String reason = StringArgumentType.getString(context, "reason");
                            context.getSource().sendFeedback(() -> Text.literal("[TODO] Ban player " + username + ": " + reason), false);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .executes(context -> {
                        // TODO: Call backend to ban player with default reason
                        String username = StringArgumentType.getString(context, "username");
                        context.getSource().sendFeedback(() -> Text.literal("[TODO] Ban player " + username), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft delete_player <username>
            .then(CommandManager.literal("delete_player")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        // TODO: Call backend to delete player data
                        String username = StringArgumentType.getString(context, "username");
                        context.getSource().sendFeedback(() -> Text.literal("[TODO] Delete player " + username), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft unban <username>
            .then(CommandManager.literal("unban")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        // TODO: Call backend to unban player
                        String username = StringArgumentType.getString(context, "username");
                        context.getSource().sendFeedback(() -> Text.literal("[TODO] Unban player " + username), false);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
        );
    }
}
