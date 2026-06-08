package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.storage.placement.RtsPlacementSound;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * =============================================================================
 * RtsStorageMining — Remote Mining & Ultimine State Machine
 * =============================================================================
 *
 * <p>Owns the server-side remote mining state machine for one
 * {@link RtsStorageSession}.  This class handles everything related to
 * player-visible mining behaviour while the storage screen (or any RTS-mode
 * remote-mining trigger) is active:</p>
 *
 * <ul>
 *   <li><b>Single-block remote mining</b> — starting, ticking, and
 *       completing break progress on one target block.</li>
 *   <li><b>Ultimine batch processing</b> — collecting connected valid
 *       targets, breaking them in per-tick batches, and sending progress.</li>
 *   <li><b>Tool lease management</b> — locating a matching tool in the
 *       player inventory or linked storage, borrowing a single copy, tracking
 *       durability loss, and returning the remainder.</li>
 *   <li><b>Drop absorption</b> — post-break collection of item entities
 *       into linked storage when {@code autoStoreMinedDrops} is enabled.</li>
 *   <li><b>Network synchronisation</b> — sending break-stage and ultimine
 *       progress packets to the client.</li>
 * </ul>
 *
 * <p>This class deliberately does <b>not</b> own crafting, fluid transfer,
 * storage page construction, linked-storage resolution rules, or placement
 * batching — those systems remain in their existing helpers so this split does
 * not widen the gameplay surface.</p>
 *
 * <p>The extracted {@link RtsToolLease} inner class now lives in its own
 * top-level file under the same package.</p>
 *
 * @see RtsToolLease
 * @see RtsStorageSession
 */
public final class RtsStorageMining {

    // =========================================================================
    //  Constants
    // =========================================================================

    /** Maximum number of blocks an ultimine batch may collect. */
    private static final int ULTIMINE_MAX_BLOCKS = 256;
    /** Max blocks per dimension for area mine (X, Y, Z). */
    private static final int AREA_MINE_MAX_SIZE = 12;
    /** Maximum explicit shape-destroy targets accepted from Quick Build. */
    private static final int AREA_DESTROY_MAX_TARGETS = 32768;

    /** How many ultimine targets are processed in a single tick. */
    private static final int ULTIMINE_BLOCKS_PER_TICK = 8;

    /** Number of hotbar slots a player has (0-8). */
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;

    /** Utility class — no instantiation. */
    private RtsStorageMining() {
    }

    // =========================================================================
    //  SECTION 1 — PUBLIC API: Mining State Machine
    // =========================================================================
    //  These methods are the entry points called by RtsStorageManager and
    //  network handlers to start, tick, and cancel remote mining / ultimine.
    // =========================================================================

    /**
     * Entry point for single-block remote mining.
     *
     * <p><b>Start ({@code start == true}):</b>
     * <ol>
     *   <li>Validates progression (REMOTE_BREAK), session, and world-target
     *       access.</li>
     *   <li>If {@code allowPlacedBlockRecovery} is set, attempts to break
     *       the block as a "placed block" first; if the block vanishes,
     *       the recovery succeeded and mining stops.</li>
     *   <li>In creative mode, destroys the block instantly and refreshes
     *       the storage page.</li>
     *   <li>In survival, borrows a matching tool and begins the remote
     *       break-progress animation ({@link #beginRemoteMining}).</li>
     * </ol>
     *
     * <p><b>Stop ({@code start == false}):</b>
     * <ul>
     *   <li>If an ultimine batch is still in progress, does nothing
     *       (ultimine handles its own lifecycle).</li>
     *   <li>Otherwise cancels all active mining via
     *       {@link #stopActiveMining}.</li>
     * </ul>
     *
     * @param player               the server player triggering the action
     * @param session              the player's current storage session
     * @param pos                  target block position
     * @param face                 face being mined (may be {@code null})
     * @param start                {@code true} to start, {@code false} to stop
     * @param toolSlot             the selected hotbar slot (will be clamped to 0-8)
     * @param toolItemId           resource-location string of the tool item
     * @param toolPrototype        prototype ItemStack for matching in inventory
     * @param allowPlacedBlockRecovery  if true, try placed-block recovery first
     */
    public static void mine(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face, boolean start,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery) {
        if (start && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_BREAK)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);

        if (start) {
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                stopActiveMining(player, session);
                return;
            }

