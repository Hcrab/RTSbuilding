package com.rtsbuilding.rtsbuilding.server.plugin;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** 保存真实插件栈的不可变安装记录；构造时固定为一件但完整保留 NBT。 */
public final class RtsInstalledPlugin {
    private final ResourceLocation pluginId;
    private final ItemStack stack;
    private final long installedGameTime;

    public RtsInstalledPlugin(ResourceLocation pluginId, ItemStack stack, long installedGameTime) {
        this.pluginId = pluginId;
        this.stack = copyOne(stack);
        this.installedGameTime = installedGameTime;
    }

    public ResourceLocation pluginId() { return pluginId; }
    public ItemStack stack() { return stack; }
    public long installedGameTime() { return installedGameTime; }

    static ItemStack copyOne(ItemStack source) {
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = source.copy();
        copy.setCount(1);
        return copy;
    }
}
