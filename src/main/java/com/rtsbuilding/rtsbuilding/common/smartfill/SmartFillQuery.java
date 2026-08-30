package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.core.BlockPos;

/** 智能填洞规划器使用的只读世界查询端口。 */
@FunctionalInterface
public interface SmartFillQuery {
    SmartFillCell classify(BlockPos pos);
}
