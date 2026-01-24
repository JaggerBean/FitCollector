package com.stepcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public class StepCraftActionMenuScreenHandler extends GenericContainerScreenHandler {
    private final String targetPlayer;

    public StepCraftActionMenuScreenHandler(int syncId, PlayerInventory playerInventory, String targetPlayer) {
        super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, new SimpleInventory(9), 1);
        this.targetPlayer = targetPlayer;
        // Fill with action items
        this.getInventory().setStack(0, makeActionItem(new ItemStack(Items.IRON_SWORD), "Ban Player"));
        this.getInventory().setStack(1, makeActionItem(new ItemStack(Items.PAPER), "Unban Player"));
        this.getInventory().setStack(2, makeActionItem(new ItemStack(Items.BARRIER), "Delete Player"));
    }

    private ItemStack makeActionItem(ItemStack stack, String name) {
        net.minecraft.nbt.NbtCompound display = new net.minecraft.nbt.NbtCompound();
        display.putString("Name", "{\"text\":\"" + name + "\"}");
        net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
        tag.put("display", display);
        try {
            java.lang.reflect.Method setTag = ItemStack.class.getMethod("setTag", net.minecraft.nbt.NbtCompound.class);
            setTag.invoke(stack, tag);
        } catch (Exception e) {
            // fallback: do nothing or log
        }
        return stack;
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }
}
