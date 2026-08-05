package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.entity.EntityPlayerSP;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端移动策略接口。第三方模组可把自定义策略注册到
 * {@link RtsMovementModeRegistry}；优先级越高越先检测。
 */
@SideOnly(Side.CLIENT)
public interface MovementModeHandler {
    boolean isActive(EntityPlayerSP player);

    MovementParams computeParams(EntityPlayerSP player, Vec3d toTarget, double horizontalDist);

    default void onActivate(EntityPlayerSP player) {
    }

    default void onDeactivate(EntityPlayerSP player) {
    }
}
