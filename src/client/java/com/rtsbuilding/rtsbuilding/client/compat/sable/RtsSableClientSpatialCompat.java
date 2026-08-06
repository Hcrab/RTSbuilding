package com.rtsbuilding.rtsbuilding.client.compat.sable;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Sable 客户端插值姿态、局部射线和渲染矩阵适配。
 *
 * <p>仅客户端调用。未安装 Sable 时，随 RTS 打包的 Companion 返回空子世界，所有方法
 * 自然退化为原版坐标、射线和渲染路径。</p>
 */
public final class RtsSableClientSpatialCompat {
    private RtsSableClientSpatialCompat() {
    }

    public static Vec3 projectRenderToGlobal(Level level, Vec3 logicalPos) {
        if (level == null || logicalPos == null) {
            return logicalPos;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, logicalPos);
        if (subLevel instanceof ClientSubLevelAccess clientSubLevel) {
            return clientSubLevel.renderPose().transformPosition(logicalPos);
        }
        return subLevel == null ? logicalPos : subLevel.logicalPose().transformPosition(logicalPos);
    }

    /** 显式投影后再计算距离，避免 Companion Position 默认重载语义不明确。 */
    public static double renderDistanceSquared(Level level, Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        // 相机是主世界全局坐标；只投影 plot 内的命中点。
        return a.distanceToSqr(projectRenderToGlobal(level, b));
    }

    public static Ray toRenderLocalRay(
            Level level, BlockPos framePosition, Vec3 globalOrigin, Vec3 globalDirection) {
        if (level == null || framePosition == null || globalOrigin == null || globalDirection == null) {
            return new Ray(globalOrigin, globalDirection);
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, framePosition);
        Pose3dc pose = subLevel instanceof ClientSubLevelAccess clientSubLevel
                ? clientSubLevel.renderPose()
                : subLevel == null ? null : subLevel.logicalPose();
        if (pose == null) {
            return new Ray(globalOrigin, globalDirection);
        }
        Vec3 localOrigin = pose.transformPositionInverse(globalOrigin);
        Vec3 localDirection = pose.transformNormalInverse(globalDirection);
        return new Ray(localOrigin, localDirection.lengthSqr() < 1.0E-9D
                ? globalDirection
                : localDirection.normalize());
    }

    /** 在已减去相机位置的 PoseStack 上追加 plot 到主世界的完整渲染姿态。 */
    public static boolean applyRenderPose(Level level, BlockPos logicalPos, PoseStack poseStack) {
        if (level == null || logicalPos == null || poseStack == null) {
            return false;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, logicalPos);
        if (!(subLevel instanceof ClientSubLevelAccess clientSubLevel)) {
            return false;
        }
        float[] matrixValues = new float[16];
        clientSubLevel.renderPose().bakeIntoMatrix(new Matrix4d()).get(matrixValues);
        poseStack.mulPose(new Matrix4f().set(matrixValues));
        return true;
    }

    /**
     * 建立单方块局部渲染帧。
     *
     * <p>先以 double 精度投影逻辑方块的 lower corner，再只将方向写入 float 矩阵；后续
     * 顶点必须使用 0..1 附近的局部坐标。这样不会把大 plot 的绝对坐标塞入 float 矩阵，
     * 避免高亮和幽灵的精度漂移。</p>
     */
    public static boolean applyBlockRenderFrame(Level level, BlockPos logicalPos, PoseStack poseStack) {
        if (level == null || logicalPos == null || poseStack == null) {
            return false;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, logicalPos);
        if (!(subLevel instanceof ClientSubLevelAccess clientSubLevel)) {
            return false;
        }
        Pose3dc renderPose = clientSubLevel.renderPose();
        Vec3 renderedOrigin = renderPose.transformPosition(Vec3.atLowerCornerOf(logicalPos));
        poseStack.translate(renderedOrigin.x, renderedOrigin.y, renderedOrigin.z);
        poseStack.mulPose(new Quaternionf().set(renderPose.orientation()));
        return true;
    }

    public static void renderInFrame(Level level, BlockPos logicalPos, PoseStack poseStack, Runnable renderer) {
        poseStack.pushPose();
        try {
            applyRenderPose(level, logicalPos, poseStack);
            renderer.run();
        } finally {
            poseStack.popPose();
        }
    }

    public static boolean isWithinBounds(Level level, BlockPos logicalPos,
            double anchorX, double anchorZ, double maxRadius) {
        if (logicalPos == null) {
            return false;
        }
        Vec3 global = projectRenderToGlobal(level, Vec3.atCenterOf(logicalPos));
        return Math.abs(global.x - anchorX) <= maxRadius
                && Math.abs(global.z - anchorZ) <= maxRadius;
    }

    /** 保留 plot 内逻辑坐标，仅按当前物理位置过滤。 */
    public static List<BlockPos> filterWithinBounds(Level level, List<BlockPos> blocks,
            double anchorX, double anchorZ, double maxRadius) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        List<BlockPos> result = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (isWithinBounds(level, pos, anchorX, anchorZ, maxRadius)) {
                result.add(pos);
            }
        }
        return result.size() == blocks.size() ? blocks : result.isEmpty() ? List.of() : result;
    }

    public record Ray(Vec3 origin, Vec3 direction) {
    }
}