            if (allowPlacedBlockRecovery
                    && PlacedBlockTrackerData.get(player.serverLevel()).isPlaced(pos)
                    && RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
                BlockState before = player.serverLevel().getBlockState(pos);
                RtsStorageManager.breakPlaced(player, pos, face, false);
                BlockState after = player.serverLevel().getBlockState(pos);
                if (!before.equals(after)) {
                    stopActiveMining(player, session);
                    return;
                }
            }
            stopActiveMining(player, session);
            if (player.isCreative()) {
                destroyMinedBlock(player, session, pos, slot);
                RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                return;
            }
            session.miningToolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
            beginRemoteMining(player, session, pos, face, slot);
            return;
        }

        if (!isCommittedUltimineBatch(session)) {
            stopActiveMining(player, session);
        }
    }

    /**
     * Starts an ultimine batch (connected-block mining) at the given seed
     * position.
     *
     * <p><b>Flow:</b>
     * <ol>
     *   <li>Validates progression (ULTIMINE) and clamps the limit to
     *       {@link #ULTIMINE_MAX_BLOCKS} and the player's progression cap.</li>
     *   <li>In creative mode, collects and instantly breaks all valid
     *       targets, then refreshes the page.</li>
     *   <li>In survival mode, borrows a tool, collects valid targets,
     *       stores them in the session, and begins remote break progress on
     *       the first target.</li>
     * </ol>
     *
     * @param player          the server player
     * @param session         the player's storage session
     * @param pos             seed position to start ultimine collection from
     * @param face            mining face
     * @param toolSlot        selected hotbar slot
     * @param toolItemId      resource-location string of the tool
     * @param toolPrototype   prototype stack for inventory matching
     * @param requestedLimit  maximum blocks the player requested
     * @param mode            ultimine mode (0 = same block type, etc.)
     */
    public static void startUltimine(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, int requestedLimit, byte mode) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return;
        }
        int limit = Math.max(1, Math.min(Math.min(ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));

        if (player.isCreative()) {
            Deque<BlockPos> targets = collectUltimineTargets(player, pos, slot, ItemStack.EMPTY, limit, true, mode);
            if (targets.isEmpty()) {
                stopActiveMining(player, session);
                return;
            }
            stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        stopActiveMining(player, session);
        RtsToolLease toolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        Deque<BlockPos> targets = collectUltimineTargets(player, pos, slot, toolLease.stack(), limit, false, mode);
        if (targets.isEmpty()) {
            returnMiningTool(player, session, toolLease);
            return;
        }

        session.miningToolLease = toolLease;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = slot;
        sendUltimineProgress(player, 0, targets.size());
        beginRemoteMining(player, session, targets.peekFirst(), face, slot);
    }

    /**
     * Starts an area-mine operation: breaks all breakable blocks within the
     * specified 3D volume bounds.
     *
     * <p>Scans every position inside the bounding box, collects valid targets
     * (non-air, breakable, within world-access range), and feeds them into
     * the existing ultimine batch processing system so that progress, tool
     * leases, drop absorption, and storage-page refreshes work identically.</p>
     *
     * <p><b>流程：</b>
     * <ol>
     *   <li>检查 ULTIMINE 特性权限和会话状态</li>
     *   <li>对每个维度 clamp 到 {@link #AREA_MINE_MAX_SIZE}</li>
     *   <li>三重循环扫描立方体范围内的所有方块</li>
     *   <li>过滤空气、不可破坏方块和超出世界访问范围的方块</li>
     *   <li>创造模式直接全部破坏；生存模式借用工具并启动远程挖掘</li>
     * </ol>
     *
     * @param player          the server player
     * @param session         the player's storage session
     * @param minX, maxX      inclusive X-axis bounds
     * @param minY, maxY      inclusive Y-axis bounds
     * @param minZ, maxZ      inclusive Z-axis bounds
     * @param toolSlot        selected hotbar slot
     * @param toolItemId      resource-location string of the tool
     * @param toolPrototype   prototype stack for inventory matching
     */
    public static void areaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType) {
        // --- 1. 前置检查 ---
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            return;
        }

        // --- 2. 边界裁剪：确保每个维度不超过 AREA_MINE_MAX_SIZE（客户端也应执行同款 clamp） ---
        int clampedMinX = minX;
        int clampedMaxX = Math.min(clampedMinX + AREA_MINE_MAX_SIZE - 1, maxX);
        int clampedMinZ = minZ;
        int clampedMaxZ = Math.min(clampedMinZ + AREA_MINE_MAX_SIZE - 1, maxZ);
        int clampedMinY = minY;
        int clampedMaxY = Math.min(clampedMinY + AREA_MINE_MAX_SIZE - 1, maxY);

        // --- 3. 计算形状中心与半径 ---
        double cx = (clampedMinX + clampedMaxX + 1) / 2.0D;
        double cz = (clampedMinZ + clampedMaxZ + 1) / 2.0D;
        double rx = (clampedMaxX - clampedMinX + 1) / 2.0D;
        double rz = (clampedMaxZ - clampedMinZ + 1) / 2.0D;
        double cylRadiusSq = Math.max(rx, rz) * Math.max(rx, rz);

        int boxDx = clampedMaxX - clampedMinX;
        int boxDy = clampedMaxY - clampedMinY;
        int boxDz = clampedMaxZ - clampedMinZ;

        // --- 4. 三重循环扫描范围，按形状过滤（最大 12×12×12 = 1728 格）---
        //     每个方块依次检查：形状过滤 → 世界访问权限 → 非空气 → 可破坏
        ServerLevel level = player.serverLevel();
        Deque<BlockPos> targets = new ArrayDeque<>();
        for (int y = clampedMinY; y <= clampedMaxY; y++) {
            for (int x = clampedMinX; x <= clampedMaxX; x++) {
                for (int z = clampedMinZ; z <= clampedMaxZ; z++) {
                    // 形状过滤（枚举 ordinal: 0=BLOCK, 1=LINE, 2=SQUARE, 3=WALL, 4=CIRCLE, 5=BOX）
                    if (shapeType == 0) {
                        // BLOCK：仅中心方块
                        int cxBlock = clampedMinX + boxDx / 2;
                        int cyBlock = clampedMinY + boxDy / 2;
                        int czBlock = clampedMinZ + boxDz / 2;
                        if (x != cxBlock || y != cyBlock || z != czBlock) continue;
                    } else if (shapeType == 1) {
                        // LINE：沿最长轴延伸的直线
                        if (boxDx >= boxDy && boxDx >= boxDz) {
                            if (y != clampedMinY || z != clampedMinZ) continue;
                        } else if (boxDy >= boxDx && boxDy >= boxDz) {
                            if (x != clampedMinX || z != clampedMinZ) continue;
                        } else {
                            if (x != clampedMinX || y != clampedMinY) continue;
                        }
                    } else if (shapeType == 2) {
                        // SQUARE：仅底面一层
                        if (y != clampedMinY) continue;
                    } else if (shapeType == 3) {
                        // WALL：垂直外壁（XZ 平面边界）
                        boolean onWall = (x == clampedMinX || x == clampedMaxX)
                                || (z == clampedMinZ || z == clampedMaxZ);
                        if (!onWall) continue;
                    } else if (shapeType == 4) {
                        // CIRCLE：XZ 平面圆形（等同旧 CYLINDER）
                        double ddx = (x + 0.5D) - cx;
                        double ddz = (z + 0.5D) - cz;
                        if ((ddx * ddx + ddz * ddz) > cylRadiusSq + 0.5D) continue;
                    }
                    // BOX (shapeType == 5): 不过滤，全部加入
                    if (!includeAreaMineFillCell(shapeType, fillType, clampedMinX, clampedMaxX,
                            clampedMinY, clampedMaxY, clampedMinZ, clampedMaxZ,
                            x, y, z, cx, cz, cylRadiusSq)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }
                    targets.add(pos.immutable());
                }
            }
        }

        // --- 4. 处理结果 ---
        if (targets.isEmpty()) {
            // 无可破坏方块 → 停止当前挖掘
            stopActiveMining(player, session);
            return;
        }

        if (player.isCreative()) {
            // 创造模式：瞬间全部破坏
            stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        // 生存模式：借用工具，设置 Ultimine 目标队列，逐步远程挖掘
        stopActiveMining(player, session);
        RtsToolLease toolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);

        session.miningToolLease = toolLease;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningToolSlot = slot;
        sendUltimineProgress(player, 0, targets.size());
        beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    public static void areaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, String toolItemId, ItemStack toolPrototype) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)) {
            return;
        }
        if (session == null || positions == null || positions.isEmpty()) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);
        if (player.isCreative()) {
            Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, ItemStack.EMPTY, true);
            if (targets.isEmpty()) {
                stopActiveMining(player, session);
                return;
            }
            stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        stopActiveMining(player, session);
        RtsToolLease toolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, toolLease.stack(), false);
        if (targets.isEmpty()) {
            returnMiningTool(player, session, toolLease);
            return;
        }

        session.miningToolLease = toolLease;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningToolSlot = slot;
        sendUltimineProgress(player, 0, targets.size());
        beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    private static Deque<BlockPos> collectAreaDestroyTargets(ServerPlayer player, List<BlockPos> positions,
            int toolSlot, ItemStack linkedTool, boolean creative) {
        if (player == null || positions == null || positions.isEmpty()) {
            return new ArrayDeque<>();
        }
        ServerLevel level = player.serverLevel();
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos raw : positions) {
            if (raw == null || unique.size() >= AREA_DESTROY_MAX_TARGETS) {
                continue;
            }
            BlockPos pos = raw.immutable();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            if (!creative && computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool) <= 0.0F) {
                continue;
            }
            unique.add(pos);
        }
        return new ArrayDeque<>(unique);
    }

    private static boolean includeAreaMineFillCell(byte shapeType, byte fillType,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            int x, int y, int z, double cx, double cz, double radiusSq) {
        if (fillType <= 0 || shapeType == 0 || shapeType == 1) {
            return true;
        }
        boolean xBoundary = x == minX || x == maxX;
        boolean yBoundary = y == minY || y == maxY;
        boolean zBoundary = z == minZ || z == maxZ;
        int boundaryAxes = (xBoundary ? 1 : 0) + (yBoundary ? 1 : 0) + (zBoundary ? 1 : 0);
        if (shapeType == 2) {
            return xBoundary || zBoundary;
        }
        if (shapeType == 4) {
            double radius = Math.sqrt(radiusSq);
            double inner = Math.max(0.0D, radius - 1.0D);
            double ddx = (x + 0.5D) - cx;
            double ddz = (z + 0.5D) - cz;
            return (ddx * ddx + ddz * ddz) >= inner * inner - 0.5D;
        }
        if (fillType >= 2) {
            return boundaryAxes >= 2;
        }
        return boundaryAxes >= 1;
    }

    /**
     * Main tick handler for remote mining progress, invoked every server tick
     * while the player is in an RTS screen or remote-mining state.
     *
     * <p><b>Single-block mode</b> ({@code session.miningPos != null}):
     * <ol>
     *   <li>Validates world-target access and block breakability.</li>
     *   <li>Computes the destroy step (mining speed adjusted for tool and
     *       underwater penalties).</li>
     *   <li>Accumulates progress and sends break-stage updates to the
     *       client.</li>
     *   <li>On completion (progress ≥ 1.0F), breaks the block and either
     *       proceeds to the next ultimine target or finalises.</li>
     * </ol>
     *
     * <p><b>Ultimine mode</b> ({@code session.miningPos == null}):
     * delegates to {@link #processUltimineTargets}.</p>
     *
     * @param player   the server player
     * @param session  the player's storage session
     */
    public static void tickActiveMining(ServerPlayer player, RtsStorageSession session) {
        if (session.miningPos == null) {
            if (!session.ultimineTargets.isEmpty()) {
                processUltimineTargets(player, session);
            }
            return;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.miningPos)) {
            stopActiveMining(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = session.miningPos;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0.0F) {
            stopActiveMining(player, session);
            return;
        }

        float step = computeRemoteDestroyStep(player, state, pos, session.miningToolSlot, session.miningToolLease.stack());
        if (step <= 0.0F) {
            return;
        }

        session.miningProgress += step;
        int stage = Math.min(9, (int) (session.miningProgress * 10.0F));
        if (stage != session.miningStage) {
            level.destroyBlockProgress(player.getId(), pos, stage);
            sendMineProgress(player, pos, stage);
            session.miningStage = stage;
        }

        if (session.miningProgress < 1.0F) {
            return;
        }

        boolean broken = destroyMinedBlock(player, session, pos, session.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);

        if (broken && !session.ultimineTargets.isEmpty()) {
            removeUltimineTarget(session, pos);
            session.ultimineProcessedTargets = Math.max(session.ultimineProcessedTargets, 1);
            if (canAutoStoreDrops(player, session)) {
                session.ultimineAbsorbedDrops |= absorbNearbyMinedDrops(player, pos, session);
            }
            session.miningPos = null;
            session.miningProgress = 0.0F;
            session.miningStage = -1;
            processUltimineTargets(player, session);
            return;
        }

        clearMineProgress(player, pos);
        if (broken && canAutoStoreDrops(player, session)) {
            boolean absorbed = absorbNearbyMinedDrops(player, pos, session);
            if (absorbed) {
                RtsStorageManager.runQuestDetect(player, session, false);
            }
        }
        returnMiningTool(player, session, session.miningToolLease);
        markMiningStorageDirty(player, session);
        resetMiningState(session);
    }

    /**
     * Stops all active mining/ultimine activity for the given session,
     * clears break-stage particles on the client, returns the borrowed tool,
     * and resets the session's mining state.
     *
     * @param player   the server player
     * @param session  the player's storage session
     */
    public static void stopActiveMining(ServerPlayer player, RtsStorageSession session) {
        boolean hadMiningState = session.miningPos != null
                || session.ultimineProgressPos != null
                || !session.ultimineTargets.isEmpty()
                || !session.miningToolLease.isEmpty();
        boolean hadUltimine = session.ultimineProgressPos != null || !session.ultimineTargets.isEmpty();
        BlockPos progressPos = session.miningPos != null ? session.miningPos : session.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            sendMineProgress(player, progressPos, -1);
        }
        if (hadUltimine) {
            sendUltimineProgress(player, -1, 0);
        }
        returnMiningTool(player, session, session.miningToolLease);
        if (hadMiningState) {
            markMiningStorageDirty(player, session);
        }
        resetMiningState(session);
    }

    public static void markMiningStorageDirty(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        RtsStorageManager.markStorageViewDirty(player, session);
    }

    // =========================================================================
    //  SECTION 2 — CORE MINING OPERATIONS
    // =========================================================================
    //  Low-level mining operations: starting break progress on a position,
    //  executing block destruction with a tool lease, and computing the
    //  per-tick destroy step (with underwater penalty removal).
    // =========================================================================

    /**
     * Initialises remote mining state for the given block position, clearing
     * any previous break-stage particles from a different target.
     */
    private static void beginRemoteMining(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face,
            int toolSlot) {
        if (session.miningPos != null && !session.miningPos.equals(pos)) {
            clearMineProgress(player, session.miningPos);
        }
        session.miningPos = pos.immutable();
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = clampHotbarSlot(toolSlot);
        session.miningProgress = 0.0F;
        session.miningStage = -1;
    }

    /**
     * Destroys the block at {@code pos}, either via a borrowed tool lease
     * (which tracks the mutated remainder) or by temporarily switching the
     * player's selected hotbar slot.
     *
     * @return {@code true} if the block was successfully broken
     */
    private static boolean destroyMinedBlock(ServerPlayer player, RtsStorageSession session, BlockPos pos, int toolSlot) {
        BlockState beforeState = player.serverLevel().getBlockState(pos);
        boolean broken;
        if (session != null && session.miningToolLease != null && !session.miningToolLease.isEmpty()) {
            RtsToolLease lease = session.miningToolLease;
            MiningDestroyOutcome outcome = destroyBlockWithTemporaryMainHand(player, pos, lease.stack());
            session.miningToolLease = lease.withStack(protectBorrowedToolRemainder(player, lease, outcome.remainder()));
            broken = outcome.broken();
        } else {
            broken = withTemporarySelectedSlot(player, toolSlot, () -> player.gameMode.destroyBlock(pos));
        }
        if (broken) {
            sendBreakAnimation(player, pos, beforeState);
            RtsPlacementSound.playRemoteBlockBreakSound(player, player.serverLevel(), pos);
        }
        return broken;
    }

    /**
     * Computes the per-tick destroy progress for the given block/tool
     * combination, applying underwater penalty cancellation.
     *
     * @return a float in (0.0, 1.0] representing progress per tick, or
     *         ≤ 0.0 if the block cannot be mined
     */
    private static float computeRemoteDestroyStep(ServerPlayer player, BlockState state, BlockPos pos, int toolSlot,
            ItemStack linkedTool) {
        if (linkedTool != null && !linkedTool.isEmpty()) {
            return withTemporaryOnGround(player, true, () -> withTemporaryMainHandItem(
                    player,
                    linkedTool,
                    () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
        }
        return withTemporaryOnGround(player, true, () -> withTemporarySelectedSlot(
                player,
                toolSlot,
                () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
    }

    /**
     * Swaps the player's main hand to the given tool stack, destroys the
     * block, reads back the (possibly damaged) remainder, and restores the
     * original main-hand item.
     *
     * @return a {@link MiningDestroyOutcome} with the break result and the
     *         tool remainder
     */
    private static MiningDestroyOutcome destroyBlockWithTemporaryMainHand(ServerPlayer player, BlockPos pos, ItemStack tool) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        boolean broken;
        ItemStack remainder;
        try {
            broken = player.gameMode.destroyBlock(pos);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new MiningDestroyOutcome(broken, remainder);
    }

    /**
     * Cancels the underwater mining speed penalty ({@code SUBMERGED_MINING_SPEED})
     * while preserving any positive modifier from enchantments or mods.
     *
     * <p>Vanilla 1.21.1 applies a penalty when the player is submerged; this
     * method removes only the penalty portion (values < 1.0) so that buffs are
     * retained for remote mining.</p>
     */
    private static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        if (destroyStep <= 0.0F) {
            return destroyStep;
        }
        float adjusted = destroyStep;
        if (player.isEyeInFluid(FluidTags.WATER)) {
            double submergedMiningSpeed = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed > 0.0D && submergedMiningSpeed < 1.0D) {
                adjusted *= (float) (1.0D / submergedMiningSpeed);
            }
        }
        return adjusted;
    }

    // =========================================================================
    //  SECTION 3 — ULTIMINE PROCESSING
    // =========================================================================
    //  Ultimine batch lifecycle: collecting connected valid targets, breaking
    //  them in per-tick batches (ULTIMINE_BLOCKS_PER_TICK), and sending
    //  break-stage + ultimine progress packets to the client.
    // =========================================================================

    /**
     * Processes up to {@link #ULTIMINE_BLOCKS_PER_TICK} queued ultimine
     * targets. Each target is validated (world access, breakability, tool
     * compatibility) before being destroyed. If auto-store is enabled, drops
     * are absorbed into linked storage.
     */
    private static void processUltimineTargets(ServerPlayer player, RtsStorageSession session) {
        if (session.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        int processedThisTick = 0;
        while (processedThisTick < ULTIMINE_BLOCKS_PER_TICK && !session.ultimineTargets.isEmpty()) {
            BlockPos target = session.ultimineTargets.removeFirst();
            processedThisTick++;
            session.ultimineProcessedTargets++;

            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            BlockState targetState = level.getBlockState(target);
            if (targetState.isAir() || !targetState.getFluidState().isEmpty()
                    || targetState.getDestroySpeed(level, target) < 0.0F) {
                continue;
            }
            if (computeRemoteDestroyStep(player, targetState, target, session.miningToolSlot, session.miningToolLease.stack()) <= 0.0F) {
                continue;
            }
            boolean targetBroken = destroyMinedBlock(player, session, target, session.miningToolSlot);
            if (targetBroken && canAutoStoreDrops(player, session)) {
                session.ultimineAbsorbedDrops |= absorbNearbyMinedDrops(player, target, session);
            }
            if (targetBroken && isToolNearBreak(session)) {
                break;
            }
        }

        sendUltimineBatchProgress(player, session);
        if (session.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
        }
    }

    /**
     * Sends the current ultimine batch progress to the client, mapping
     * {@code processed / total} to a break stage (0-9).
     */
    private static void sendUltimineBatchProgress(ServerPlayer player, RtsStorageSession session) {
        BlockPos progressPos = session.ultimineProgressPos;
        if (progressPos == null) {
            return;
        }
        int total = Math.max(1, session.ultimineTotalTargets);
        int processed = session.ultimineProcessedTargets;
        int stage = Math.min(9, (int) (processed / (double) total * 10.0D));
        sendMineProgress(player, progressPos, stage);
        sendUltimineProgress(player, processed, total);
    }

    /**
     * Finalises an ultimine batch: clears progress, triggers quest detection
     * if drops were absorbed, returns the borrowed tool, schedules a storage
     * page refresh, and resets the mining state.
     */
    private static void finishUltimineBatch(ServerPlayer player, RtsStorageSession session) {
        sendUltimineProgress(player, -1, 0);
        if (session.ultimineAbsorbedDrops) {
            RtsStorageManager.runQuestDetect(player, session, false);
        }
        returnMiningTool(player, session, session.miningToolLease);
        markMiningStorageDirty(player, session);
        BlockPos progressPos = session.ultimineProgressPos;
        if (progressPos != null) {
            clearMineProgress(player, progressPos);
        }
        resetMiningState(session);
    }

    /**
     * Collects connected ultimine candidates starting from {@code seed}.
     * Delegates to {@link RtsUltimineCollector} with an {@link #isUltimineCandidate}
     * predicate for per-block validation.
     */
    private static Deque<BlockPos> collectUltimineTargets(ServerPlayer player, BlockPos seed, int toolSlot, ItemStack linkedTool,
            int limit) {
        return collectUltimineTargets(player, seed, toolSlot, linkedTool, limit, player != null && player.isCreative(), (byte) 0);
    }

    private static Deque<BlockPos> collectUltimineTargets(ServerPlayer player, BlockPos seed, int toolSlot, ItemStack linkedTool,
            int limit, boolean creative, byte mode) {
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, seed)) {
            return new ArrayDeque<>();
        }

        ServerLevel level = player.serverLevel();
        List<BlockPos> targets = RtsUltimineCollector.collect(
                level,
                seed,
                limit,
                (candidatePos, state, seedState) -> isUltimineCandidate(
                        player,
                        candidatePos,
                        state,
                        seedState,
                        toolSlot,
                        linkedTool,
                        creative,
                        mode));
        return new ArrayDeque<>(targets);
    }

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
    private static boolean isUltimineCandidate(
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            BlockState seedState,
            int toolSlot,
            ItemStack linkedTool,
            boolean creative,
            byte mode) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (mode == 0 && state.getBlock() != seedState.getBlock()) {
            return false;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return false;
        }
        if (creative) {
            return true;
        }
        if (state.getDestroySpeed(player.serverLevel(), pos) < 0.0F) {
            return false;
        }
        float seedDestroySpeed = seedState.getDestroySpeed(player.serverLevel(), pos);
        float candidateDestroySpeed = state.getDestroySpeed(player.serverLevel(), pos);
        if (seedDestroySpeed >= 0.0F && candidateDestroySpeed > seedDestroySpeed * 1.5F) {
            return false;
        }
        return computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool) > 0.0F;
    }

    /**
     * Instantly breaks all queued ultimine targets for a creative-mode player.
     */
    private static void breakCreativeUltimineTargets(ServerPlayer player, RtsStorageSession session, Deque<BlockPos> targets,
            int toolSlot) {
        while (!targets.isEmpty()) {
            BlockPos target = targets.removeFirst();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            destroyMinedBlock(player, session, target, toolSlot);
        }
    }

    /** Removes a specific position from the ultimine target queue. */
    private static void removeUltimineTarget(RtsStorageSession session, BlockPos pos) {
        session.ultimineTargets.removeIf(target -> target.equals(pos));
    }

    /**
     * Returns {@code true} if an ultimine batch is currently committed
     * (miningPos is null but targets remain).
     */
    private static boolean isCommittedUltimineBatch(RtsStorageSession session) {
        return session.miningPos == null && !session.ultimineTargets.isEmpty();
    }

    // =========================================================================
    //  SECTION 4 — TOOL LEASE MANAGEMENT
    // =========================================================================
    //  Borrowing a matching tool from the player's inventory or linked
    //  storage, tracking durability via RtsToolLease, and returning the
    //  remainder (with a safety fallback for non-damageable tools).
    // =========================================================================

    /**
     * Locates a real tool matching {@code toolPrototype} and borrows a single
     * copy, searching the player's main inventory first, then linked storage.
     *
     * <p>The tool item is validated against the registry: it must exist, must
     * not be a {@link BlockItem}, and must match {@code toolPrototype}.</p>
     *
     * @return a non-empty {@link RtsToolLease} on success, or
     *         {@link RtsToolLease#empty()} if no tool was found
     */
    private static RtsToolLease borrowMiningTool(ServerPlayer player, RtsStorageSession session, String toolItemId,
            ItemStack toolPrototype, int selectedToolSlot) {
        if (player == null || session == null || toolPrototype == null || toolPrototype.isEmpty()
                || toolItemId == null || toolItemId.isBlank()) {
            return RtsToolLease.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(toolItemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return RtsToolLease.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item instanceof BlockItem || toolPrototype.getItem() != item) {
            return RtsToolLease.empty();
        }

        RtsToolLease playerLease = borrowMiningToolFromPlayerInventory(player, toolPrototype, selectedToolSlot);
        if (!playerLease.isEmpty()) {
            return playerLease;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.orderHandlersForExtract(
                RtsLinkedStorageResolver.resolveLinkedHandlers(player, session));
        if (activeLinked.isEmpty()) {
            return RtsToolLease.empty();
        }
        for (LinkedHandler linked : activeLinked) {
            RtsToolLease linkedLease = borrowMiningToolFromLinkedHandler(linked.handler(), toolPrototype);
            if (!linkedLease.isEmpty()) {
                return linkedLease;
            }
        }
        return RtsToolLease.empty();
    }

    /**
     * Scans the player's main inventory (excluding the selected hotbar slot)
     * for a matching tool, then checks the remaining hotbar slots.
     */
    private static RtsToolLease borrowMiningToolFromPlayerInventory(ServerPlayer player, ItemStack prototype, int selectedToolSlot) {
        int selected = clampHotbarSlot(selectedToolSlot);
        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            RtsToolLease lease = borrowMiningToolFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) {
                return lease;
            }
        }
        for (int slot = 0; slot < PLAYER_HOTBAR_SLOT_COUNT; slot++) {
            if (slot == selected) {
                continue;
            }
            RtsToolLease lease = borrowMiningToolFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) {
                return lease;
            }
        }
        return RtsToolLease.empty();
    }

    /**
     * Attempts to split off a single item from the given player inventory slot.
     *
     * @return a player-slot lease, or {@link RtsToolLease#empty()} if the slot
     *         does not match {@code prototype}
     */
    private static RtsToolLease borrowMiningToolFromPlayerSlot(ServerPlayer player, ItemStack prototype, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return RtsToolLease.empty();
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, prototype)) {
            return RtsToolLease.empty();
        }
        ItemStack borrowed = current.split(1);
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, current);
        }
        player.getInventory().setChanged();
        return borrowed.isEmpty() ? RtsToolLease.empty() : RtsToolLease.playerSlot(slot, borrowed);
    }

    /**
     * Searches a linked {@link IItemHandler} for a matching tool and extracts
     * one item. If extraction yields a non-matching item, it is re-inserted.
     *
     * @return a linked-storage lease, or {@link RtsToolLease#empty()}
     */
    private static RtsToolLease borrowMiningToolFromLinkedHandler(IItemHandler handler, ItemStack prototype) {
        if (handler == null || prototype == null || prototype.isEmpty()) {
            return RtsToolLease.empty();
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) {
                continue;
            }
            ItemStack borrowed = handler.extractItem(slot, 1, false);
            if (!borrowed.isEmpty() && ItemStack.isSameItemSameComponents(borrowed, prototype)) {
                return RtsToolLease.linkedSlot(handler, slot, borrowed);
            }
            if (!borrowed.isEmpty()) {
                RtsStorageTransfers.insertToHandlerPreferExisting(handler, borrowed);
            }
        }
        return RtsToolLease.empty();
    }

    /**
     * Returns the borrowed tool (or its damaged remainder) to the original
     * source. If the source slot is unavailable, falls back to linked storage
     * or the player's inventory.
     */
    private static void returnMiningTool(ServerPlayer player, RtsStorageSession session, RtsToolLease lease) {
        if (player == null || session == null || lease == null || lease.isEmpty()) {
            return;
        }
        ItemStack remain = lease.returnToSource(player);
        if (remain.isEmpty()) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        RtsStorageTransfers.storeToLinkedWithFallback(handlers, player, remain);
    }

    /**
     * Safety fallback: if the borrowed tool remainder is unexpectedly empty
     * and the original stack meets {@link #shouldProtectEmptyBorrowedToolRemainder}
     * criteria, restores the original stack to avoid losing non-repairable,
     * single-stack tools.
     */
    private static ItemStack protectBorrowedToolRemainder(ServerPlayer player, RtsToolLease lease, ItemStack remainder) {
        if (remainder != null && !remainder.isEmpty()) {
            return remainder;
        }
        ItemStack original = lease.original();
        if (!shouldProtectEmptyBorrowedToolRemainder(original)) {
            return ItemStack.EMPTY;
        }
        RtsbuildingMod.LOGGER.warn(
                "RTS borrowed mining tool from {} became empty after block break; restoring original stack as a safety fallback for {}.",
                lease.describeSource(),
                player == null ? "unknown player" : player.getGameProfile().getName());
        return original.copy();
    }

    /**
     * Determines whether the safety fallback should protect an empty tool
     * remainder. Protection applies to non-stackable, non-damageable items
     * that are not {@link BlockItem}s.
     */
    private static boolean shouldProtectEmptyBorrowedToolRemainder(ItemStack original) {
        return original != null
                && !original.isEmpty()
                && !(original.getItem() instanceof BlockItem)
                && original.getMaxStackSize() == 1
                && !original.isDamageableItem();
    }

    // =========================================================================
    //  SECTION 5 — TEMPORARY CONTEXT SWITCHERS
    // =========================================================================
    //  Helpers that temporarily swap a player's state (selected slot, main
    //  hand item, on-ground flag) to make Minecraft API calls behave as if
    //  the player were standing at the remote target position.
    // =========================================================================

    /**
     * Temporarily sets the player's main hand item to {@code stack}, calls
     * the action, and restores the original hand item.
     */
    public static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            return action.get();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
    }

    /**
     * Temporarily sets the player's on-ground flag, calls the action, and
     * restores the original value.
     */
    public static <T> T withTemporaryOnGround(ServerPlayer player, boolean onGround, Supplier<T> action) {
        boolean previous = player.onGround();
        player.setOnGround(onGround);
        try {
            return action.get();
        } finally {
            player.setOnGround(previous);
        }
    }

    /**
     * Temporarily changes the player's selected hotbar slot, calls the
     * action, and restores the original selection.
     */
    private static <T> T withTemporarySelectedSlot(ServerPlayer player, int toolSlot, Supplier<T> action) {
        int slot = clampHotbarSlot(toolSlot);
        int prevSelected = player.getInventory().selected;

        player.getInventory().selected = slot;
        try {
            return action.get();
        } finally {
            player.getInventory().selected = prevSelected;
        }
    }

    /** Clamps a slot index to the valid hotbar range (0-8). */
    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    // =========================================================================
    //  SECTION 6 — DROP ABSORPTION
    // =========================================================================
    //  Collects item entities near the mined block and stores them into linked
    //  storage first, then the player's inventory when autoStoreMinedDrops is enabled.
    // =========================================================================

    /**
     * Scans for {@link ItemEntity}s within a 1.25-block radius of the mined
     * position and stores each matching drop into linked storage first, then the
     * player's inventory. If both destinations are full, the remaining item
     * stays in the world.
     *
     * @param player   the server player
     * @param pos      the mined block position
     * @param session  the player's storage session
     * @return {@code true} if at least one drop was absorbed
     */
    private static boolean absorbNearbyMinedDrops(ServerPlayer player, BlockPos pos, RtsStorageSession session) {
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);

        AABB box = new AABB(pos).inflate(1.25D);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());
        boolean changed = false;
        for (ItemEntity drop : drops) {
            ItemStack original = drop.getItem();
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remain = handlers.isEmpty()
                    ? original.copy()
                    : RtsStorageTransfers.storeToLinkedOnly(handlers, original);
            if (!remain.isEmpty()) {
                remain = RtsStorageTransfers.moveToPlayerInventoryOnly(player, remain);
            }
            if (remain.getCount() != original.getCount()) {
                changed = true;
            }
            if (remain.isEmpty()) {
                drop.discard();
            } else if (remain.getCount() != original.getCount()) {
                drop.setItem(remain);
            }
        }
        return changed;
    }

    // =========================================================================
    //  SECTION 7 — NETWORK COMMUNICATION
    // =========================================================================
    //  Sends break-stage and ultimine progress payloads to the client for
    //  visual feedback (block break cracks, progress bar).
    // =========================================================================

    private static void sendMineProgress(ServerPlayer player, BlockPos pos, int stage) {
        PacketDistributor.sendToPlayer(player, new S2CRtsMineProgressPayload(pos, (byte) stage));
    }

    private static void sendBreakAnimation(ServerPlayer player, BlockPos pos, BlockState state) {
        if (player == null || pos == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsBreakAnimationPayload(pos.immutable(), state));
    }

    private static void sendUltimineProgress(ServerPlayer player, int processed, int total) {
        PacketDistributor.sendToPlayer(player, new S2CRtsUltimineProgressPayload(processed, total));
    }

    // =========================================================================
    //  SECTION 8 — HELPERS & INNER TYPES
    // =========================================================================

    /**
     * Resets all mining-related fields on the session to their default values.
     * This is the canonical cleanup method called after every mining operation
     * completes or is cancelled.
     */
    private static void resetMiningState(RtsStorageSession session) {
        session.miningPos = null;
        session.ultimineTargets.clear();
        session.ultimineProgressPos = null;
        session.ultimineTotalTargets = 0;
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningProgress = 0.0F;
        session.miningStage = -1;
        session.miningToolLease = RtsToolLease.empty();
    }

    /**
     * Clears break-stage particles on both the server and client for the
     * given position.
     */
    private static void clearMineProgress(ServerPlayer player, BlockPos pos) {
        player.serverLevel().destroyBlockProgress(player.getId(), pos, -1);
        sendMineProgress(player, pos, -1);
    }

    /**
     * Returns {@code true} if the auto-store-mined-drops feature is both
     * enabled in the session and unlocked by the player's progression.
     */
    private static boolean canAutoStoreDrops(ServerPlayer player, RtsStorageSession session) {
        return session.autoStoreMinedDrops
                && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS);
    }

    private static boolean isToolNearBreak(RtsStorageSession session) {
        if (session == null || session.miningToolLease == null || session.miningToolLease.isEmpty()) {
            return false;
        }
        ItemStack tool = session.miningToolLease.stack();
        return tool.isDamageableItem()
                && tool.getDamageValue() >= tool.getMaxDamage() - 1;
    }

    /** Outcome of destroying a block with a temporary main-hand tool. */
    private record MiningDestroyOutcome(boolean broken, ItemStack remainder) {
    }
}
