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
 * RTSBuilding 的 Sable 子世界空间适配唯一入口。
 *
 * <p>世界读写始终使用传入的逻辑坐标；只有距离、RTS 边界、领地、临时玩家位置和渲染
 * 才投影到主世界。该类不依赖 Sable 本体：随 RTS 打包的 Sable Companion 在未安装
 * Sable 时提供恒等实现，因此普通 Fabric 客户端和专用服务器的行为保持不变。</p>
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

    /** 返回逻辑方块当前物理中心对应的主世界方块坐标，供范围与领地判断使用。 */
    public static BlockPos physicalBlockPos(Level level, BlockPos logicalPos) {
        if (logicalPos == null) {
            return null;
        }
        return BlockPos.containing(projectLogicalToGlobal(level, Vec3.atCenterOf(logicalPos)));
    }

    /** 将逻辑方块面转换为当前物理姿态下最接近的主世界方向。 */
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
        return squaredDistance(globalA, globalB);
    }

    /** 保持为纯数学入口，供无运行时子世界服务的单元测试验证三轴距离语义。 */
    static double squaredDistance(Vec3 first, Vec3 second) {
        return first == null || second == null ? Double.MAX_VALUE : first.distanceToSqr(second);
    }

    /** 查询逻辑位置所属的稳定子世界 UUID；主世界或未加载子世界返回 {@code null}。 */
    public static UUID frameId(Level level, BlockPos logicalPos) {
        if (level == null || logicalPos == null) {
            return null;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, logicalPos);
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    /** 两个逻辑位置是否仍属于同一主世界或同一 plot。 */
    public static boolean sameFrame(Level level, BlockPos first, BlockPos second) {
        return first != null && second != null
                && Objects.equals(frameId(level, first), frameId(level, second));
    }
}
