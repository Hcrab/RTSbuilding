package com.rtsbuilding.rtsbuilding.server.storage.cache;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Forge 1.20.1 存储浏览器使用的完整物品变体身份。
 *
 * <p>1.20.1 没有 1.21.1 的数据组件哈希，因此使用设为单个后的完整序列化栈作为键。
 * 这不仅包含普通标签，也保留 Forge 能力数据，从而不再把同 ID、
 * 不同耐久/附魔/内容的物品错误聚合成一项。</p>
 */
public final class RtsItemVariantKey {
    private final String itemId;
    private final ItemStack prototype;
    private final CompoundTag serializedStack;
    private final int hashCode;

    private RtsItemVariantKey(ItemStack stack) {
        this.prototype = stack.copy();
        this.prototype.setCount(1);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        this.itemId = id == null ? "" : id.toString();
        this.serializedStack = this.prototype.serializeNBT();
        this.hashCode = 31 * this.itemId.hashCode() + this.serializedStack.hashCode();
    }

    public static RtsItemVariantKey of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        RtsItemVariantKey key = new RtsItemVariantKey(stack);
        return key.itemId.isEmpty() ? null : key;
    }

    public String itemId() {
        return this.itemId;
    }

    public ItemStack prototype() {
        return this.prototype.copy();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RtsItemVariantKey that)) return false;
        return this.hashCode == that.hashCode
                && this.itemId.equals(that.itemId)
                && Objects.equals(this.serializedStack, that.serializedStack)
                && ItemStack.isSameItemSameTags(this.prototype, that.prototype);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }
}
