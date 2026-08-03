package com.rtsbuilding.rtsbuilding.platform;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 统一主线无上下文“可替换”判断与 1.19.2 材质 API 的语义差异。
 *
 * <p>本类只回答方块状态本身是否属于可替换材质，不处理玩家、手持物、放置方向或
 * 世界权限等带上下文规则。这样业务层能保留主线的无上下文判断语义，同时避免把
 * 1.19.2 的材质 API 散落到蓝图与放置流程中。</p>
 */
public final class RtsBlockStates {
    private RtsBlockStates() {
    }

    public static boolean canBeReplaced(BlockState state) {
        return state != null && state.getMaterial().isReplaceable();
    }
}
