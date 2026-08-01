package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 蓝图世界虚影中的单个方块快照。
 *
 * <p>本记录只携带已经完成旋转的世界坐标、方块状态和“原蓝图方块缺失”标记，不负责
 * 材料判断、范围裁剪或实际渲染。将它从 BlueprintPanel 中移出后，预览生成器与各渲染器
 * 不再为了读取一条数据而依赖整个面板总状态。</p>
 */
public record BlueprintGhostBlock(
        BlockPos pos,
        BlockState state,
        boolean missing) {
}
