package com.rtsbuilding.rtsbuilding.forgecompat.network;

import net.minecraft.world.item.ItemStack;

/**
 * Small codecs for payload records that use Minecraft 1.21's built-in
 * ItemStack stream codec on mainline.
 */
public final class RtsForgeBufCodecs {
    private RtsForgeBufCodecs() {
    }

    public static void writeItem(RegistryFriendlyByteBuf buffer, ItemStack stack) {
        buffer.writeItem(stack == null ? ItemStack.EMPTY : stack);
    }

    public static ItemStack readItem(RegistryFriendlyByteBuf buffer) {
        return buffer.readItem();
    }
}
