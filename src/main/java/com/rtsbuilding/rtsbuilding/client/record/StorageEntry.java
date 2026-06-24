package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.world.item.ItemStack;

/**
 * 存储条目——不可变数据记录，用于 UI 渲染。
 */
public record StorageEntry(
        ItemStack stack,
        String itemId,
        long count,
        String namespace,
        String path
) {}
