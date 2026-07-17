package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.task.placement.PlacementSliceResult;
import com.rtsbuilding.rtsbuilding.server.task.placement.PlacementResumePolicy;
import com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 远程批量放置的命令构造与单 slice 执行器。
 *
 * <p>本类把请求冻结为 {@link PlaceBatchJob} / {@link PlacementTaskState}，并在任务引擎
 * 分配的数量与纳秒预算内执行一个 slice；跨 tick 生命周期只由 TaskStore 持有。
 *
 * <p>快速建造作业（形状建造）受 {@link #BUILD_BATCH_MAX_QUEUED_JOBS}=4 限制，
 * 单个方块放置无限制。NBT 只用于 durable task payload，不再写入 Session 队列。
 *
 * <p>不负责：单方块放置逻辑（{@link RtsPlacementExecutor}）、
 * 状态计划预解析（{@link RtsPlacementQuickBuild}）、
 * 物品提取（{@link RtsPlacementExtractor}）、声音（{@link RtsPlacementSound}）。
 */
public final class RtsPlacementBatch {
    private static final int BUILD_BATCH_MAX_BLOCKS_PER_TICK = 64;
    private static final int BUILD_BATCH_MAX_QUEUED_JOBS = 4;

    private RtsPlacementBatch() {
    }

    /**
     * Queues a batch of positions for remote placement. Sanitises input,
     * validates progression access, and caps the batch at
     * {@link C2SRtsPlaceBatchPayload#MAX_POSITIONS} positions.
     * <p>
     * Quick-build jobs (shape builds) are limited to
     * {@link #BUILD_BATCH_MAX_QUEUED_JOBS} queued jobs; when the queue is full,
     * new quick-build jobs are rejected. Single-block placements
     * ({@code quickBuild = false}) bypass this limit.
     */
    /**
     * Queues a batch of positions for remote placement.
     *
     * @return {@code true} if the job was actually queued; {@code false} if the
     *         job was silently skipped (progression locked, no valid positions,
     *         or quick-build queue full).  Callers should use this return value
     *         to decide whether to complete the associated workflow entry.
     */
    public static boolean enqueuePlaceBatch(ServerPlayer player, RtsStorageSession session, List<BlockPos> clickedPositions,
            Direction face, double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX, double rayDirY,
            double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint,
            int workflowEntryId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            if (sendRemoteHint && player != null) {
                player.sendSystemMessage(
                        Component.translatable("message.rtsbuilding.quick_build.remote_place_locked"), true);
            }
            return false;
        }
        if (session == null || clickedPositions == null || clickedPositions.isEmpty() || face == null) {
            return false;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<BlockPos> positions = new ArrayList<>(Math.min(clickedPositions.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
        for (BlockPos pos : clickedPositions) {
            if (pos == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            positions.add(pos.immutable());
            if (positions.size() >= C2SRtsPlaceBatchPayload.MAX_POSITIONS) {
                break;
            }
        }
        if (positions.isEmpty()) {
            return false;
        }
        // Quick-build jobs (shape builds) are limited to BUILD_BATCH_MAX_QUEUED_JOBS;
        // reject when full. Single-block placements bypass this limit.
        PlaceBatchJob job = new PlaceBatchJob(
                positions,
                face,
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetX, face, Direction.Axis.X),
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetY, face, Direction.Axis.Y),
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetZ, face, Direction.Axis.Z),
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId == null ? "" : itemId,
                RtsPlacementExtractor.sanitizePrototype(itemId, itemPrototype),
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                quickBuild,
                forceEmptyHand,
                sendRemoteHint,
                workflowEntryId);
        return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitPlacementJob(player, job);
    }

    /**
     * Tick 处理器，从排队的批处理作业中处理最多 {@link #BUILD_BATCH_MAX_BLOCKS_PER_TICK}
     * 个方块。快速建造作业使用预解析的状态计划快速路径；
     * 其他所有作业走交互式单放置路径。
     * 当一个完整作业完成时保存并刷新会话。
     */
    public static PlacementTaskState snapshotDetachedState(
            PlaceBatchJob job, net.minecraft.core.RegistryAccess registryAccess) {
        if (job == null || registryAccess == null) throw new IllegalArgumentException("job/registryAccess 不能为空");
        CompoundTag definition = job.toNbt(registryAccess);
        definition.remove(PlaceBatchJob.NBT_INDEX);
        return new PlacementTaskState(
                definition,
                job.workflowEntryId,
                job.totalCount(),
                job.index,
                job.placedPositions.size(),
                job.skippedWhileProcessing,
                job.placedPositions);
    }

    /**
     * 从 TaskStore 的纯值状态恢复一个只读/单 slice 临时 Job。
     * 返回对象绝不能加入 Session 队列；它仅用于扫描或执行当前快照。
     */
    public static PlaceBatchJob restoreDetachedJob(
            PlacementTaskState state, net.minecraft.core.RegistryAccess registryAccess) {
        if (state == null || registryAccess == null) {
            throw new IllegalArgumentException("state/registryAccess 不能为空");
        }
        PlaceBatchJob job = PlaceBatchJob.fromNbt(state.definition(), registryAccess);
        if (job.totalCount() != state.totalUnits() || job.workflowEntryId != state.workflowEntryId()) {
            throw new IllegalArgumentException("detached placement definition 与 snapshot 身份不一致");
        }
        job.index = state.cursorUnits();
        job.placedPositions.addAll(state.placedPositions());
        job.skippedWhileProcessing = state.failedUnits();
        return job;
    }

    /**
     * 把玩家选择的恢复策略转换成新的纯值状态；本方法不读取或修改世界。
     */
    public static PlacementTaskState applyDetachedResumeStrategy(
            ServerPlayer player, PlacementTaskState state, int strategy) {
        if (player == null || state == null || (strategy != 0 && strategy != 1)) return null;
        return state.withResumePolicy(strategy == 0
                ? PlacementResumePolicy.SKIP_CONFLICTS
                : PlacementResumePolicy.OVERWRITE_CONFLICTS);
    }

    /**
     * 在一个主线程预算片内推进 detached placement。
     *
     * <p>本方法从纯值 definition 临时重建 PlaceBatchJob，仅借用 player/session 解析真实世界、
     * 物品与 Capability。临时 job 从不加入 Session 队列；所有跨 tick 权威状态都通过返回的
     * PlacementTaskState 交回 TaskStore。</p>
     */
    public static PlacementSliceResult tickDetachedPlacementSlice(
            ServerPlayer player, RtsStorageSession session, PlacementTaskState state,
            int maxBlocks, long deadlineNanos) {
        if (player == null || session == null || state == null) {
            throw new IllegalArgumentException("player/session/state 不能为空");
        }
        PlaceBatchJob job = restoreDetachedJob(state, player.registryAccess());
        Block expectedBlock = expectedPlacementBlock(job);
        List<BlockPos> overwriteDropPositions = new ArrayList<>();

        int beforeCursor = job.index;
        int beforeSucceeded = job.placedPositions.size();
        int beforeFailed = job.skippedWhileProcessing;
        int limit = Math.max(0, Math.min(Config.buildBatchBlocksPerTick(), maxBlocks));
        int processed = 0;
        PlacementSliceResult.Outcome outcome = job.hasNext()
                ? PlacementSliceResult.Outcome.CONTINUE : PlacementSliceResult.Outcome.COMPLETE;

        while (processed < limit && System.nanoTime() < deadlineNanos && job.hasNext()) {
            BlockPos clickedPos = job.next();
            if (state.resumePolicy() != PlacementResumePolicy.DEFAULT && expectedBlock != null) {
                BlockPos targetPos = job.quickBuild()
                        ? clickedPos
                        : RtsPlacementExecutor.placementTargetPos(
                                player.level(), clickedPos, job.face());
                if (!player.level().hasChunkAt(targetPos)) {
                    job.unconsumeLast();
                    break;
                }
                BlockState targetState = player.level().getBlockState(targetPos);
                boolean alreadyExpected = targetState.getBlock() == expectedBlock;
                boolean conflict = !alreadyExpected && !targetState.isAir() && !targetState.canBeReplaced();
                if (alreadyExpected
                        || (conflict && state.resumePolicy() == PlacementResumePolicy.SKIP_CONFLICTS)) {
                    job.skippedWhileProcessing++;
                    processed++;
                    continue;
                }
                if (conflict && state.resumePolicy() == PlacementResumePolicy.OVERWRITE_CONFLICTS
                        && !prepareOverwriteConflict(player, targetPos, targetState, overwriteDropPositions)) {
                    job.skippedWhileProcessing++;
                    processed++;
                    continue;
                }
            }
            boolean keepGoing = processOnePlacement(player, session, job, clickedPos);
            processed++;
            if (!keepGoing) {
                // 事务未获得资源时不消费 cursor；真实物品仍由原库存/Capability 或世界持有。
                job.unconsumeLast();
                outcome = PlacementSliceResult.Outcome.WAITING_RESOURCE;
                break;
            }
        }
        if (!overwriteDropPositions.isEmpty()) {
            // 覆盖产生的同步掉落也进入同一个轻量缓存，不另建持久任务或等待磁盘 ACK。
            com.rtsbuilding.rtsbuilding.server.service.mining.RtsDropAbsorber
                    .absorbMinedDropsBatch(player, session, overwriteDropPositions);
        }
        if (outcome != PlacementSliceResult.Outcome.WAITING_RESOURCE && !job.hasNext()) {
            outcome = PlacementSliceResult.Outcome.COMPLETE;
        }

        PlacementTaskState next = state.advance(
                job.index,
                job.placedPositions.size(),
                job.skippedWhileProcessing,
                job.placedPositions);
        return new PlacementSliceResult(
                next,
                processed,
                Math.max(0, job.index - beforeCursor),
                Math.max(0, job.placedPositions.size() - beforeSucceeded),
                Math.max(0, job.skippedWhileProcessing - beforeFailed),
                outcome);
    }

    private static Block expectedPlacementBlock(PlaceBatchJob job) {
        String itemId = job.itemId();
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(itemId);
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                || !(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id)
                instanceof net.minecraft.world.item.BlockItem blockItem)) return null;
        Block expectedBlock = blockItem.getBlock();
        return expectedBlock == Blocks.AIR ? null : expectedBlock;
    }

    /** 覆盖策略的单格世界事务；调用点已经受 TaskStore revision ACK 与 slice 预算保护。 */
    private static boolean prepareOverwriteConflict(
            ServerPlayer player, BlockPos pos, BlockState current, List<BlockPos> dropPositions) {
        var level = player.level();
        if (!RtsClaimProtectionService.canBreakBlock(player, pos, Direction.UP)) return false;

        List<ItemStack> drops = Block.getDrops(current, level, pos, level.getBlockEntity(pos));
        level.destroyBlock(pos, false);
        if (!current.requiresCorrectToolForDrops() || player.isCreative()) {
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    Block.popResource(level, pos, drop);
                }
            }
            if (!drops.isEmpty()) dropPositions.add(pos.immutable());
        } else {
            player.sendSystemMessage(
                    Component.translatable("message.rtsbuilding.placement.tool_required", current.getBlock().getName()),
                    true);
        }
        return true;
    }

    /**
     * detached 任务首次进入终态时写入一次历史，并把页面/工作流/存档副作用交给合并器。
     */
    public static void recordDetachedHistory(ServerPlayer player, PlacementTaskState state) {
        if (player == null || state == null) return;
        PlaceBatchJob definition = PlaceBatchJob.fromNbt(state.definition(), player.registryAccess());
        if (!state.placedPositions().isEmpty()) {
            ServerHistoryManager.recordPlacement(player, state.placedPositions(), definition.face());
        }
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markWorkflow(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());
    }

    private static boolean processOnePlacement(
            ServerPlayer player, RtsStorageSession session, PlaceBatchJob job, BlockPos clickedPos) {
        RtsPlacementQuickBuild.StatePlacementPlan statePlan = job.quickBuild()
                ? job.statePlacementPlan(player) : null;
        boolean keepGoing;
        if (statePlan != null) {
            BlockPos trackedPos = clickedPos;
            BlockState beforeState = player.level().getBlockState(trackedPos);
            keepGoing = RtsPlacementQuickBuild.placeStateBatchEntry(player, session, clickedPos, statePlan);
            if (keepGoing && (beforeState.isAir() || beforeState.canBeReplaced())
                    && !player.level().getBlockState(trackedPos).isAir()) {
                job.placedPositions.add(trackedPos);
            } else if (keepGoing) {
                job.skippedWhileProcessing++;
            }
            return keepGoing;
        }

        Vec3 hitLocation = new Vec3(
                clickedPos.getX() + job.hitOffsetX(),
                clickedPos.getY() + job.hitOffsetY(),
                clickedPos.getZ() + job.hitOffsetZ());
        BlockPos adjPos = clickedPos.relative(job.face());
        BlockState beforeClicked = player.level().getBlockState(clickedPos);
        BlockState beforeAdjacent = player.level().hasChunkAt(adjPos)
                ? player.level().getBlockState(adjPos) : null;
        keepGoing = RtsPlacementExecutor.placeSelectedInternal(
                player,
                session,
                clickedPos,
                job.face(),
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                job.rotateSteps(),
                job.forcePlace(),
                job.skipIfOccupied(),
                job.itemId(),
                job.itemPrototype(),
                job.rayOriginX(),
                job.rayOriginY(),
                job.rayOriginZ(),
                job.rayDirX(),
                job.rayDirY(),
                job.rayDirZ(),
                job.quickBuild(),
                job.forceEmptyHand(),
                false,
                job.sendRemoteHint());
        if (keepGoing) {
            BlockPos actualPos = RtsPlacementHelper.detectPlacedPos(
                    player.level(), clickedPos, beforeClicked, adjPos, beforeAdjacent);
            if (actualPos != null) job.placedPositions.add(actualPos);
            else job.skippedWhileProcessing++;
        }
        return keepGoing;
    }

    /**
     * 工作流消失时收拢已发生的放置副作用，避免直接移除队列后丢失历史与持久化刷新。
     */
    public static final class PlaceBatchJob {
        private final List<BlockPos> clickedPositions;
        private final Direction face;
        private final double hitOffsetX;
        private final double hitOffsetY;
        private final double hitOffsetZ;
        private final byte rotateSteps;
        private final boolean forcePlace;
        private final boolean skipIfOccupied;
        private final String itemId;
        private final ItemStack itemPrototype;
        private final double rayOriginX;
        private final double rayOriginY;
        private final double rayOriginZ;
        private final double rayDirX;
        private final double rayDirY;
        private final double rayDirZ;
        private final boolean quickBuild;
        private final boolean forceEmptyHand;
        private final boolean sendRemoteHint;
        /** The unique entry ID of the workflow entry associated with this job. */
        private final int workflowEntryId;
        private int index;
        private boolean statePlanResolved;
        private RtsPlacementQuickBuild.StatePlacementPlan statePlan;
        final List<BlockPos> placedPositions = new ArrayList<>();

        /**
         * 因方块已存在/检测不到放置位置而跳过的数量，
         * 在 job 完成时报告为 failedBlocks。
         */
        int skippedWhileProcessing;

        private PlaceBatchJob(List<BlockPos> clickedPositions, Direction face, double hitOffsetX, double hitOffsetY,
                double hitOffsetZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
                ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX,
                double rayDirY, double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint,
                int workflowEntryId) {
            this.clickedPositions = clickedPositions;
            this.face = face;
            this.hitOffsetX = hitOffsetX;
            this.hitOffsetY = hitOffsetY;
            this.hitOffsetZ = hitOffsetZ;
            this.rotateSteps = rotateSteps;
            this.forcePlace = forcePlace;
            this.skipIfOccupied = skipIfOccupied;
            this.itemId = itemId;
            this.itemPrototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
            this.rayOriginX = rayOriginX;
            this.rayOriginY = rayOriginY;
            this.rayOriginZ = rayOriginZ;
            this.rayDirX = rayDirX;
            this.rayDirY = rayDirY;
            this.rayDirZ = rayDirZ;
            this.quickBuild = quickBuild;
            this.forceEmptyHand = forceEmptyHand;
            this.sendRemoteHint = sendRemoteHint;
            this.workflowEntryId = workflowEntryId;
        }

        private boolean hasNext() {
            return this.index < this.clickedPositions.size();
        }

        public int remainingCount() {
            return this.clickedPositions.size() - this.index;
        }

        public int totalCount() {
            return this.clickedPositions.size();
        }

        public int successfulCount() {
            return this.placedPositions.size();
        }

        public int failedCount() {
            return this.skippedWhileProcessing;
        }

        private BlockPos next() {
            return this.clickedPositions.get(this.index++);
        }

        /**
         * 返回剩余（未处理）位置的不可变列表。
         */
        public List<BlockPos> remainingPositions() {
            return this.clickedPositions.subList(this.index, this.clickedPositions.size());
        }

        /** 记录一个已成功放置的位置。 */
        public void markPlaced(BlockPos pos) {
            if (pos != null) {
                this.placedPositions.add(pos);
            }
        }

        /** 放置失败时回退索引，下个 tick 重试同一位置 */
        void unconsumeLast() {
            if (this.index > 0) {
                this.index--;
            }
        }

        /** 跳过当前一个位置（用于冲突跳过或已手动放置跳过） */
        public void skipOne() {
            if (hasNext()) {
                this.index++;
            }
        }

        /** 返回当前处理到的索引位置 */
        public int getIndex() {
            return this.index;
        }

        /** 返回本 job 对应的工作流条目 ID (entry.id, 不可变) */
        public int workflowEntryId() {
            return this.workflowEntryId;
        }

        /** 返回所有点击位置列表（不可修改） */
        public List<BlockPos> clickedPositions() {
            return java.util.Collections.unmodifiableList(this.clickedPositions);
        }

        // ──────────────────────────────────────────────────────────
        //  NBT 序列化——用于会话持久化
        // ──────────────────────────────────────────────────────────

        private static final String NBT_POSITIONS = "positions";
        private static final String NBT_FACE = "face";
        private static final String NBT_HIT_OFFSET_X = "hitOffsetX";
        private static final String NBT_HIT_OFFSET_Y = "hitOffsetY";
        private static final String NBT_HIT_OFFSET_Z = "hitOffsetZ";
        private static final String NBT_ROTATE_STEPS = "rotateSteps";
        private static final String NBT_FORCE_PLACE = "forcePlace";
        private static final String NBT_SKIP_IF_OCCUPIED = "skipIfOccupied";
        private static final String NBT_ITEM_ID = "itemId";
        private static final String NBT_ITEM_PROTOTYPE = "itemPrototype";
        private static final String NBT_RAY_ORIGIN_X = "rayOriginX";
        private static final String NBT_RAY_ORIGIN_Y = "rayOriginY";
        private static final String NBT_RAY_ORIGIN_Z = "rayOriginZ";
        private static final String NBT_RAY_DIR_X = "rayDirX";
        private static final String NBT_RAY_DIR_Y = "rayDirY";
        private static final String NBT_RAY_DIR_Z = "rayDirZ";
        private static final String NBT_QUICK_BUILD = "quickBuild";
        private static final String NBT_FORCE_EMPTY_HAND = "forceEmptyHand";
        private static final String NBT_SEND_REMOTE_HINT = "sendRemoteHint";
        private static final String NBT_WORKFLOW_ENTRY_ID = "workflowEntryId";
        private static final String NBT_INDEX = "index";

        /**
         * 将此批处理作业序列化为 {@link CompoundTag} 用于持久化存储。
         */
        public CompoundTag toNbt(net.minecraft.core.RegistryAccess registryAccess) {
            CompoundTag tag = new CompoundTag();
            long[] posArray = new long[clickedPositions.size()];
            for (int i = 0; i < clickedPositions.size(); i++) {
                posArray[i] = clickedPositions.get(i).asLong();
            }
            tag.putLongArray(NBT_POSITIONS, posArray);
            tag.putByte(NBT_FACE, (byte) face.get3DDataValue());
            tag.putDouble(NBT_HIT_OFFSET_X, hitOffsetX);
            tag.putDouble(NBT_HIT_OFFSET_Y, hitOffsetY);
            tag.putDouble(NBT_HIT_OFFSET_Z, hitOffsetZ);
            tag.putByte(NBT_ROTATE_STEPS, rotateSteps);
            tag.putBoolean(NBT_FORCE_PLACE, forcePlace);
            tag.putBoolean(NBT_SKIP_IF_OCCUPIED, skipIfOccupied);
            tag.putString(NBT_ITEM_ID, itemId);
            if (!itemPrototype.isEmpty()) {
                tag.put(NBT_ITEM_PROTOTYPE, com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.save(
                        itemPrototype, registryAccess));
            }
            tag.putDouble(NBT_RAY_ORIGIN_X, rayOriginX);
            tag.putDouble(NBT_RAY_ORIGIN_Y, rayOriginY);
            tag.putDouble(NBT_RAY_ORIGIN_Z, rayOriginZ);
            tag.putDouble(NBT_RAY_DIR_X, rayDirX);
            tag.putDouble(NBT_RAY_DIR_Y, rayDirY);
            tag.putDouble(NBT_RAY_DIR_Z, rayDirZ);
            tag.putBoolean(NBT_QUICK_BUILD, quickBuild);
            tag.putBoolean(NBT_FORCE_EMPTY_HAND, forceEmptyHand);
            tag.putBoolean(NBT_SEND_REMOTE_HINT, sendRemoteHint);
            tag.putInt(NBT_WORKFLOW_ENTRY_ID, workflowEntryId);
            tag.putInt(NBT_INDEX, index);
            return tag;
        }

        /**
         * 从 {@link CompoundTag} 反序列化 {@link PlaceBatchJob}。
         */
        public static PlaceBatchJob fromNbt(CompoundTag tag, net.minecraft.core.RegistryAccess registryAccess) {
            long[] posArray = tag.getLongArray(NBT_POSITIONS).orElseGet(() -> new long[0]);
            List<BlockPos> positions = new ArrayList<>(posArray.length);
            for (long l : posArray) {
                positions.add(BlockPos.of(l));
            }
            Direction face = Direction.from3DDataValue(tag.getByteOr(NBT_FACE, (byte) 0));
            double hitOffsetX = tag.getDoubleOr(NBT_HIT_OFFSET_X, 0.0D);
            double hitOffsetY = tag.getDoubleOr(NBT_HIT_OFFSET_Y, 0.0D);
            double hitOffsetZ = tag.getDoubleOr(NBT_HIT_OFFSET_Z, 0.0D);
            byte rotateSteps = tag.getByteOr(NBT_ROTATE_STEPS, (byte) 0);
            boolean forcePlace = tag.getBooleanOr(NBT_FORCE_PLACE, false);
            boolean skipIfOccupied = tag.getBooleanOr(NBT_SKIP_IF_OCCUPIED, false);
            String itemId = tag.getStringOr(NBT_ITEM_ID, "");
            ItemStack itemPrototype = ItemStack.EMPTY;
            if (tag.contains(NBT_ITEM_PROTOTYPE)) {
                itemPrototype = com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.load(
                        tag.getCompoundOrEmpty(NBT_ITEM_PROTOTYPE), registryAccess);
            }
            double rayOriginX = tag.getDoubleOr(NBT_RAY_ORIGIN_X, 0.0D);
            double rayOriginY = tag.getDoubleOr(NBT_RAY_ORIGIN_Y, 0.0D);
            double rayOriginZ = tag.getDoubleOr(NBT_RAY_ORIGIN_Z, 0.0D);
            double rayDirX = tag.getDoubleOr(NBT_RAY_DIR_X, 0.0D);
            double rayDirY = tag.getDoubleOr(NBT_RAY_DIR_Y, 0.0D);
            double rayDirZ = tag.getDoubleOr(NBT_RAY_DIR_Z, 0.0D);
            boolean quickBuild = tag.getBooleanOr(NBT_QUICK_BUILD, false);
            boolean forceEmptyHand = tag.getBooleanOr(NBT_FORCE_EMPTY_HAND, false);
            boolean sendRemoteHint = tag.getBooleanOr(NBT_SEND_REMOTE_HINT, false);
            int workflowEntryId = tag.getIntOr(NBT_WORKFLOW_ENTRY_ID, 0);
            int index = tag.getIntOr(NBT_INDEX, 0);

            PlaceBatchJob job = new PlaceBatchJob(
                    positions, face, hitOffsetX, hitOffsetY, hitOffsetZ,
                    rotateSteps, forcePlace, skipIfOccupied, itemId, itemPrototype,
                    rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ,
                    quickBuild, forceEmptyHand, sendRemoteHint, workflowEntryId);
            job.index = index;
            return job;
        }

        BlockPos templatePosition() {
            return this.clickedPositions.isEmpty() ? null : this.clickedPositions.get(0);
        }

        BlockHitResult templateHit(BlockPos templatePos) {
            return new BlockHitResult(
                    new Vec3(
                            templatePos.getX() + this.hitOffsetX,
                            templatePos.getY() + this.hitOffsetY,
                            templatePos.getZ() + this.hitOffsetZ),
                    this.face,
                    templatePos,
                    false);
        }

        private RtsPlacementQuickBuild.StatePlacementPlan statePlacementPlan(ServerPlayer player) {
            if (!this.statePlanResolved) {
                this.statePlan = RtsPlacementQuickBuild.resolveStatePlacementPlan(player, this);
                this.statePlanResolved = true;
            }
            return this.statePlan;
        }

        public Direction face() {
            return this.face;
        }

        public double hitOffsetX() {
            return this.hitOffsetX;
        }

        public double hitOffsetY() {
            return this.hitOffsetY;
        }

        public double hitOffsetZ() {
            return this.hitOffsetZ;
        }

        public byte rotateSteps() {
            return this.rotateSteps;
        }

        public boolean forcePlace() {
            return this.forcePlace;
        }

        public boolean skipIfOccupied() {
            return this.skipIfOccupied;
        }

        public String itemId() {
            return this.itemId;
        }

        public ItemStack itemPrototype() {
            return this.itemPrototype.copy();
        }

        public double rayOriginX() {
            return this.rayOriginX;
        }

        public double rayOriginY() {
            return this.rayOriginY;
        }

        public double rayOriginZ() {
            return this.rayOriginZ;
        }

        public double rayDirX() {
            return this.rayDirX;
        }

        public double rayDirY() {
            return this.rayDirY;
        }

        public double rayDirZ() {
            return this.rayDirZ;
        }

        public boolean quickBuild() {
            return this.quickBuild;
        }

        public boolean forceEmptyHand() {
            return this.forceEmptyHand;
        }

        private boolean sendRemoteHint() {
            return this.sendRemoteHint;
        }
    }
}
