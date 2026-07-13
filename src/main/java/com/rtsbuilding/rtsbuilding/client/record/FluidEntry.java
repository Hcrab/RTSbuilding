package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.world.item.ItemStack;

/**
 * 流体条目——不可变数据记录，用于 UI 渲染。
 *
 * <p>通过 {@link ItemStack} 类型的 {@code preview} 字段渲染流体图标，
 * 使用 {@link net.neoforged.neoforge.fluids.FluidUtil#getFilledBucket} 生成预览物品。</p>
 */
public record FluidEntry(
        String fluidId,
        String label,
        long amount,
        long capacity,
        String namespace,
        String path,
        ItemStack preview
) {}
