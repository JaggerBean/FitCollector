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
            // /stepcraft gui_players
            .then(CommandManager.literal("gui_players")
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    if (source.getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                        StepCraftUIHelper.openPlayersList(player);
                    } else {
                        source.sendError(Text.literal("Only players can use this command."));
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
                        String username = StringArgumentType.getString(context, "username");
                        try {
                            String result = BackendClient.getYesterdayStepsForPlayer(username);
                            context.getSource().sendFeedback(() -> Text.literal("Yesterday's steps for " + username + ": " + result), false);
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft bans
            .then(CommandManager.literal("bans")
                .executes(context -> {
                    try {
                        String result = BackendClient.getAllServerBans();
                        context.getSource().sendFeedback(() -> Text.literal("Bans: " + result), false);
                    } catch (Exception e) {
                        context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
            )
            // /stepcraft players_list
            .then(CommandManager.literal("players_list")
                .executes(context -> {
                    try {
                        String result = BackendClient.getPlayersList();
                        context.getSource().sendFeedback(() -> Text.literal("Players list: " + result), false);
                    } catch (Exception e) {
                        context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
            )
            // /stepcraft claim_reward <username>
            .then(CommandManager.literal("claim_reward")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        try {
                            String result = BackendClient.claimRewardForPlayer(username);
                            context.getSource().sendFeedback(() -> Text.literal("Claim reward for " + username + ": " + result), false);
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft ban <username> [reason]
            .then(CommandManager.literal("ban")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .then(CommandManager.argument("reason", StringArgumentType.string())
                        .executes(context -> {
                            String username = StringArgumentType.getString(context, "username");
                            String reason = StringArgumentType.getString(context, "reason");
                            try {
                                String result = BackendClient.banPlayer(username, reason);
                                context.getSource().sendFeedback(() -> Text.literal("Ban player " + username + ": " + result), false);
                            } catch (Exception e) {
                                context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        String defaultReason = "broke code of conduct";
                        try {
                            String result = BackendClient.banPlayer(username, defaultReason);
                            context.getSource().sendFeedback(() -> Text.literal("Ban player " + username + ": " + result), false);
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft delete_player <username>
            .then(CommandManager.literal("delete_player")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        try {
                            String result = BackendClient.deletePlayer(username);
                            context.getSource().sendFeedback(() -> Text.literal("Delete player " + username + ": " + result), false);
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft unban <username>
            .then(CommandManager.literal("unban")
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        try {
                            String result = BackendClient.unbanPlayer(username);
                            context.getSource().sendFeedback(() -> Text.literal("Unban player " + username + ": " + result), false);
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
        );
    }
}
