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
 * <p>本类明确只允许客户端调用；服务端只依赖不含任何渲染类型的
 * {@code RtsSableSpatialCompat}，避免专用服务器在验证类时加载客户端类。</p>
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

    /**
     * 显式投影后再计算距离，避开 Companion 1.6.0 的 {@code Position} 默认重载回归。
     */
    public static double renderDistanceSquared(Level level, Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        // a 是主相机的全局坐标，不能再按 plot 逻辑坐标投影；只有命中点 b 需要转换。
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

    /** 在已经减去相机位置的 PoseStack 上追加 plot→主世界渲染矩阵。 */
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
        Matrix4f matrix = new Matrix4f().set(matrixValues);
        poseStack.mulPose(matrix);
        return true;
    }

    /**
     * 按 Sable 原版方块黑框的方式建立单方块局部渲染帧。
     *
     * <p>调用方传入的 PoseStack 已经减去主相机位置。这里先用 double 精度把 plot 中的
     * 方块原点投影到主世界，再只追加飞船旋转；随后调用方必须使用 0..1 附近的局部顶点。
     * 这样不会让很大的 plot 绝对坐标进入 float 矩阵，避免高亮和虚影漂移。</p>
     *
     * @return 位于 Sable 子世界时返回 true；调用方此时应改用相对方块原点的局部坐标
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

    /** 保留船内逻辑坐标，只按其当前画面物理位置过滤。 */
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
