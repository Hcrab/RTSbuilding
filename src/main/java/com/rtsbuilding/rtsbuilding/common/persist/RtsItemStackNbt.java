package com.rtsbuilding.rtsbuilding.common.persist;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 隔离 26.1 的物品栈 Codec/NBT 读写细节。
 *
 * <p>业务层仍然以 {@link CompoundTag} 为持久化边界；本类负责把注册表上下文
 * 接到新版 {@link ItemStack#CODEC}，避免各储存、任务和回收逻辑分别依赖版本 API。
 * 解码失败时保守返回空栈，由调用方沿用既有的“忽略损坏条目”行为。
 */
public final class RtsItemStackNbt {
    private RtsItemStackNbt() {
    }

    public static CompoundTag save(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        Tag encoded = ItemStack.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .result()
                .orElse(null);
        return encoded instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    public static ItemStack load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result()
                .orElse(ItemStack.EMPTY);
    }
}
