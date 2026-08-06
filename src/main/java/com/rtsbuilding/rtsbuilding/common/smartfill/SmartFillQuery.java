package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.util.math.BlockPos;

/** 智能填洞只读世界查询；客户端预览和服务端重算共用。 */
public interface SmartFillQuery {
    SmartFillCell classify(BlockPos pos);
}
