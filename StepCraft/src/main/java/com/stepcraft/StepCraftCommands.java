package com.stepcraft;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
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
            // /stepcraft admin_gui (OPs only)
            .then(CommandManager.literal("admin_gui")
                .requires(source -> source.hasPermissionLevel(4))
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
            // /stepcraft set_api_key <key> (OPs only)
            .then(CommandManager.literal("set_api_key")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.argument("key", StringArgumentType.greedyString())
                    .executes(context -> {
                        String key = StringArgumentType.getString(context, "key");
                        StepCraftConfig.setApiKey(key.trim());
                        context.getSource().sendFeedback(() -> Text.literal("API key saved."), true);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft players_gui [query] (OPs only)
            .then(CommandManager.literal("players_gui")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    if (source.getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                        StepCraftUIHelper.openPlayerSelectList(player, null, 0, StepCraftPlayerAction.NONE);
                    } else {
                        source.sendError(Text.literal("Only players can use this command."));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(CommandManager.argument("query", StringArgumentType.greedyString())
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        String query = StringArgumentType.getString(context, "query");
                        if (source.getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                            StepCraftUIHelper.openPlayerSelectList(player, query, 0, StepCraftPlayerAction.NONE);
                        } else {
                            source.sendError(Text.literal("Only players can use this command."));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft claim_status <username> (OPs only)
            .then(CommandManager.literal("claim_status")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        try {
                            StepCraftChestScreenHandler.RewardTier tier = StepCraftChestScreenHandler.getTierForYesterday(username);
                            if (tier == null) {
                                context.getSource().sendFeedback(() -> Text.literal("No reward tier for yesterday's steps."), false);
                                return Command.SINGLE_SUCCESS;
                            }
                            String result = BackendClient.getClaimStatusForPlayer(
                                username,
                                tier.minSteps(),
                                StepCraftChestScreenHandler.getYesterdayDayParam()
                            );
                            context.getSource().sendFeedback(() -> Text.literal("Claim status for " + username + ": " + result), false);
                        } catch (Exception e) {
                            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft players (OPs only)
            .then(CommandManager.literal("players")
                .requires(source -> source.hasPermissionLevel(4))
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
            // /stepcraft today_steps [username]
            .then(CommandManager.literal("today_steps")
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    String username = source.getPlayer().getName().getString();
                    try {
                        String result = BackendClient.getTodayStepsForPlayer(username);
                        source.sendFeedback(() -> Text.literal("Day steps for " + username + ": " + result), false);
                    } catch (Exception e) {
                        source.sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .requires(source -> {
                        if (source.hasPermissionLevel(4)) return true;
                        String username = source.getName();
                        return username.equalsIgnoreCase(source.getPlayer().getName().getString());
                    })
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        ServerCommandSource source = context.getSource();
                        if (!source.hasPermissionLevel(4) && !username.equalsIgnoreCase(source.getPlayer().getName().getString())) {
                            source.sendError(Text.literal("You can only use this command for yourself."));
                            return 0;
                        }
                        try {
                            String result = BackendClient.getTodayStepsForPlayer(username);
                            source.sendFeedback(() -> Text.literal("Day steps for " + username + ": " + result), false);
                        } catch (Exception e) {
                            source.sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft bans (OPs only)
            .then(CommandManager.literal("bans")
                .requires(source -> source.hasPermissionLevel(4))
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
            // /stepcraft players_list (OPs only)
            .then(CommandManager.literal("players_list")
                .requires(source -> source.hasPermissionLevel(4))
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
            // /stepcraft claim_reward [username]
            .then(CommandManager.literal("claim_reward")
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    try {
                        ServerPlayerEntity player = source.getPlayer();
                        String username = player.getName().getString();
                        StepCraftChestScreenHandler.claimRewardWithCommands(
                            player,
                            username,
                            result -> source.sendFeedback(() -> Text.literal("Claim reward for " + username + ": " + result), false),
                            error -> source.sendError(Text.literal("Error: " + error))
                        );
                    } catch (Exception e) {
                        source.sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .requires(source -> {
                        if (source.hasPermissionLevel(4)) return true;
                        String username = source.getName();
                        return username.equalsIgnoreCase(source.getPlayer().getName().getString());
                    })
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        ServerCommandSource source = context.getSource();
                        if (!source.hasPermissionLevel(4) && !username.equalsIgnoreCase(source.getPlayer().getName().getString())) {
                            source.sendError(Text.literal("You can only use this command for yourself."));
                            return 0;
                        }
                        try {
                            ServerPlayerEntity player = source.getPlayer();
                            StepCraftChestScreenHandler.claimRewardWithCommands(
                                player,
                                username,
                                result -> source.sendFeedback(() -> Text.literal("Claim reward for " + username + ": " + result), false),
                                error -> source.sendError(Text.literal("Error: " + error))
                            );
                        } catch (Exception e) {
                            source.sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft ban [username] [reason] (OPs only)
            .then(CommandManager.literal("ban")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    String username = source.getPlayer().getName().getString();
                    String defaultReason = "broke code of conduct";
                    try {
                        String result = BackendClient.banPlayer(username, defaultReason);
                        source.sendFeedback(() -> Text.literal("Ban player " + username + ": " + result), false);
                    } catch (Exception e) {
                        source.sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        String username = StringArgumentType.getString(context, "username");
                        String defaultReason = "broke code of conduct";
                        try {
                            String result = BackendClient.banPlayer(username, defaultReason);
                            source.sendFeedback(() -> Text.literal("Ban player " + username + ": " + result), false);
                        } catch (Exception e) {
                            source.sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(CommandManager.argument("reason", StringArgumentType.string())
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            String username = StringArgumentType.getString(context, "username");
                            String reason = StringArgumentType.getString(context, "reason");
                            try {
                                String result = BackendClient.banPlayer(username, reason);
                                source.sendFeedback(() -> Text.literal("Ban player " + username + ": " + result), false);
                            } catch (Exception e) {
                                source.sendError(Text.literal("Error: " + e.getMessage()));
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
            )
            // /stepcraft delete_player [username] (OPs only)
            .then(CommandManager.literal("delete_player")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    String username = source.getPlayer().getName().getString();
                    try {
                        String result = BackendClient.deletePlayer(username);
                        source.sendFeedback(() -> Text.literal("Delete player " + username + ": " + result), false);
                    } catch (Exception e) {
                        source.sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        String username = StringArgumentType.getString(context, "username");
                        try {
                            String result = BackendClient.deletePlayer(username);
                            source.sendFeedback(() -> Text.literal("Delete player " + username + ": " + result), false);
                        } catch (Exception e) {
                            source.sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            // /stepcraft unban [username] (OPs only)
            .then(CommandManager.literal("unban")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    String username = source.getPlayer().getName().getString();
                    try {
                        String result = BackendClient.unbanPlayer(username);
                        source.sendFeedback(() -> Text.literal("Unban player " + username + ": " + result), false);
                    } catch (Exception e) {
                        source.sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(CommandManager.argument("username", StringArgumentType.string())
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        String username = StringArgumentType.getString(context, "username");
                        try {
                            String result = BackendClient.unbanPlayer(username);
                            source.sendFeedback(() -> Text.literal("Unban player " + username + ": " + result), false);
                        } catch (Exception e) {
                            source.sendError(Text.literal("Error: " + e.getMessage()));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
        );
    }
}
