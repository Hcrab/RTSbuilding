package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 挖掘系统验证谓词中心仓库，提供所有无状态且幂等的检查和限制常量。
 *
 * <p>定义挖掘系统的硬限制和验证方法：
 *
 * <p><b>常量限制：</b>
 * <ul>
 *   <li>{@link #ULTIMINE_MAX_BLOCKS}=256 — BFS 连锁挖掘收集的硬上限</li>
 *   <li>{@link #AREA_MINE_MAX_SIZE}=12 — 区域挖掘每个维度的最大范围</li>
 *   <li>{@link #AREA_DESTROY_MAX_TARGETS}=32768 — 区域破坏接受的最大位置数</li>
 *   <li>{@link #ULTIMINE_BLOCKS_PER_TICK}=8 — 每 tick 处理的连锁挖掘目标数（节流）</li>
 * </ul>
 *
 * <p><b>验证功能：</b>
 * <ul>
 *   <li>{@link #isBreakableBlock} — 判断方块是否可破坏（排除空气，允许含水方块）</li>
 *   <li>{@link #hasValidDestroySpeed} — 检查方块是否有正的破坏速度</li>
 *   <li>{@link #isUltimineCandidate} — 连锁挖掘候选检查（类型匹配、速度比、工具可达性）</li>
 *   <li>{@link #isToolNearBreak} — 检测工具是否即将损坏（≤5% 耐久）</li>
 *   <li>{@link #collectUltimineTargets} — 委托 {@link com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector} 收集连通方块</li>
 *   <li>{@link #tryRecoverPlacedBlock} — 尝试恢复 RTS 已放置的方块</li>
 * </ul>
 */
public final class RtsMiningValidator {

    // =========================================================================
    //  常量
    // =========================================================================

    /** 连锁挖掘批次最多可收集的方块数。 */
    public static final int ULTIMINE_MAX_BLOCKS = 256;

    /** 区域挖掘每维度最大方块数（X、Y、Z）。 */
    public static final int AREA_MINE_MAX_SIZE = 12;

    /** 快速建造接受的显式形状破坏最大目标数。 */
    public static final int AREA_DESTROY_MAX_TARGETS = 32768;

    /** 单个 tick 中处理的连锁挖掘目标数。 */
    public static final int ULTIMINE_BLOCKS_PER_TICK = 8;

    /** 玩家的快捷栏槽位数（0-8）。 */
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;

    private RtsMiningValidator() {
    }

    public static int ultimineMaxBlocks() {
        return configIntOrDefault(Config::ultimineMaxBlocks, ULTIMINE_MAX_BLOCKS);
    }

    public static int areaMineMaxSize() {
        return configIntOrDefault(Config::areaMineMaxSize, AREA_MINE_MAX_SIZE);
    }

    public static int areaDestroyMaxTargets() {
        return configIntOrDefault(Config::areaDestroyMaxTargets, AREA_DESTROY_MAX_TARGETS);
    }

    public static int ultimineBlocksPerTick() {
        return configIntOrDefault(Config::ultimineBlocksPerTick, ULTIMINE_BLOCKS_PER_TICK);
    }

    private static int configIntOrDefault(java.util.function.IntSupplier supplier, int fallback) {
        try {
            return supplier.getAsInt();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    // =========================================================================
    //  方块验证
    // =========================================================================

    /**
     * 如果方块状态既不是空气也不是不可破坏的（破坏速度 < 0），返回 {@code true}。
     * 与旧代码不同，<b>含水方块是允许的</b>——仅排除纯流体
     * （方块本身实际上为空气的状态）。
     */
    public static boolean isBreakableBlock(BlockState state) {
        // 先检查 isAir——这也能捕获纯流体，因为
        // FluidState.isAir() 与 BlockState.isAir() 不同。
        if (state.isAir()) {
            return false;
        }
        // 注意：我们不在此处检查 state.getFluidState().isEmpty()。
        // 含水方块（楼梯、台阶、栅栏等）有非空的 FluidState，
        // 但是完全可以破坏的。只有同时也是 isAir() 的纯流体方块
        // 才会被上面的检查排除。
        return true;
    }

    /**
     * 如果方块具有正的破坏速度，返回 {@code true}。
     * 不可破坏的方块（基岩、末地传送门框架等）返回 false。
     */
    public static boolean hasValidDestroySpeed(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    // =========================================================================
    //  连锁挖掘候选检查
    // =========================================================================

    /**
     * 检查候选方块是否为有效的连锁挖掘目标。
     *
     * <p>候选方块在以下情况下有效：
     * <ol>
     *   <li>不是空气。</li>
     *   <li>在模式 0 下，其方块类型与种子方块匹配。</li>
     *   <li>玩家可以访问世界目标。</li>
     *   <li>创造模式绕过进一步检查。</li>
     *   <li>生存模式：方块有有效的破坏速度，且不比种子方块显著更难。
     *       工具进度会在收集前验证种子块，并在实际破坏前逐块复核。</li>
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
        return true;
    }

    // =========================================================================
    //  会话/功能检查
    // =========================================================================

    /**
     * 如果自动储存挖掘掉落物功能在会话中启用且被玩家的进度解锁，返回 {@code true}。
     */
    public static boolean canAutoStoreDrops(ServerPlayer player, RtsStorageSession session) {
        return session.sessionFlags.autoStoreMinedDrops
                && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS);
    }

    /**
     * 如果连锁挖掘批次已提交（miningPos 为 null 但目标仍存在），返回 {@code true}。
     */
    public static boolean isCommittedUltimineBatch(RtsStorageSession session) {
        return session.mining.miningPos == null && !session.mining.ultimineTargets.isEmpty();
    }

    /**
     * 检查活跃工具（来自租赁或选中槽位）是否在其最大耐久度的 5% 以内。
     * 当保护启用时，挖掘系统应停止以避免破坏工具。
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
     * 返回活跃挖掘工具的堆叠，优先使用工具租赁（如果存在），
     * 否则回退到玩家的选中快捷栏槽位。
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
     * 如果客户端指示快捷栏中选中了非 BlockItem 的工具（即玩家明确选择了
     * 一个挖掘工具并期望系统借用它），返回 {@code true}。
     */
    public static boolean isSelectedMiningToolRequested(String toolItemId, ItemStack toolPrototype) {
        return toolItemId != null
                && !toolItemId.isBlank()
                && toolPrototype != null
                && !toolPrototype.isEmpty()
                && !(toolPrototype.getItem() instanceof BlockItem);
    }

    // =========================================================================
    //  数学/槽位辅助
    // =========================================================================

    /** 将进度浮点映射到可见裂纹阶段（0-8）。 */
    public static int visibleMiningStage(float progress) {
        return Math.min(8, Math.max(0, (int) (progress * 9.0F)));
    }

    /** 将槽位索引限制在有效快捷栏范围（0-8）内。 */
    public static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    /**
     * 从 {@code seed} 开始收集连接的连锁挖掘候选。
     * 委托给 {@link RtsUltimineCollector}，使用 {@link #isUltimineCandidate}
     * 谓词进行每方块验证。
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
        BlockState seedState = player.serverLevel().getBlockState(seed);
        if (!isBreakableBlock(seedState)) {
            return new java.util.ArrayDeque<>();
        }
        if (!creative) {
            if (!hasValidDestroySpeed(seedState, player.serverLevel(), seed)) {
                return new java.util.ArrayDeque<>();
            }
            // 连锁收集阶段只验证种子块能开挖；后续候选的工具判定留给批处理，
            // 避免铺开一片候选时重复计算整批工具速度。
            if (MiningSpeedCalculator.computeRemoteDestroyStep(player, seedState, seed, toolSlot, linkedTool,
                    selectedToolRequested) <= 0.0F) {
                return new java.util.ArrayDeque<>();
            }
        }

        java.util.List<BlockPos> targets = RtsUltimineCollector.collect(
                player.serverLevel(),
                seed,
                limit,
                (candidatePos, state, seedBlockState) -> isUltimineCandidate(
                        player,
                        candidatePos,
                        state,
                        seedBlockState,
                        toolSlot,
                        linkedTool,
                        selectedToolRequested,
                        creative,
                        mode));
        return new java.util.ArrayDeque<>(targets);
    }

    // =========================================================================
    //  已放置方块恢复
    // =========================================================================

    /**
     * 尝试恢复给定位置的 RTS 已放置方块。如果该方块由 RTS 放置且破坏后消失，
     * 返回 {@code true} 指示挖掘应停止（恢复成功）。
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
