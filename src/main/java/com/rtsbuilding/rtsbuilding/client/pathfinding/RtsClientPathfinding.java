package com.rtsbuilding.rtsbuilding.client.pathfinding;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Forge 1.20.1 的轻量客户端移动器。
 *
 * <p>它只负责 Ctrl+右键移动玩家这一条可见交互：记录目标、在客户端 tick 前设置本地玩家速度、
 * 到达后停止。它不拥有 RTS 交互、容器打开、远程放置或工作流状态，也不向服务端注册新协议。</p>
 */
public final class RtsClientPathfinding {
    private static final double WALK_SPEED = 0.19D;
    private static final double FLY_SPEED = 0.32D;
    private static final double SWIM_SPEED = 0.12D;
    private static final double ARRIVAL_HORIZONTAL_SQ = 0.28D * 0.28D;
    private static final double ARRIVAL_3D_SQ = 0.36D * 0.36D;
    private static final double EPSILON = 0.0001D;
    private static final long TARGET_HIGHLIGHT_FADE_MS = 350L;

    private static BlockPos target;
    private static BlockPos highlightedTarget;
    private static long highlightFadeStartedAtMs;
    private static boolean highlightFading;
    private static int targetYOffset;

    private RtsClientPathfinding() {
    }

    public static void goTo(BlockPos target) {
        BlockPos immutableTarget = target == null ? null : target.immutable();
        RtsClientPathfinding.target = immutableTarget;
        targetYOffset = 0;
        setHighlightedTarget(immutableTarget);
    }

    public static void goToAbove(BlockPos target, int yOffset) {
        BlockPos immutableTarget = target == null ? null : target.immutable();
        RtsClientPathfinding.target = immutableTarget;
        targetYOffset = Math.max(1, yOffset);
        setHighlightedTarget(immutableTarget);
    }

    public static void cancel() {
        target = null;
        targetYOffset = 0;
        clearHighlightedTarget();
    }

    public static boolean isMoving() {
        return target != null;
    }

    public static MoveTargetHighlight getMoveTargetHighlight() {
        if (highlightedTarget == null) {
            return null;
        }
        if (!highlightFading) {
            return new MoveTargetHighlight(highlightedTarget, 1.0F);
        }
        long elapsed = System.currentTimeMillis() - highlightFadeStartedAtMs;
        if (elapsed >= TARGET_HIGHLIGHT_FADE_MS) {
            clearHighlightedTarget();
            return null;
        }
        float alpha = 1.0F - (elapsed / (float) TARGET_HIGHLIGHT_FADE_MS);
        return new MoveTargetHighlight(highlightedTarget, Math.max(0.0F, alpha));
    }

    public static void tickPre() {
        if (target == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ClientRtsController.get().isEnabled()) {
            cancel();
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = targetPosition(minecraft, target, targetYOffset);
        Vec3 toTarget = targetPos.subtract(playerPos);
        Vec3 horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
        double horizontalDistance = horizontal.length();

        if (arrived(playerPos, targetPos, horizontalDistance)) {
            stopPlayer(player);
            finishArrived();
            return;
        }

        faceTarget(player, toTarget, horizontalDistance);
        Vec3 velocity = computeVelocity(player, toTarget, horizontal, horizontalDistance);
        if (velocity.lengthSqr() <= EPSILON) {
            cancel();
            return;
        }

        player.setDeltaMovement(velocity);
        if (player.horizontalCollision && player.onGround() && target.getY() + 1.0D > player.getY() + 0.2D) {
            player.jumpFromGround();
        }
    }

    private static boolean arrived(Vec3 playerPos, Vec3 targetPos, double horizontalDistance) {
        if (targetYOffset > 0) {
            return playerPos.distanceToSqr(targetPos) <= ARRIVAL_3D_SQ;
        }
        return horizontalDistance * horizontalDistance <= ARRIVAL_HORIZONTAL_SQ
                && Math.abs(playerPos.y - targetPos.y) <= 1.25D;
    }

    private static Vec3 computeVelocity(LocalPlayer player, Vec3 toTarget, Vec3 horizontal, double horizontalDistance) {
        boolean threeDimensional = player.getAbilities().flying || player.isFallFlying() || player.isInWater() || player.isInLava()
                || targetYOffset > 0;
        double speed = movementSpeed(player);
        if (threeDimensional) {
            Vec3 dir = toTarget.normalize();
            return dir.scale(speed);
        }

        if (horizontalDistance <= EPSILON) {
            return Vec3.ZERO;
        }
        Vec3 current = player.getDeltaMovement();
        return horizontal.normalize().scale(speed).add(0.0D, current.y, 0.0D);
    }

    private static double movementSpeed(LocalPlayer player) {
        if (player.getAbilities().flying || player.isFallFlying()) {
            return FLY_SPEED;
        }
        if (player.isInWater() || player.isInLava()) {
            return SWIM_SPEED;
        }
        return Math.max(WALK_SPEED, player.getSpeed() * 1.45D);
    }

    private static void faceTarget(LocalPlayer player, Vec3 toTarget, double horizontalDistance) {
        if (horizontalDistance > EPSILON) {
            float yaw = (float) (Math.atan2(toTarget.z, toTarget.x) * (180.0D / Math.PI)) - 90.0F;
            player.setYRot(yaw);
        }
        if (targetYOffset > 0 || player.getAbilities().flying || player.isFallFlying() || player.isInWater() || player.isInLava()) {
            float pitch = (float) (-(Math.atan2(toTarget.y, Math.max(EPSILON, horizontalDistance)) * (180.0D / Math.PI)));
            player.setXRot(pitch);
        }
    }

    private static Vec3 targetPosition(Minecraft minecraft, BlockPos pos, int yOffset) {
        double y = yOffset > 0 ? pos.getY() + yOffset : blockSurfaceY(minecraft, pos);
        return new Vec3(pos.getX() + 0.5D, y, pos.getZ() + 0.5D);
    }

    private static double blockSurfaceY(Minecraft minecraft, BlockPos pos) {
        BlockState state = minecraft.level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(minecraft.level, pos);
        if (!shape.isEmpty()) {
            return pos.getY() + shape.max(Direction.Axis.Y);
        }

        BlockPos below = pos.below();
        BlockState belowState = minecraft.level.getBlockState(below);
        VoxelShape belowShape = belowState.getCollisionShape(minecraft.level, below);
        if (!belowShape.isEmpty() || !belowState.is(Blocks.AIR)) {
            return below.getY() + belowShape.max(Direction.Axis.Y);
        }
        return pos.getY() + 0.5D;
    }

    private static void stopPlayer(LocalPlayer player) {
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, current.y, 0.0D);
    }

    private static void finishArrived() {
        target = null;
        targetYOffset = 0;
        beginHighlightFade();
    }

    private static void setHighlightedTarget(BlockPos pos) {
        highlightedTarget = pos == null ? null : pos.immutable();
        highlightFadeStartedAtMs = 0L;
        highlightFading = false;
    }

    private static void beginHighlightFade() {
        if (highlightedTarget == null) {
            return;
        }
        highlightFadeStartedAtMs = System.currentTimeMillis();
        highlightFading = true;
    }

    private static void clearHighlightedTarget() {
        highlightedTarget = null;
        highlightFadeStartedAtMs = 0L;
        highlightFading = false;
    }

    public record MoveTargetHighlight(BlockPos target, float alpha) {
    }
}
