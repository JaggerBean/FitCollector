package com.stepcraft;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public record StepCraftChestOpenData(Text title, DefaultedList<ItemStack> items) {

    public static final int SIZE = 27;

    // Fabric 1.20.6 expects a PacketCodec for the opening payload.
    public static final PacketCodec<RegistryByteBuf, StepCraftChestOpenData> PACKET_CODEC =
            new PacketCodec<>() {
                @Override
                public StepCraftChestOpenData decode(RegistryByteBuf buf) {
                    Text title = Text.Serialization.fromJson(buf.readString(), buf.getRegistryManager());

                    DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
                    for (int i = 0; i < SIZE; i++) {
                        items.set(i, ItemStack.PACKET_CODEC.decode(buf));
                    }
                    return new StepCraftChestOpenData(title, items);
                }

                @Override
                public void encode(RegistryByteBuf buf, StepCraftChestOpenData value) {
                    // Encode Text safely via JSON string
                    buf.writeString(Text.Serialization.toJsonString(value.title(), buf.getRegistryManager()));

                    // Always encode exactly 27 stacks
                    for (int i = 0; i < SIZE; i++) {
                        ItemStack stack = value.items() != null && i < value.items().size()
                                ? value.items().get(i)
                                : ItemStack.EMPTY;
                        ItemStack.PACKET_CODEC.encode(buf, stack);
                    }
                }
            };
}
