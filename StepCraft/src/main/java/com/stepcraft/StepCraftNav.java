package com.stepcraft;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StepCraftNav {
    private StepCraftNav() {}

    private static final Map<UUID, Deque<NavEntry>> STACKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> SUPPRESS_PUSH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void pushCurrent(ServerPlayerEntity player) {
        if (player == null) return;
        if (Boolean.TRUE.equals(SUPPRESS_PUSH.get())) return;

        ScreenHandler handler = player.currentScreenHandler;
        NavEntry entry = fromHandler(handler);
        if (entry == null) return;

        STACKS.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>()).push(entry);
    }

    public static void goBack(ServerPlayerEntity player, Runnable fallback) {
        if (player == null) return;
        Deque<NavEntry> stack = STACKS.get(player.getUuid());
        if (stack == null || stack.isEmpty()) {
            if (fallback != null) fallback.run();
            return;
        }

        NavEntry entry = stack.pop();
        openEntry(player, entry, fallback);
    }

    private static void openEntry(ServerPlayerEntity player, NavEntry entry, Runnable fallback) {
        if (entry == null) {
            if (fallback != null) fallback.run();
            return;
        }

        suppressPush(() -> {
            switch (entry.type) {
                case ADMIN -> StepCraftUIHelper.openPlayersList(player);
                case PLAYER_LIST -> {
                    List<String> players = entry.players != null ? entry.players : List.of();
                    String query = entry.query != null ? entry.query : "";
                    StepCraftPlayerAction action = entry.action != null ? entry.action : StepCraftPlayerAction.NONE;
                    if (players.isEmpty() && entry.totalPlayers == 0) {
                        StepCraftUIHelper.openPlayerSelectList(player, query, entry.page, action);
                    } else {
                        StepCraftScreens.openPlayerList(player, players, query, entry.page, entry.totalPlayers, action);
                    }
                }
                case ACTION_MENU -> StepCraftScreens.openActionMenu(player, entry.targetPlayer);
                case CLAIM_REWARDS -> StepCraftScreens.openClaimRewards(player, entry.targetPlayer);
                case CLAIM_STATUS -> StepCraftScreens.openClaimStatus(player, entry.targetPlayer);
                case SETTINGS -> StepCraftScreens.openSettings(player);
                case REWARDS -> StepCraftScreens.openRewards(player);
                case CONFIRM -> StepCraftScreens.openConfirm(player, entry.action, entry.targetPlayer, entry.returnToPlayerList);
                default -> {
                    if (fallback != null) fallback.run();
                }
            }
        });
    }

    private static void suppressPush(Runnable action) {
        SUPPRESS_PUSH.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            SUPPRESS_PUSH.set(Boolean.FALSE);
        }
    }

    private static NavEntry fromHandler(ScreenHandler handler) {
        if (handler instanceof StepCraftChestScreenHandler) {
            return new NavEntry(Type.ADMIN);
        }
        if (handler instanceof StepCraftPlayerListScreenHandler list) {
            return new NavEntry(Type.PLAYER_LIST)
                .withPlayers(list.getPlayers())
                .withQuery(list.getQuery())
                .withPage(list.getPage())
                .withTotalPlayers(list.getTotalPlayers())
                .withAction(list.getAction());
        }
        if (handler instanceof StepCraftActionMenuScreenHandler menu) {
            return new NavEntry(Type.ACTION_MENU).withTarget(menu.getTargetPlayer());
        }
        if (handler instanceof StepCraftClaimRewardsScreenHandler claimRewards) {
            return new NavEntry(Type.CLAIM_REWARDS).withTarget(claimRewards.getTargetPlayer());
        }
        if (handler instanceof StepCraftClaimStatusScreenHandler claimStatus) {
            return new NavEntry(Type.CLAIM_STATUS).withTarget(claimStatus.getTargetPlayer());
        }
        if (handler instanceof StepCraftSettingsScreenHandler) {
            return new NavEntry(Type.SETTINGS);
        }
        if (handler instanceof StepCraftRewardsScreenHandler) {
            return new NavEntry(Type.REWARDS);
        }
        if (handler instanceof StepCraftConfirmScreenHandler confirm) {
            return new NavEntry(Type.CONFIRM)
                .withTarget(confirm.getTargetPlayer())
                .withAction(confirm.getAction())
                .withReturnToPlayerList(confirm.isReturnToPlayerList());
        }
        return null;
    }

    private enum Type {
        ADMIN,
        PLAYER_LIST,
        ACTION_MENU,
        CLAIM_REWARDS,
        CLAIM_STATUS,
        SETTINGS,
        REWARDS,
        CONFIRM
    }

    private static class NavEntry {
        private final Type type;
        private String targetPlayer;
        private StepCraftPlayerAction action;
        private boolean returnToPlayerList;
        private List<String> players;
        private String query;
        private int page;
        private int totalPlayers;

        private NavEntry(Type type) {
            this.type = type;
        }

        private NavEntry withTarget(String target) {
            this.targetPlayer = target;
            return this;
        }

        private NavEntry withAction(StepCraftPlayerAction action) {
            this.action = action;
            return this;
        }

        private NavEntry withReturnToPlayerList(boolean value) {
            this.returnToPlayerList = value;
            return this;
        }

        private NavEntry withPlayers(List<String> players) {
            this.players = players;
            return this;
        }

        private NavEntry withQuery(String query) {
            this.query = query;
            return this;
        }

        private NavEntry withPage(int page) {
            this.page = page;
            return this;
        }

        private NavEntry withTotalPlayers(int totalPlayers) {
            this.totalPlayers = totalPlayers;
            return this;
        }
    }
}