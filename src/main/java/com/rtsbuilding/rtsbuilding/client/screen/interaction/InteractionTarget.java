package com.rtsbuilding.rtsbuilding.client.screen.interaction;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 交互轮盘目标数据。
 * <p>
 * 记录交互轮盘的作用目标，可以是实体或方块。
 *
 * @param entityId   目标实体 ID（-1 表示无效，即方块目标）
 * @param hitLocation 命中位置坐标
 * @param blockHit    方块命中结果（可能为 null）
 * @param rayOrigin   射线起点
 * @param rayDir      射线方向
 */
public record InteractionTarget(
        int entityId,
        Vec3 hitLocation,
        BlockHitResult blockHit,
        Vec3 rayOrigin,
        Vec3 rayDir) {
    /**
     * 判断是否为实体目标。
     *
     * @return 如果目标为实体则返回 true
     */
    public boolean isEntityTarget() {
        return this.entityId >= 0;
    }
}
