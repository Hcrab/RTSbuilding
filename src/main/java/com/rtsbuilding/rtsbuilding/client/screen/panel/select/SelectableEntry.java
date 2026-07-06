package com.rtsbuilding.rtsbuilding.client.screen.panel.select;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 可选择条目的通用接口——定义交互目标选择面板中每个选项的行为。
 *
 * <p>当前有两种实现：{@link EntityEntry}（实体）和 {@link BlockEntry}（方块）。</p>
 */
public sealed interface SelectableEntry permits EntityEntry, BlockEntry {

    /** 条目显示名称 */
    String displayName();

    /**
     * 唯一标识符，用于增量更新时匹配旧条目。
     * <ul>
     *   <li>{@link EntityEntry} → 实体 ID ({@code int})</li>
     *   <li>{@link BlockEntry} → 方块坐标 ({@link BlockPos})</li>
     * </ul>
     */
    Object identifier();
}
