package com.rtsbuilding.rtsbuilding.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * RTSBuilding 与 Sable 子世界之间唯一的空间坐标适配入口。
 *
 * <p>世界读写始终继续使用传入的逻辑坐标；只有距离、RTS 边界、claim、临时玩家位置和渲染
 * 才投影到主世界。这个类不负责修改 Sable 物理状态，也不直接依赖 Sable 内部类，因此后续工具
 * 只需复用这里，不必重新理解 plot 坐标。</p>
 */
public final class RtsSableSpatialCompat {
    private RtsSableSpatialCompat() {
    }

    /** 将逻辑位置按服务端当前姿态投影到主世界；普通世界位置原样返回。 */
    public static Vec3 projectLogicalToGlobal(Level level, Vec3 logicalPos) {
        if (level == null || logicalPos == null) {
            return logicalPos;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, logicalPos);
        return subLevel == null ? logicalPos : subLevel.logicalPose().transformPosition(logicalPos);
    }

    /** 返回逻辑方块当前物理中心对应的主世界方块坐标，供范围与 claim 判断使用。 */
    public static BlockPos physicalBlockPos(Level level, BlockPos logicalPos) {
        if (logicalPos == null) {
            return null;
        }
        return BlockPos.containing(projectLogicalToGlobal(level, Vec3.atCenterOf(logicalPos)));
    }

    /** 将船内方块面转换为当前物理姿态下最接近的主世界方向。 */
    public static Direction physicalDirection(Level level, BlockPos logicalPos, Direction logicalDirection) {
        if (level == null || logicalPos == null || logicalDirection == null) {
            return logicalDirection;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, logicalPos);
        if (subLevel == null) {
            return logicalDirection;
        }
        Vec3 physicalNormal = subLevel.logicalPose().transformNormal(new Vec3(
                logicalDirection.getStepX(), logicalDirection.getStepY(), logicalDirection.getStepZ()));
        return Direction.getNearest(physicalNormal.x, physicalNormal.y, physicalNormal.z);
    }

    /** 服务端逻辑姿态下的真实距离平方。 */
    public static double logicalDistanceSquared(Level level, Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        Vec3 globalA = projectLogicalToGlobal(level, a);
        Vec3 globalB = projectLogicalToGlobal(level, b);
        return globalA.distanceToSqr(globalB);
    }

    /** 查询一个逻辑位置所属的稳定子世界 UUID；主世界或未加载子世界返回 {@code null}。 */
    public static UUID frameId(Level level, BlockPos logicalPos) {
        if (level == null || logicalPos == null) {
            return null;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, logicalPos);
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    /** 两个逻辑位置是否仍属于同一主世界/同一艘飞船。 */
    public static boolean sameFrame(Level level, BlockPos first, BlockPos second) {
        return first != null && second != null
                && Objects.equals(frameId(level, first), frameId(level, second));
    }

}
