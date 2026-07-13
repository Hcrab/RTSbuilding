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
        String path,
        byte linkedMode
) {
    /** 双向模式 */
    public static final byte MODE_BIDIRECTIONAL = 0;
    /** 仅提取模式 */
    public static final byte MODE_EXTRACT_ONLY = 1;

    /** 是否来自双向绑定容器 */
    public boolean isBidirectional() {
        return linkedMode == MODE_BIDIRECTIONAL;
    }

    /** 是否来自仅提取容器 */
    public boolean isExtractOnly() {
        return linkedMode == MODE_EXTRACT_ONLY;
    }
}
