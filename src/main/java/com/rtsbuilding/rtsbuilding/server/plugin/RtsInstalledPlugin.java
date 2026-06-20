package com.rtsbuilding.rtsbuilding.server.plugin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * One player-owned installed plugin entry.
 *
 * <p>The stored {@link ItemStack} is the real plugin item consumed from the
 * player. Keeping the stack instead of only an item id preserves future NBT or
 * component data and makes uninstall safe for modpack/plugin variants.
 */
public record RtsInstalledPlugin(ResourceLocation pluginId, ItemStack stack, long installedGameTime) {
    public RtsInstalledPlugin {
        stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
    }
}
