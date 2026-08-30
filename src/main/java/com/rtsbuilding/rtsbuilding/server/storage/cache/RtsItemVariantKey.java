package com.rtsbuilding.rtsbuilding.server.storage.cache;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 存储浏览器使用的完整物品变体身份。
 *
 * <p>同一物品 ID 的 ItemStack 只要组件不同，就必须进入不同的变体桶。
 * 这里保存数量为 1 的不可变语义原型，并使用 Minecraft 自身的组件哈希与
 * {@link ItemStack#isSameItemSameComponents(ItemStack, ItemStack)} 作为身份规则。
 */
public final class RtsItemVariantKey {
    private final String itemId;
    private final ItemStack prototype;
    private final int hashCode;

    private RtsItemVariantKey(ItemStack stack) {
        this.prototype = stack.copyWithCount(1);
        this.hashCode = ItemStack.hashItemAndComponents(this.prototype);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(this.prototype.getItem());
        this.itemId = id == null ? "" : id.toString();
    }

    /** 从非空堆叠创建变体键；未注册物品返回 {@code null}。 */
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

    /** 返回数量为 1 的副本，避免调用方修改键内部状态。 */
    public ItemStack prototype() {
        return this.prototype.copyWithCount(1);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RtsItemVariantKey that)) return false;
        return this.hashCode == that.hashCode
                && ItemStack.isSameItemSameComponents(this.prototype, that.prototype);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public String toString() {
        return this.itemId + "#" + this.hashCode;
    }
}
