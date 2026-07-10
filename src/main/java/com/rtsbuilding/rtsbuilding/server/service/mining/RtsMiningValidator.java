package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Central repository for all validation predicates used by the mining
 * system. Every method is stateless and idempotent.
 *
 * <p>This class owns constants that define mining limits:
 * <ul>
 *   <li>{@link #ULTIMINE_MAX_BLOCKS} ??hard cap on BFS ultimine collection</li>
 *   <li>{@link #AREA_MINE_MAX_SIZE} ??max extent per dimension for area mine</li>
 *   <li>{@link #AREA_DESTROY_MAX_TARGETS} ??max explicit positions accepted</li>
 *   <li>{@link #ULTIMINE_BLOCKS_PER_TICK} ??batch throttle</li>
 * </ul>
 */
public final class RtsMiningValidator {

    // =========================================================================
    //  Constants
    // =========================================================================

    /** Maximum number of blocks an ultimine batch may collect. */
    public static final int ULTIMINE_MAX_BLOCKS = 256;

    /** Max blocks per dimension for area mine (X, Y, Z). */
    public static final int AREA_MINE_MAX_SIZE = 36;

    /** Maximum explicit shape-destroy targets accepted from Quick Build. */
    public static final int AREA_DESTROY_MAX_TARGETS = 98304;

    /** How many ultimine targets are processed in a single tick. */
    public static final int ULTIMINE_BLOCKS_PER_TICK = 8;

    /** Number of hotbar slots a player has (0-8). */
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;

    private RtsMiningValidator() {
    }

    public static int ultimineMaxBlocks() {
        return configIntOrDefault(Config::ultimineMaxBlocks, ULTIMINE_MAX_BLOCKS);
    }

    public static int areaMineMaxSize() {
        return configIntOrDefault(Config::areaMineMaxSize, AREA_MINE_MAX_SIZE);
    }

    public static int areaMineMaxVolume() {
        return configIntOrDefault(
                Config::areaMineMaxVolume,
                AREA_MINE_MAX_SIZE * AREA_MINE_MAX_SIZE * AREA_MINE_MAX_SIZE);
    }

    public static int areaMineMaxWidth() {
        return configIntOrDefault(Config::areaMineMaxWidth, areaMineMaxSize());
    }

    public static int areaMineMaxHeight() {
        return configIntOrDefault(Config::areaMineMaxHeight, areaMineMaxSize());
    }

    public static int areaMineMaxDepth() {
        return configIntOrDefault(Config::areaMineMaxDepth, areaMineMaxSize());
    }

    public static int areaDestroyMaxTargets() {
        return configIntOrDefault(Config::areaDestroyMaxTargets, AREA_DESTROY_MAX_TARGETS);
    }

    public static int ultimineBlocksPerTick() {
        return configIntOrDefault(Config::ultimineBlocksPerTick, ULTIMINE_BLOCKS_PER_TICK);
    }

    private static int configIntOrDefault(java.util.function.IntSupplier getter, int fallback) {
        try {
            return Math.max(1, getter.getAsInt());
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    // =========================================================================
    //  Block Validation
    // =========================================================================

    /**
     * Returns {@code true} if the block state is neither air nor
     * unbreakable (destroy speed &lt; 0).  Unlike the old code,
     * <b>waterlogged blocks are allowed</b> ??only pure fluids
     * (states where the block itself is effectively air) are excluded.
     */
    public static boolean isBreakableBlock(BlockState state) {
        // Check isAir first ??this catches pure fluids too since
        // FluidState.isAir() is not the same as BlockState.isAir().
        if (state.isAir()) {
            return false;
        }
        // NOTE: we do NOT check state.getFluidState().isEmpty() here.
        // Waterlogged blocks (stairs, slabs, fences, etc.) have a non-empty
        // FluidState but are perfectly breakable.  Only pure-fluid blocks
        // that are also isAir() are excluded by the check above.
        return true;
    }

    /**
     * Returns {@code true} if the block has a positive destroy speed.
     * Unbreakable blocks (bedrock, end portal frame, etc.) return false.
     */
    public static boolean hasValidDestroySpeed(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    // =========================================================================
    //  Ultimine Candidate Check
    // =========================================================================

    /**
     * Checks whether a candidate block is a valid ultimine target.
     *
     * <p>A candidate is valid if:
     * <ol>
     *   <li>It is not air.</li>
     *   <li>In mode 0, its block type matches the seed block.</li>
     *   <li>The player can access the world target.</li>
     *   <li>Creative mode bypasses further checks.</li>
     *   <li>Survival: the block has a valid destroy speed, is not
     *       significantly harder than the seed block, and the tool can make
     *       progress on it.</li>
     * </ol>
     */
    public static boolean isUltimineCandidate(
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            BlockState seedState,
            int toolSlot,
            ItemStack linkedTool,
            boolean selectedToolRequested,
            boolean creative,
            byte mode) {
        if (!isBreakableBlock(state)) {
            return false;
        }
        if (mode == 0 && state.getBlock() != seedState.getBlock()) {
            return false;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return false;
        }
        if (!RtsClaimProtectionService.canBreakBlock(player, pos, Direction.DOWN)) {
            return false;
        }
        if (creative) {
            return true;
        }
        if (!hasValidDestroySpeed(state, player.serverLevel(), pos)) {
            return false;
        }
        float seedDestroySpeed = seedState.getDestroySpeed(player.serverLevel(), pos);
        float candidateDestroySpeed = state.getDestroySpeed(player.serverLevel(), pos);
        if (seedDestroySpeed >= 0.0F && candidateDestroySpeed > seedDestroySpeed * 1.5F) {
            return false;
        }
        return RtsMiningStateMachine.computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool,
                selectedToolRequested) > 0.0F;
    }

    // =========================================================================
    //  Session / Feature Checks
    // =========================================================================

    /**
     * Returns {@code true} if the auto-store-mined-drops feature is both
     * enabled in the session and unlocked by the player's progression.
     */
    public static boolean canAutoStoreDrops(ServerPlayer player, RtsStorageSession session) {
        return session.autoStoreMinedDrops
                && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS);
    }

    /**
     * Returns {@code true} if an ultimine batch is currently committed
     * (miningPos is null but targets remain).
     */
    public static boolean isCommittedUltimineBatch(RtsStorageSession session) {
        return session.mining.miningPos == null && !session.mining.ultimineTargets.isEmpty();
    }

    /**
     * Checks whether the active tool (from lease or selected slot) is within
     * 5% of its maximum durability.  When protection is enabled, the mining
     * system should stop to avoid breaking the tool.
     */
    public static boolean isToolNearBreak(ServerPlayer player, RtsStorageSession session) {
        if (session == null || !session.mining.miningToolProtectionEnabled) {
            return false;
        }
        ItemStack tool = activeMiningTool(player, session);
        if (tool.isEmpty() || !tool.isDamageableItem()) {
            return false;
        }
        int maxDamage = tool.getMaxDamage();
        if (maxDamage <= 0) {
            return false;
        }
        int remaining = maxDamage - tool.getDamageValue();
        int threshold = Math.max(1, (int) Math.ceil(maxDamage * 0.05D));
        return remaining <= threshold;
    }

    /**
     * Returns the stack of the active mining tool, preferring the tool lease
     * if one exists, otherwise falling back to the player's selected hotbar
     * slot.
     */
    public static ItemStack activeMiningTool(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return ItemStack.EMPTY;
        }
        if (session.mining.miningToolLease != null && !session.mining.miningToolLease.isEmpty()) {
            return session.mining.miningToolLease.stack();
        }
        if (player == null) {
            return ItemStack.EMPTY;
        }
        int slot = clampHotbarSlot(session.mining.miningToolSlot);
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(slot);
    }

    /**
     * Returns {@code true} if the client indicated a non-BlockItem tool was
     * selected in the hotbar (i.e. the player explicitly chose a mining tool
     * and expects the system to borrow it).
     */
    public static boolean isSelectedMiningToolRequested(String toolItemId, ItemStack toolPrototype) {
        return toolItemId != null
                && !toolItemId.isBlank()
                && toolPrototype != null
                && !toolPrototype.isEmpty()
                && !(toolPrototype.getItem() instanceof BlockItem);
    }

    // =========================================================================
    //  Math / Slot Helpers
    // =========================================================================

    /** Maps a progress float to a visible crack stage (0-8). */
    public static int visibleMiningStage(float progress) {
        return Math.min(8, Math.max(0, (int) (progress * 9.0F)));
    }

    /** Clamps a slot index to the valid hotbar range (0-8). */
    public static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    /**
     * Collects connected ultimine candidates starting from {@code seed}.
     * Delegates to {@link RtsUltimineCollector} with an
     * {@link #isUltimineCandidate} predicate for per-block validation.
     */
    public static java.util.Deque<BlockPos> collectUltimineTargets(
            ServerPlayer player, BlockPos seed, int toolSlot, ItemStack linkedTool,
            boolean selectedToolRequested, int limit, boolean creative, byte mode) {
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, seed)) {
            return new java.util.ArrayDeque<>();
        }
        if (!RtsClaimProtectionService.canBreakBlock(player, seed, Direction.DOWN)) {
            return new java.util.ArrayDeque<>();
        }

        java.util.List<BlockPos> targets = RtsUltimineCollector.collect(
                player.serverLevel(),
                seed,
                limit,
                (candidatePos, state, seedState) -> isUltimineCandidate(
                        player,
                        candidatePos,
                        state,
                        seedState,
                        toolSlot,
                        linkedTool,
                        selectedToolRequested,
                        creative,
                        mode));
        return new java.util.ArrayDeque<>(targets);
    }

    // =========================================================================
    //  Placed Block Recovery
    // =========================================================================

    /**
     * Attempts to recover a RTS-placed block at the given position.  If the
     * block was placed by RTS and disappears after break, returns {@code true}
     * to indicate that mining should stop (the recovery succeeded).
     */
    public static boolean tryRecoverPlacedBlock(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face) {
        if (PlacedBlockTrackerData.get(player.serverLevel()).isPlaced(pos)
                && RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            BlockState before = player.serverLevel().getBlockState(pos);
            RtsPlacedRecoveryService.breakPlaced(player, pos, face, false);
            BlockState after = player.serverLevel().getBlockState(pos);
            return !before.equals(after);
        }
        return false;
    }
}
