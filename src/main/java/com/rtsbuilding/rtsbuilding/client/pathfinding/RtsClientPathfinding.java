package com.rtsbuilding.rtsbuilding.client.pathfinding;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side auto-pathfinding — moves the local player toward a target block
 * by setting velocity each tick via {@link LocalPlayer#setDeltaMovement}.
 * <p>
 * Uses {@link RtsMovementModeRegistry} to select the appropriate
 * {@link MovementModeHandler} for the player's current pose/state,
 * which handles speed calculation, velocity type (2D/3D), sprinting rules,
 * and stuck behaviour per movement mode.
 * <p>
 * Runs in {@link ClientTickEvent} before {@code aiStep()}, so the client's
 * own physics engine processes the velocity. Walking animation, collision
 * detection and position sync ({@code ServerboundMovePlayerPacket}) happen
 * automatically.
 * <p>
 * Other mods can register custom movement modes via
 * {@link RtsMovementModeRegistry#register(MovementModeHandler, int)}
 * or by listening to {@link RtsMovementModeRegistry.RegisterMovementModeEvent}.
 */
public final class RtsClientPathfinding {

    private static BlockPos target = null;
    private static MovementModeHandler previousMode = null;
    private static BlockPos highlightedTarget = null;
    private static long highlightFadeStartedAtMs = 0L;
    private static boolean highlightFading = false;
    /**
     * 当 &gt; 0 时，目标点 Y 轴偏移量（单位：格）。
     * 用于「飞到目标上方」模式（Ctrl + 双击右键），
     * 到达判定也要求 3D 接近（不含 horizontal-only 检查）。
     */
    private static int targetYOffset = 0;

    /** 到达判定：水平距离平方阈值。 */
    private static final double REACH_DISTANCE_SQ = 0.1 * 0.1;
    /** 向量零长度判断阈值，避免除零。 */
    private static final double EPSILON = 0.01;
    private static final long TARGET_HIGHLIGHT_FADE_MS = 350L;

    private RtsClientPathfinding() {}

    /**
     * Applies {@code entityInside()} slowing effects directly to the velocity vector for
     * blocks the player's AABB overlaps. Mirrors vanilla behaviour where each block's
     * {@code entityInside()} calls {@code setDeltaMovement(delta.multiply(...))} directly.
     */
    private static Vec3 applyEntityInsideSlow(LocalPlayer player, Vec3 velocity) {
        BlockPos min = BlockPos.containing(player.getBoundingBox().minX, player.getBoundingBox().minY, player.getBoundingBox().minZ);
        BlockPos max = BlockPos.containing(player.getBoundingBox().maxX, player.getBoundingBox().maxY, player.getBoundingBox().maxZ);
        Vec3 result = velocity;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(Blocks.SOUL_SAND)) {
                result = result.multiply(0.4, 1.0, 0.4);
            } else if (state.is(Blocks.HONEY_BLOCK)) {
                result = result.multiply(0.5, 1.0, 0.5);
            } else if (state.is(Blocks.COBWEB)) {
                result = result.multiply(0.25, 0.05, 0.25);
            }
        }
        return result;
    }

    /**
     * Starts moving the local player toward {@code target}.
     * Sends a packet to the server for server-side tracking/cleanup.
     */
    public static void goTo(BlockPos target) {
        RtsClientPathfinding.target = target.immutable();
        targetYOffset = 0;
        setHighlightedTarget(RtsClientPathfinding.target);
        RtsClientPacketGateway.sendPathfindingGoTo(target);
    }

    /**
     * Starts moving the local player to <strong>land on top</strong> of the
     * target block, with a 3D arrival check (both XZ and Y proximity).
     * <p>
     * Unlike {@link #goTo(BlockPos)} which uses horizontal-only arrival for
     * flying modes, this forces the player to reach the block's surface
     * position ({@code yOffset = 1} block above).
     * <p>
     * Intended for Ctrl + double right-click while flying — precision landing.
     *
     * @param target  the block to land on
     * @param yOffset vertical offset above the block (pass 1 to land on surface)
     */
    public static void goToAbove(BlockPos target, int yOffset) {
        RtsClientPathfinding.target = target.immutable();
        targetYOffset = Math.max(1, yOffset);
        setHighlightedTarget(RtsClientPathfinding.target);
        RtsClientPacketGateway.sendPathfindingGoTo(target);
    }

    /**
     * Cancels any active movement and cleans up the previous mode.
     */
    public static void cancel() {
        stopMovement();
        clearHighlightedTarget();
    }

    private static void stopMovement() {
        target = null;
        targetYOffset = 0;
        if (previousMode != null && Minecraft.getInstance().player instanceof LocalPlayer lp) {
            previousMode.onDeactivate(lp);
        }
        previousMode = null;
    }

    private static void finishArrived() {
        stopMovement();
        beginHighlightFade();
    }

    /**
     * Returns {@code true} if movement is currently active.
     */
    public static boolean isMoving() {
        return target != null;
    }

    @Nullable
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

   /**
     * Called from {@link ClientTickEvent.Pre}
     * — before {@code aiStep()}. Sets the player's velocity toward the target
     * and faces the player in the correct direction.
     */
    public static void tickPre() {
        if (target == null) return;

        // Ensure the registry is initialised on first tick
        RtsMovementModeRegistry.init();

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ClientRtsController.get().isEnabled()) {
            cancel();
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = computeTargetPos();
        Vec3 toTarget = targetPos.subtract(playerPos);
        Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z);
        double horizontalDist = horizontal.length();

        // ── Face the target (yaw) ──
        faceTarget(player, toTarget);

        // ── Resolve movement mode ──
        MovementParams params = resolveMode(player);
        if (params == null) {
            cancel();
            return;
        }

        // ── Arrival check ──
        if (isArrived(player, playerPos, targetPos, params)) {
            finishArrived();
            return;
        }

        // ── Pitch ──
        applyPitch(player, toTarget, horizontalDist, params);

        // ── Sprint ──
        applySprint(player, params);

        // ── Velocity ──
        applyVelocity(player, toTarget, horizontal, horizontalDist, targetPos, playerPos, params);

        // ── Stuck / collision ──
        if (player.horizontalCollision
                && target.getY() + 1.0 > player.position().y + 0.2) {
            handleStuck(player, params);
        }
    }

    // ==================================================================
    //  Helper methods
    // ==================================================================

    /**
     * Computes the 3D target position from the stored {@link #target} block,
     * using {@link #targetYOffset} to decide the Y level.
     * <p>
     * For normal mode ({@code targetYOffset == 0}): uses the actual top surface
     * of the target block's collision shape. This correctly handles slabs
     * (surface at Y+0.5), carpets (Y+0.0625), stairs, and non-collision blocks
     * (air, torches — falls through to the block below's surface).
     * <p>
     * For precision landing ({@code targetYOffset > 0}): uses the fixed offset
     * above the block (e.g. Y+1 for landing on top).
     */
    private static Vec3 computeTargetPos() {
        double y;
        if (targetYOffset > 0) {
            y = target.getY() + targetYOffset;
        } else {
            y = getBlockSurfaceY(target);
        }
        return new Vec3(target.getX() + 0.5, y, target.getZ() + 0.5);
    }

    /**
     * Returns the Y coordinate of the top surface of the block at {@code pos},
     * computed from the block's actual collision shape.
     * <ul>
     *   <li>Full block → Y+1.0</li>
     *   <li>Bottom slab → Y+0.5</li>
     *   <li>Carpet → Y+0.0625</li>
     *   <li>No collision (air, torches, etc.) → surface of the block below</li>
     *   <li>Two blocks of nothing → {@code pos.getY() + 0.5} (center of the block)</li>
     * </ul>
     */
    private static double getBlockSurfaceY(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return pos.getY() + 1.0;

        BlockState state = mc.level.getBlockState(pos);
        VoxelShape collisionShape = state.getCollisionShape(mc.level, pos);

        if (!collisionShape.isEmpty()) {
            // Block has collision — use the actual top surface
            return pos.getY() + collisionShape.max(Direction.Axis.Y);
        }

        // No collision (air, torches, signs, etc.) — check the block below
        BlockPos below = pos.below();
        BlockState belowState = mc.level.getBlockState(below);
        VoxelShape belowShape = belowState.getCollisionShape(mc.level, below);

        if (!belowShape.isEmpty()) {
            return below.getY() + belowShape.max(Direction.Axis.Y);
        }

        // Two blocks of nothing — target the center of the target block
        return pos.getY() + 0.5;
    }

    /**
     * Sets the player's yaw to face the target direction.
     */
    private static void faceTarget(LocalPlayer player, Vec3 toTarget) {
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    /**
     * Resolves the current movement mode from the registry, tracks
     * mode transitions, and returns the movement params.
     *
     * @return params, or {@code null} if no mode found
     */
    @Nullable
    private static MovementParams resolveMode(LocalPlayer player) {
        MovementModeHandler currentMode = RtsMovementModeRegistry.findActive(player);
        if (currentMode == null) return null;

        // Handle mode transitions (activate / deactivate lifecycle)
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

    /**
     * Checks whether the player has arrived at the target position.
     * The vertical check depends on the mode and whether precision-landing is active.
     * <p>
     * For precision landing ({@code targetYOffset > 0}):
     * when the player is close enough in both XZ and Y, the flight abilities
     * are disabled so the player falls naturally onto the block surface via
     * gravity. Minecraft's native collision handling then works for any block
     * shape (slabs, stairs, carpets, etc.). The pathfinding is cancelled and
     * walking mode takes over for genuine touchdown.
     */
    private static boolean isArrived(LocalPlayer player, Vec3 playerPos, Vec3 targetPos, MovementParams params) {
        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        double horizDistSq = dx * dx + dz * dz;

        if (targetYOffset > 0) {
            // Precision landing (Ctrl+双击): require both horizontal AND vertical proximity,
            // then disable creative flight so the player lands on whatever collision shape
            // the block provides.
            if (horizDistSq < 0.25) { // within 0.5 blocks horizontally
                double dy = playerPos.y - targetPos.y;
                if (Math.abs(dy) < 0.5) {
                    // Close enough — initiate genuine landing:
                    // disable creative flight so gravity pulls the player down
                    // onto the block surface (handles slabs/stairs/carpets natively).
                    if (player.getAbilities().flying && !player.isFallFlying()) {
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                    }
                    return true;
                }
            }
            return false;
        }

        // Normal mode: per-mode Y check
        if (horizDistSq >= REACH_DISTANCE_SQ) return false;
        return params.arrivalCheckHorizontalOnly() || playerPos.y >= targetPos.y;
    }

    /**
     * Sets the player's pitch based on movement mode.
     * <ul>
     *   <li>Input-system modes (elytra): pitch toward target only for precision landing;
     *       fly-over keeps current pitch.</li>
     *   <li>Velocity-driven modes: pitch flat (velocity vector handles vertical).</li>
     * </ul>
     */
    private static void applyPitch(LocalPlayer player, Vec3 toTarget, double horizontalDist, MovementParams params) {
        if (params.useInputSystem()) {
            if (targetYOffset > 0) {
                float pitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDist + EPSILON));
                player.setXRot(pitch);
            }
            // Fly-over: keep current pitch
        } else {
            player.setXRot(0);
        }
    }

    /**
     * Applies sprinting rules per the mode's {@link MovementParams#allowSprint()}.
     */
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

    /**
     * Applies velocity toward the target using either the input system
     * (elytra) or direct {@code setDeltaMovement} (other modes).
     */
    private static void applyVelocity(LocalPlayer player, Vec3 toTarget, Vec3 horizontal,
                                       double horizontalDist, Vec3 targetPos, Vec3 playerPos,
                                       MovementParams params) {
        if (params.useInputSystem()) {
            // Elytra: forwardImpulse = +1 means "press W", activates forward thrust.
            // The adjusted pitch above naturally steers the player toward the target.
            player.input.keyPresses = new net.minecraft.world.entity.player.Input(
                    true, false, false, false, false, false, false);
            player.hurtMarked = true;
            return;
        }

        if (horizontalDist <= EPSILON) return;

        double speed = params.speed();
        // Scale down when close to avoid overshooting
        if (params.applyApproachSlowdown() && horizontalDist < 0.5) {
            speed *= horizontalDist / 0.5;
        }

        if (params.threeDimensional()) {
            // 3D velocity: swim directly toward the target
            double dist3D = toTarget.length();
            if (dist3D > EPSILON) {
                player.setDeltaMovement(toTarget.scale(speed / dist3D));
            }
        } else {
            // 2D velocity: horizontal only
            Vec3 velocity = horizontal.scale(speed / horizontalDist);

            if (targetYOffset > 0) {
                // Precision landing: gentle vertical guidance
                double dy = targetPos.y - playerPos.y;
                double vertSpeed = Math.min(Math.abs(dy) * 0.15, 0.4) * Math.signum(dy);
                velocity = new Vec3(velocity.x, vertSpeed, velocity.z);
            } else {
                velocity = new Vec3(velocity.x, player.getDeltaMovement().y, velocity.z);
            }

            if (params.applyEntityInsideSlow()) {
                velocity = applyEntityInsideSlow(player, velocity);
            }
            player.setDeltaMovement(velocity);
        }

        player.hurtMarked = true;
    }

    /**
     * Handles being stuck against an obstacle based on the mode's configured
     * {@link MovementParams.StuckBehavior}.
     */
    private static void handleStuck(LocalPlayer player, MovementParams params) {
        MovementParams.StuckBehavior behavior = params.stuckBehavior();
        if (behavior == null || behavior == MovementParams.StuckBehavior.NONE) return;

        switch (behavior) {
            case JUMP -> {
                if (player.onGround()) {
                    player.jumpFromGround();
                    player.hurtMarked = true;
                }
            }
            case FLOAT_UP -> {
                // LivingEntity.travel() adds +0.04 to deltaMovement.y every tick in water
                // (natural buoyancy). We replicate that here so the player gently rises
                // when blocked, matching vanilla liquid behaviour.
                // CRITICAL: Zero the horizontal velocity — otherwise the swimming branch
                // pushes the player into the shore wall every tick, preventing them from
                // floating up and climbing out.
                double floatSpeed = player.isInWater() ? 0.04 : 0.02;
                player.setDeltaMovement(0, floatSpeed, 0);
                player.hurtMarked = true;
            }
            case FLY_UP -> {
                // Gentle upward boost to clear obstacles during flight
                player.setDeltaMovement(player.getDeltaMovement().x, 0.1, player.getDeltaMovement().z);
                player.hurtMarked = true;
            }
        }
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
