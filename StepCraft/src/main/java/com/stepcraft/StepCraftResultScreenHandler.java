package com.stepcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

        try {
            JsonElement element = JsonParser.parseString(message);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String formatted = gson.toJson(element);
            for (String line : formatted.split("\\n")) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
        } catch (Exception ignored) {
            lines.add(message);
        }
        return lines;
    }

    private static List<String> toPagesFromLines(List<String> lines) {
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
