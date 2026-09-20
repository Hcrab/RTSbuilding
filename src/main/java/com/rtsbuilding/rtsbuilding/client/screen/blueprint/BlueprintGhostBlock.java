package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 蓝图预览中的单个不可变方块快照。
 *
 * <p>坐标是蓝图局部坐标；{@link BlueprintGhostPreview#blocks()} 通过惰性视图
 * 提供带锚点的世界坐标。渲染器不应回读面板或蓝图文件。</p>
 */
public record BlueprintGhostBlock(BlockPos pos, BlockState state, boolean missing) {
}
