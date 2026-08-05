package com.rtsbuilding.rtsbuilding.client.pathfinding;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import net.minecraftforge.common.ForgeHooks;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side auto-pathfinding — moves the local player toward a target block
 * by setting velocity each tick before the local player update.
 * <p>
 * Uses {@link RtsMovementModeRegistry} to select the appropriate
 * {@link MovementModeHandler} for the player's current pose/state,
 * which handles speed calculation, velocity type (2D/3D), sprinting rules,
 * and stuck behaviour per movement mode.
 * <p>
 * Runs in {@link TickEvent.ClientTickEvent} START before the local player update, so the client's
 * own physics engine processes the velocity. Walking animation, collision
 * detection and position sync ({@code ServerboundMovePlayerPacket}) happen
 * automatically.
 * <p>
 * Other mods can register custom movement modes via
 * {@link RtsMovementModeRegistry#register(MovementModeHandler, int)}
 * or by listening to {@link RtsMovementModeRegistry.RegisterMovementModeEvent}.
 */
@SideOnly(Side.CLIENT)
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

    /** 对玩家碰撞箱覆盖到的方块应用 1.12 方块碰撞减速效果。 */
    private static Vec3d applyEntityInsideSlow(EntityPlayerSP player, Vec3d velocity) {
        AxisAlignedBB box = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(player.boundingBox);
        BlockPos min = new BlockPos(box.minX, box.minY, box.minZ);
        BlockPos max = new BlockPos(box.maxX, box.maxY, box.maxZ);
        Vec3d result = velocity;
        for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(min, max)) {
            BlockState state = BlockState.fromWorld(player.worldObj, pos);
            if (state.getBlock() == Blocks.soul_sand) {
                result = new Vec3d(result.x * 0.4D, result.y, result.z * 0.4D);
            } else if (state.getBlock() == Blocks.web) {
                result = new Vec3d(result.x * 0.25D, result.y * 0.05D, result.z * 0.25D);
            }
        }
        return result;
    }

    /**
     * Starts moving the local player toward {@code target}.
     * Sends a packet to the server for server-side tracking/cleanup.
     */
    public static void goTo(BlockPos target) {
        RtsClientPathfinding.target = target.toImmutable();
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
        RtsClientPathfinding.target = target.toImmutable();
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
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (previousMode != null && player != null) {
            previousMode.onDeactivate(player);
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
     * Called from {@link TickEvent.ClientTickEvent} START
     * — before {@code aiStep()}. Sets the player's velocity toward the target
     * and faces the player in the correct direction.
     */
    public static void tickPre() {
        if (target == null) return;

        // Ensure the registry is initialised on first tick
        RtsMovementModeRegistry.init();

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !ClientRtsController.get().isEnabled()) {
            cancel();
            return;
        }

        Vec3d playerPos = new Vec3d(player.posX, player.posY, player.posZ);
        Vec3d targetPos = computeTargetPos();
        Vec3d toTarget = targetPos.subtract(playerPos);
        Vec3d horizontal = new Vec3d(toTarget.x, 0.0D, toTarget.z);
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
        if (player.isCollidedHorizontally
                && target.getY() + 1.0D > player.posY + 0.2D) {
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
    private static Vec3d computeTargetPos() {
        double y;
        if (targetYOffset > 0) {
            y = target.getY() + targetYOffset;
        } else {
            y = getBlockSurfaceY(target);
        }
        return new Vec3d(target.getX() + 0.5D, y, target.getZ() + 0.5D);
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return pos.getY() + 1.0D;

        BlockState state = BlockState.fromWorld(mc.theWorld, pos);
        AxisAlignedBB collision = state.getCollisionBoundingBox(mc.theWorld, pos);

        if (collision != null) {
            return pos.getY() + collision.maxY;
        }

        // No collision (air, torches, signs, etc.) — check the block below
        BlockPos below = pos.down();
        BlockState belowState = BlockState.fromWorld(mc.theWorld, below);
        AxisAlignedBB belowCollision = belowState.getCollisionBoundingBox(mc.theWorld, below);

        if (belowCollision != null) {
            return below.getY() + belowCollision.maxY;
        }

        // Two blocks of nothing — target the center of the target block
        return pos.getY() + 0.5;
    }

    /**
     * Sets the player's yaw to face the target direction.
     */
    private static void faceTarget(EntityPlayerSP player, Vec3d toTarget) {
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        player.rotationYaw = yaw;
        player.setRotationYawHead(yaw);
        player.renderYawOffset = yaw;
        player.prevRenderYawOffset = yaw;
    }

    /**
     * Resolves the current movement mode from the registry, tracks
     * mode transitions, and returns the movement params.
     *
     * @return params, or {@code null} if no mode found
     */
    private static MovementParams resolveMode(EntityPlayerSP player) {
        MovementModeHandler currentMode = RtsMovementModeRegistry.findActive(player);
        if (currentMode == null) return null;

        // Handle mode transitions (activate / deactivate lifecycle)
        if (currentMode != previousMode) {
            if (previousMode != null) previousMode.onDeactivate(player);
            currentMode.onActivate(player);
            previousMode = currentMode;
        }

        Vec3d toTarget = new Vec3d(
                target.getX() + 0.5D - player.posX,
                target.getY() + (targetYOffset > 0 ? targetYOffset : 1.0D) - player.posY,
                target.getZ() + 0.5D - player.posZ);
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
    private static boolean isArrived(EntityPlayerSP player, Vec3d playerPos,
                                     Vec3d targetPos, MovementParams params) {
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
                    if (player.capabilities.isFlying) {
                        player.capabilities.isFlying = false;
                        player.sendPlayerAbilities();
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
    private static void applyPitch(EntityPlayerSP player, Vec3d toTarget,
                                   double horizontalDist, MovementParams params) {
        if (params.useInputSystem()) {
            if (targetYOffset > 0) {
                float pitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDist + EPSILON));
                player.rotationPitch = pitch;
            }
            // Fly-over: keep current pitch
        } else {
            player.rotationPitch = 0.0F;
        }
    }

    /**
     * Applies sprinting rules per the mode's {@link MovementParams#allowSprint()}.
     */
    private static void applySprint(EntityPlayerSP player, MovementParams params) {
        if (params.allowSprint()) {
            boolean canSprint = !player.capabilities.isFlying
                    && player.getFoodStats().getFoodLevel() > 6
                    && !player.isUsingItem()
                    && (player.onGround || player.isInWater() || player.handleLavaMovement());
            player.setSprinting(canSprint);
        } else {
            player.setSprinting(false);
        }
    }

    /**
     * Applies velocity toward the target using either the input system
     * (elytra) or direct 1.12 motion fields (other modes).
     */
    private static void applyVelocity(EntityPlayerSP player, Vec3d toTarget, Vec3d horizontal,
                                       double horizontalDist, Vec3d targetPos, Vec3d playerPos,
                                       MovementParams params) {
        if (params.useInputSystem()) {
            // Elytra: forwardImpulse = +1 means "press W", activates forward thrust.
            // The adjusted pitch above naturally steers the player toward the target.
            player.movementInput.moveForward = 1.0F;
            player.velocityChanged = true;
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
                Vec3d velocity = toTarget.scale(speed / dist3D);
                setVelocity(player, velocity);
            }
        } else {
            // 2D velocity: horizontal only
            Vec3d velocity = horizontal.scale(speed / horizontalDist);

            if (targetYOffset > 0) {
                // Precision landing: gentle vertical guidance
                double dy = targetPos.y - playerPos.y;
                double vertSpeed = Math.min(Math.abs(dy) * 0.15, 0.4) * Math.signum(dy);
                velocity = new Vec3d(velocity.x, vertSpeed, velocity.z);
            } else {
                velocity = new Vec3d(velocity.x, player.motionY, velocity.z);
            }

            if (params.applyEntityInsideSlow()) {
                velocity = applyEntityInsideSlow(player, velocity);
            }
            setVelocity(player, velocity);
        }

        player.velocityChanged = true;
    }

    /**
     * Handles being stuck against an obstacle based on the mode's configured
     * {@link MovementParams.StuckBehavior}.
     */
    private static void handleStuck(EntityPlayerSP player, MovementParams params) {
        MovementParams.StuckBehavior behavior = params.stuckBehavior();
        if (behavior == null || behavior == MovementParams.StuckBehavior.NONE) return;

        switch (behavior) {
            case JUMP:
                if (player.onGround) {
                    double jumpSpeed = 0.42D;
                    PotionEffect jumpBoost = player.getActivePotionEffect(Potion.jump);
                    if (jumpBoost != null) {
                        jumpSpeed += 0.1D * (jumpBoost.getAmplifier() + 1);
                    }
                    player.motionY = jumpSpeed;
                    player.isAirBorne = true;
                    player.velocityChanged = true;
                    ForgeHooks.onLivingJump(player);
                }
                break;
            case FLOAT_UP:
                // LivingEntity.travel() adds +0.04 to deltaMovement.y every tick in water
                // (natural buoyancy). We replicate that here so the player gently rises
                // when blocked, matching vanilla liquid behaviour.
                // CRITICAL: Zero the horizontal velocity — otherwise the swimming branch
                // pushes the player into the shore wall every tick, preventing them from
                // floating up and climbing out.
                double floatSpeed = player.isInWater() ? 0.04 : 0.02;
                player.motionX = 0.0D;
                player.motionY = floatSpeed;
                player.motionZ = 0.0D;
                player.velocityChanged = true;
                break;
            case FLY_UP:
                // Gentle upward boost to clear obstacles during flight
                player.motionY = 0.1D;
                player.velocityChanged = true;
                break;
            default:
                break;
        }
    }

    private static void setVelocity(EntityPlayerSP player, Vec3d velocity) {
        player.motionX = velocity.x;
        player.motionY = velocity.y;
        player.motionZ = velocity.z;
    }

    private static void setHighlightedTarget(BlockPos pos) {
        highlightedTarget = pos == null ? null : pos.toImmutable();
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

    /** Java 8 下替代 record 的不可变渲染快照。 */
    public static final class MoveTargetHighlight {
        private final BlockPos target;
        private final float alpha;

        public MoveTargetHighlight(BlockPos target, float alpha) {
            this.target = target.toImmutable();
            this.alpha = alpha;
        }

        public BlockPos target() { return target; }
        public float alpha() { return alpha; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MoveTargetHighlight)) return false;
            MoveTargetHighlight that = (MoveTargetHighlight) other;
            return Float.compare(alpha, that.alpha) == 0 && target.equals(that.target);
        }

        @Override
        public int hashCode() {
            return 31 * target.hashCode() + Float.floatToIntBits(alpha);
        }

        @Override
        public String toString() {
            return "MoveTargetHighlight[target=" + target + ", alpha=" + alpha + ']';
        }
    }
}
