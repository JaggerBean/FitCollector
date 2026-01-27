package com.stepcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Unit;
import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class StepCraftResultScreenHandler extends GenericContainerScreenHandler {
    private final SimpleInventory inventory;
    private List<String> lines = new ArrayList<>();
    private int page = 0;
    private final ItemStack fillerPane;
    private static final int ROWS = 6;
    private static final int PAGE_LINES = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_LECTERN = 48;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    public StepCraftResultScreenHandler(int syncId, PlayerInventory playerInventory, String message) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(ROWS * 9), ROWS);
        this.inventory = (SimpleInventory) this.getInventory();

        ItemStack pane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        this.fillerPane = pane;
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, pane.copy());
        }

        setResult(message);
        inventory.setStack(SLOT_BACK, menuItem(Items.BOOK, "Back", 0xFFFFFF));
    }

    public void setResult(String message) {
        this.lines = toDisplayLines(message);
        this.page = 0;
        renderPage();
        inventory.markDirty();
        sendContentUpdates();
    }

    @Override
    public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            super.onSlotClick(slot, button, actionType, player);
            return;
        }

        if (slot == SLOT_PREV && page > 0) {
            page--;
            renderPage();
            return;
        }

        if (slot == SLOT_NEXT && (page + 1) * PAGE_LINES < lines.size()) {
            page++;
            renderPage();
            return;
        }

        if (slot == SLOT_LECTERN) {
            StepCraftLecternHelper.openLectern(serverPlayer, "Result", toPagesFromLines(lines));
            return;
        }

        if (slot == SLOT_BACK) {
            StepCraftUIHelper.openPlayersList(serverPlayer);
            return;
        }
    }

    private ItemStack menuItem(net.minecraft.item.Item item, String label, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false))
        );
        return stack;
    }


    public static List<String> toDisplayLines(String message) {
        List<String> lines = new ArrayList<>();
        if (message == null || message.isBlank()) {
            lines.add("No result");
            return lines;
        }

        if (message.contains("Error:") || message.contains("error:")) {
            for (String line : message.split("\n")) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
            return lines;
        }

        try {
            String extracted = extractJsonSubstring(message);
            String label = extractLeadingLabel(message, extracted);
            JsonElement element = JsonParser.parseString(extracted != null ? extracted : message);
            element = unwrapJsonString(element);
            List<String> filtered = filterLinesForLabel(label, element);
            if (filtered != null && !filtered.isEmpty()) {
                lines.addAll(filtered);
            } else {
                if (label != null && !label.isBlank()) {
                    lines.add(prettyTitle(label));
                } else {
                    lines.add("Result");
                }
                lines.add("──────────");
                formatJsonLines(element, "", lines);
            }
        } catch (Exception ignored) {
            for (String line : message.split("\\n")) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
        }
        return lines;
    }

    private static List<String> filterLinesForLabel(String label, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }

        String key = label == null ? "" : label.trim().toLowerCase();
        JsonObject obj = element.getAsJsonObject();

        if (key.contains("health")) {
            List<String> lines = new ArrayList<>();
            lines.add("Health Check");
            lines.add("──────────");
            boolean ok = obj.has("ok") && obj.get("ok").isJsonPrimitive() && obj.get("ok").getAsBoolean();
            lines.add("Status: " + (ok ? "OK" : "FAIL"));
            return lines;
        }

        if (key.contains("server info")) {
            List<String> lines = new ArrayList<>();
            lines.add("Server Info");
            lines.add("──────────");
            addLine(lines, "Server Name", getAsString(obj, "server_name"));
            addLine(lines, "Active", getAsString(obj, "active"));
            addLine(lines, "Current Players", getAsString(obj, "current_players"));
            String maxPlayers = obj.has("max_players") && !obj.get("max_players").isJsonNull()
                    ? getAsString(obj, "max_players") : "Unlimited";
            addLine(lines, "Max Players", maxPlayers);
            String slots = obj.has("slots_available") && !obj.get("slots_available").isJsonNull()
                    ? getAsString(obj, "slots_available") : "Unlimited";
            addLine(lines, "Slots Available", slots);
            addLine(lines, "Created", formatValue(getAsString(obj, "created_at")));
            addLine(lines, "Last Used", formatValue(getAsString(obj, "last_used")));
            return lines;
        }

        if (key.contains("server bans")) {
            List<String> lines = new ArrayList<>();
            lines.add("Server Bans");
            lines.add("──────────");
            addLine(lines, "Server", getAsString(obj, "server_name"));
            int total = getAsInt(obj, "total_bans");
            addLine(lines, "Total", String.valueOf(total));

            if (obj.has("bans") && obj.get("bans").isJsonArray()) {
                JsonArray bans = obj.getAsJsonArray("bans");
                int limit = Math.min(10, bans.size());
                for (int i = 0; i < limit; i++) {
                    JsonObject ban = bans.get(i).getAsJsonObject();
                    String user = getAsString(ban, "username");
                    String reason = getAsString(ban, "reason");
                    String when = formatValue(getAsString(ban, "banned_at"));
                    if (user == null || user.isBlank()) {
                        user = "(device ban)";
                    }
                    lines.add("• " + user + " - " + reason);
                    if (when != null && !when.isBlank()) {
                        lines.add("  " + when);
                    }
                }
                if (bans.size() > limit) {
                    lines.add("… and " + (bans.size() - limit) + " more");
                }
            }
            return lines;
        }

        if (key.contains("all players")) {
            List<String> lines = new ArrayList<>();
            lines.add("All Players");
            lines.add("──────────");
            addLine(lines, "Server", getAsString(obj, "server_name"));
            addLine(lines, "Players", getAsString(obj, "player_count"));
            addLine(lines, "Records", getAsString(obj, "total_records"));

            if (obj.has("data") && obj.get("data").isJsonArray()) {
                JsonArray data = obj.getAsJsonArray("data");
                int limit = Math.min(10, data.size());
                for (int i = 0; i < limit; i++) {
                    JsonObject row = data.get(i).getAsJsonObject();
                    String user = getAsString(row, "minecraft_username");
                    String day = getAsString(row, "day");
                    String steps = getAsString(row, "steps_today");
                    lines.add("• " + user + " - " + steps + " (" + day + ")");
                }
                if (data.size() > limit) {
                    lines.add("… and " + (data.size() - limit) + " more");
                }
            }
            return lines;
        }

        if (key.contains("claim status")) {
            List<String> lines = new ArrayList<>();
            lines.add("Claim Status");
            lines.add("──────────");
            addLine(lines, "Claimed", getAsString(obj, "claimed"));
            addLine(lines, "Claimed At", formatValue(getAsString(obj, "claimed_at")));
            return lines;
        }

        if (key.contains("claim reward")) {
            List<String> lines = new ArrayList<>();
            lines.add("Claim Reward");
            lines.add("──────────");
            addLine(lines, "Claimed", getAsString(obj, "claimed"));
            addLine(lines, "Claimed At", formatValue(getAsString(obj, "claimed_at")));
            return lines;
        }

        if (key.contains("yesterday's steps")) {
            List<String> lines = new ArrayList<>();
            lines.add("Yesterday's Steps");
            lines.add("──────────");
            addLine(lines, "Minecraft Username", getAsString(obj, "minecraft_username"));
            addLine(lines, "Server Name", getAsString(obj, "server_name"));
            addLine(lines, "Day", getAsString(obj, "day"));
            addLine(lines, "Steps Yesterday", getAsString(obj, "steps_yesterday"));
            return lines;
        }

        if (key.contains("ban") || key.contains("unban") || key.contains("delete")) {
            List<String> lines = new ArrayList<>();
            lines.add(prettyTitle(label));
            lines.add("──────────");
            addLine(lines, "Player", getAsString(obj, "minecraft_username"));
            addLine(lines, "Server", getAsString(obj, "server_name"));
            addLine(lines, "Action", getAsString(obj, "action"));
            addLine(lines, "Message", getAsString(obj, "message"));
            if (obj.has("bans_removed")) {
                addLine(lines, "Bans Removed", getAsString(obj, "bans_removed"));
            }
            if (obj.has("steps_deleted")) {
                addLine(lines, "Steps Deleted", getAsString(obj, "steps_deleted"));
            }
            if (obj.has("keys_deleted")) {
                addLine(lines, "Keys Deleted", getAsString(obj, "keys_deleted"));
            }
            return lines;
        }

        return null;
    }

    private static void addLine(List<String> lines, String label, String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return;
        }
        lines.add(label + ": " + value);
    }

    private static String getAsString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return obj.get(key).toString();
        }
    }

    private static int getAsInt(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return 0;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            try {
                return Integer.parseInt(obj.get(key).getAsString());
            } catch (Exception ignoredAgain) {
                return 0;
            }
        }
    }

    private static String extractJsonSubstring(String message) {
        if (message == null) return null;
        int firstObj = message.indexOf('{');
        int lastObj = message.lastIndexOf('}');
        if (firstObj >= 0 && lastObj > firstObj) {
            return message.substring(firstObj, lastObj + 1);
        }
        int firstArr = message.indexOf('[');
        int lastArr = message.lastIndexOf(']');
        if (firstArr >= 0 && lastArr > firstArr) {
            return message.substring(firstArr, lastArr + 1);
        }
        return null;
    }

    private static String extractLeadingLabel(String message, String extractedJson) {
        if (message == null || extractedJson == null) return null;
        int idx = message.indexOf(extractedJson);
        if (idx <= 0) return null;
        String label = message.substring(0, idx).trim();
        if (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).trim();
        }
        return label.isBlank() ? null : label;
    }

    private static JsonElement unwrapJsonString(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString().trim();
            if ((raw.startsWith("{") && raw.endsWith("}")) || (raw.startsWith("[") && raw.endsWith("]"))) {
                try {
                    return JsonParser.parseString(raw);
                } catch (Exception ignored) {
                    return element;
                }
            }
        }
        return element;
    }

    private static void formatJsonLines(JsonElement element, String indent, List<String> lines) {
        if (element == null || element.isJsonNull()) {
            lines.add(indent + "N/A");
            return;
        }

        if (element.isJsonPrimitive()) {
            lines.add(indent + element.getAsJsonPrimitive().getAsString());
            return;
        }

        if (element.isJsonArray()) {
            int index = 1;
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    lines.add(indent + "• " + item.getAsJsonPrimitive().getAsString());
                } else {
                    lines.add(indent + "• Item " + index + ":");
                    formatJsonLines(item, indent + "  ", lines);
                }
                index++;
            }
            return;
        }

        if (element.isJsonObject()) {
            for (String key : element.getAsJsonObject().keySet()) {
                JsonElement value = element.getAsJsonObject().get(key);
                String prettyKey = prettyKey(key);
                if (value == null || value.isJsonNull()) {
                    lines.add(indent + prettyKey + ": N/A");
                } else if (value.isJsonPrimitive()) {
                    lines.add(indent + prettyKey + ": " + formatValue(value.getAsJsonPrimitive().getAsString()));
                } else {
                    lines.add(indent + prettyKey + ":");
                    formatJsonLines(value, indent + "  ", lines);
                }
            }
        }
    }

    private static String prettyKey(String key) {
        if (key == null) return "";
        String[] parts = key.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static String prettyTitle(String label) {
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty()) return "Result";
        return prettyKey(trimmed);
    }

    private static String formatValue(String value) {
        if (value == null) return "N/A";
        String trimmed = value.trim();
        try {
            if (trimmed.contains("T") && (trimmed.endsWith("Z") || trimmed.contains("+"))) {
                OffsetDateTime dt = OffsetDateTime.parse(trimmed);
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String base = dt.withOffsetSameInstant(ZoneOffset.UTC).format(fmt);
                return base + " UTC";
            }
        } catch (Exception ignored) {
            // fall through
        }
        return trimmed;
    }

    public static List<String> toPagesFromLines(List<String> lines) {
        List<String> pages = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            pages.add("No result");
            return pages;
        }

        StringBuilder page = new StringBuilder();
        int lineCount = 0;
        for (String line : lines) {
            page.append(line).append("\n");
            lineCount++;
            if (lineCount >= 13) {
                pages.add(page.toString());
                page.setLength(0);
                lineCount = 0;
            }
        }
        if (page.length() > 0) {
            pages.add(page.toString());
        }
        return pages;
    }


    private void renderPage() {
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, fillerPane.copy());
        }

        int start = page * PAGE_LINES;
        int end = Math.min(start + PAGE_LINES, lines.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(lines.get(i)).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)).withItalic(false))
            );
            inventory.setStack(slot, paper);
            slot++;
        }

        if (page > 0) {
            inventory.setStack(SLOT_PREV, menuItem(Items.ARROW, "Prev", 0xAAAAAA));
        }
        inventory.setStack(SLOT_LECTERN, menuItem(Items.LECTERN, "Open Lectern", 0xFFFF55));
        inventory.setStack(SLOT_BACK, menuItem(Items.BOOK, "Back", 0xFFFFFF));
        if ((page + 1) * PAGE_LINES < lines.size()) {
            inventory.setStack(SLOT_NEXT, menuItem(Items.ARROW, "Next", 0xAAAAAA));
        }

        inventory.markDirty();
        sendContentUpdates();
    }
}
