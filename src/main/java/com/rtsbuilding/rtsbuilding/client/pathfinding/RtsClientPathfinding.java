package com.rtsbuilding.rtsbuilding.client.pathfinding;

import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class RtsClientPathfinding {

    private static final RtsClientPathfinding INSTANCE = new RtsClientPathfinding();

    @Nullable private BlockPos target;
    @Nullable private MovementModeHandler previousMode;
    @Nullable private BlockPos highlightedTarget;
    private long highlightFadeStartedAtMs;
    private boolean highlightFading;
    private int targetYOffset;

    private static final double REACH_DISTANCE_SQ = 0.1 * 0.1;
    private static final double EPSILON = 0.01;
    private static final long TARGET_HIGHLIGHT_FADE_MS = 350L;

    private RtsClientPathfinding() {}

    
    
    

    public static void goTo(BlockPos target) { INSTANCE.goToInternal(target); }
    public static void goToAbove(BlockPos target, int yOffset) { INSTANCE.goToAboveInternal(target, yOffset); }
    public static void cancel() { INSTANCE.cancelInternal(); }
    public static boolean isMoving() { return INSTANCE.target != null; }
    @Nullable public static MoveTargetHighlight getMoveTargetHighlight() { return INSTANCE.getMoveTargetHighlightInternal(); }
    public static void tickPre() { INSTANCE.tickPreInternal(); }

    
    
    

    private void goToInternal(BlockPos target) {
        this.target = target.immutable();
        targetYOffset = 0;
        setHighlightedTarget(this.target);
        RtsClientPacketGateway.sendPathfindingGoTo(target);
    }

    private void goToAboveInternal(BlockPos target, int yOffset) {
        this.target = target.immutable();
        targetYOffset = Math.max(1, yOffset);
        setHighlightedTarget(this.target);
        RtsClientPacketGateway.sendPathfindingGoTo(target);
    }

    private void cancelInternal() {
        stopMovement();
        clearHighlightedTarget();
    }

    @Nullable
    private MoveTargetHighlight getMoveTargetHighlightInternal() {
        if (highlightedTarget == null) return null;
        if (!highlightFading) return new MoveTargetHighlight(highlightedTarget, 1.0F);
        long elapsed = System.currentTimeMillis() - highlightFadeStartedAtMs;
        if (elapsed >= TARGET_HIGHLIGHT_FADE_MS) { clearHighlightedTarget(); return null; }
        float alpha = 1.0F - (elapsed / (float) TARGET_HIGHLIGHT_FADE_MS);
        return new MoveTargetHighlight(highlightedTarget, Math.max(0.0F, alpha));
    }

    private void tickPreInternal() {
        if (target == null) return;
        RtsMovementModeRegistry.init();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) { cancelInternal(); return; }

        CameraModule cam = CompositionRoot.get().module(CameraModule.class);
        if (cam == null || !cam.getState().isEnabled()) { cancelInternal(); return; }

        Vec3 playerPos = player.position();
        Vec3 targetPos = computeTargetPos();
        Vec3 toTarget = targetPos.subtract(playerPos);
        Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z);
        double horizontalDist = horizontal.length();

        faceTarget(player, toTarget);

        MovementParams params = resolveMode(player);
        if (params == null) { cancelInternal(); return; }

        if (isArrived(player, playerPos, targetPos, params)) { finishArrived(); return; }

        applyPitch(player, toTarget, horizontalDist, params);
        applySprint(player, params);
        applyVelocity(player, toTarget, horizontal, horizontalDist, targetPos, playerPos, params);

        if (player.horizontalCollision && target.getY() + 1.0 > player.position().y + 0.2) {
            handleStuck(player, params);
        }
    }

    
    
    

    private void stopMovement() {
        target = null;
        targetYOffset = 0;
        if (previousMode != null && Minecraft.getInstance().player instanceof LocalPlayer lp) {
            previousMode.onDeactivate(lp);
        }
        previousMode = null;
    }

    private void finishArrived() { stopMovement(); beginHighlightFade(); }

    private Vec3 computeTargetPos() {
        double y = targetYOffset > 0 ? target.getY() + targetYOffset : getBlockSurfaceY(target);
        return new Vec3(target.getX() + 0.5, y, target.getZ() + 0.5);
    }

    private double getBlockSurfaceY(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return pos.getY() + 1.0;
        BlockState state = mc.level.getBlockState(pos);
        VoxelShape collisionShape = state.getCollisionShape(mc.level, pos);
        if (!collisionShape.isEmpty()) return pos.getY() + collisionShape.max(Direction.Axis.Y);
        BlockPos below = pos.below();
        BlockState belowState = mc.level.getBlockState(below);
        VoxelShape belowShape = belowState.getCollisionShape(mc.level, below);
        if (!belowShape.isEmpty()) return below.getY() + belowShape.max(Direction.Axis.Y);
        return pos.getY() + 0.5;
    }

    private static void faceTarget(LocalPlayer player, Vec3 toTarget) {
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    private void applyPitch(LocalPlayer player, Vec3 toTarget, double horizontalDist, MovementParams params) {
        if (params.useInputSystem()) {
            if (targetYOffset > 0) {
                float pitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDist + EPSILON));
                player.setXRot(pitch);
            }
        } else {
            player.setXRot(0);
        }
    }

    @Nullable
    private MovementParams resolveMode(LocalPlayer player) {
        MovementModeHandler currentMode = RtsMovementModeRegistry.findActive(player);
        if (currentMode == null) return null;
        if (currentMode != previousMode) {
            if (previousMode != null) previousMode.onDeactivate(player);
            currentMode.onActivate(player);
            previousMode = currentMode;
        }
        Vec3 toTarget = new Vec3(
                target.getX() + 0.5 - player.position().x,
                target.getY() + (targetYOffset > 0 ? targetYOffset : 1.0) - player.position().y,
                target.getZ() + 0.5 - player.position().z);
        double horizontalDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        return currentMode.computeParams(player, toTarget, horizontalDist);
    }

    private static void applySprint(LocalPlayer player, MovementParams params) {
        if (params.allowSprint()) {
            boolean canSprint = !player.getAbilities().flying
                    && player.getFoodData().getFoodLevel() > 6
                    && !player.isUsingItem()
                    && (player.onGround() || player.isInWater() || player.isInLava());
            player.setSprinting(canSprint);
        } else {
            player.setSprinting(false);
        }
    }

    private void applyVelocity(LocalPlayer player, Vec3 toTarget, Vec3 horizontal,
                                double horizontalDist, Vec3 targetPos, Vec3 playerPos,
                                MovementParams params) {
        if (params.useInputSystem()) {
            player.input.forwardImpulse = 1.0F;
            player.hurtMarked = true;
            return;
        }
        if (horizontalDist <= EPSILON) return;
        double speed = params.speed();
        if (params.applyApproachSlowdown() && horizontalDist < 0.5) speed *= horizontalDist / 0.5;

        if (params.threeDimensional()) {
            double dist3D = toTarget.length();
            if (dist3D > EPSILON) player.setDeltaMovement(toTarget.scale(speed / dist3D));
        } else {
            Vec3 velocity = horizontal.scale(speed / horizontalDist);
            if (targetYOffset > 0) {
                double dy = targetPos.y - playerPos.y;
                double vertSpeed = Math.min(Math.abs(dy) * 0.15, 0.4) * Math.signum(dy);
                velocity = new Vec3(velocity.x, vertSpeed, velocity.z);
            } else {
                velocity = new Vec3(velocity.x, player.getDeltaMovement().y, velocity.z);
            }
            if (params.applyEntityInsideSlow()) velocity = applyEntityInsideSlow(player, velocity);
            player.setDeltaMovement(velocity);
        }
        player.hurtMarked = true;
    }

    private static Vec3 applyEntityInsideSlow(LocalPlayer player, Vec3 velocity) {
        BlockPos min = BlockPos.containing(player.getBoundingBox().minX, player.getBoundingBox().minY, player.getBoundingBox().minZ);
        BlockPos max = BlockPos.containing(player.getBoundingBox().maxX, player.getBoundingBox().maxY, player.getBoundingBox().maxZ);
        Vec3 result = velocity;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(Blocks.SOUL_SAND)) result = result.multiply(0.4, 1.0, 0.4);
            else if (state.is(Blocks.HONEY_BLOCK)) result = result.multiply(0.5, 1.0, 0.5);
            else if (state.is(Blocks.COBWEB)) result = result.multiply(0.25, 0.05, 0.25);
        }
        return result;
    }

    private boolean isArrived(LocalPlayer player, Vec3 playerPos, Vec3 targetPos, MovementParams params) {
        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        double horizDistSq = dx * dx + dz * dz;

        if (targetYOffset > 0) {
            if (horizDistSq < 0.25) {
                double dy = playerPos.y - targetPos.y;
                if (Math.abs(dy) < 0.5) {
                    if (player.getAbilities().flying && !player.isFallFlying()) {
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                    }
                    return true;
                }
            }
            return false;
        }
        if (horizDistSq >= REACH_DISTANCE_SQ) return false;
        return params.arrivalCheckHorizontalOnly() || playerPos.y >= targetPos.y;
    }

    private void handleStuck(LocalPlayer player, MovementParams params) {
        MovementParams.StuckBehavior behavior = params.stuckBehavior();
        if (behavior == null || behavior == MovementParams.StuckBehavior.NONE) return;
        switch (behavior) {
            case JUMP -> { if (player.onGround()) { player.jumpFromGround(); player.hurtMarked = true; } }
            case FLOAT_UP -> {
                double floatSpeed = player.isInWater() ? 0.04 : 0.02;
                player.setDeltaMovement(0, floatSpeed, 0);
                player.hurtMarked = true;
            }
            case FLY_UP -> {
                player.setDeltaMovement(player.getDeltaMovement().x, 0.1, player.getDeltaMovement().z);
                player.hurtMarked = true;
            }
        }
    }

    private void setHighlightedTarget(BlockPos pos) {
        highlightedTarget = pos == null ? null : pos.immutable();
        highlightFadeStartedAtMs = 0L;
        highlightFading = false;
    }

    private void beginHighlightFade() {
        if (highlightedTarget == null) return;
        highlightFadeStartedAtMs = System.currentTimeMillis();
        highlightFading = true;
    }

    private void clearHighlightedTarget() {
        highlightedTarget = null;
        highlightFadeStartedAtMs = 0L;
        highlightFading = false;
    }

    public record MoveTargetHighlight(BlockPos target, float alpha) {}
}
