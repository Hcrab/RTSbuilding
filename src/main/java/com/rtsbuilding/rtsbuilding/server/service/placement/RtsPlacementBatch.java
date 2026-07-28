package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.placement.PlacementStatePreset;
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
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

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
    public static boolean enqueuePlaceBatch(EntityPlayerMP player, RtsStorageSession session, List<BlockPos> clickedPositions,
            EnumFacing face, double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps, String statePreset,
            boolean forcePlace, boolean skipIfOccupied, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX, double rayDirY,
            double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint,
            int workflowEntryId) {
        return enqueuePlaceBatch(player, session, clickedPositions, face,
                hitOffsetX, hitOffsetY, hitOffsetZ, rotateSteps, statePreset,
                forcePlace, skipIfOccupied, false, itemId, itemPrototype,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ,
                quickBuild, forceEmptyHand, sendRemoteHint, workflowEntryId);
    }

    public static boolean enqueuePlaceBatch(EntityPlayerMP player, RtsStorageSession session, List<BlockPos> clickedPositions,
            EnumFacing face, double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps, String statePreset,
            boolean forcePlace, boolean skipIfOccupied, boolean overwriteExisting, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX, double rayDirY,
            double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint,
            int workflowEntryId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            if (sendRemoteHint && player != null) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "message.rtsbuilding.quick_build.remote_place_locked"), true);
            }
            return false;
        }
        if (session == null || clickedPositions == null || clickedPositions.isEmpty() || face == null) {
            return false;
        }
        if (quickBuild && (itemId == null || itemId.trim().isEmpty())) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "message.rtsbuilding.quick_build.select_material"), true);
            return false;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<BlockPos> positions = new ArrayList<>(Math.min(clickedPositions.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
        for (BlockPos pos : clickedPositions) {
            if (pos == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            positions.add(pos.toImmutable());
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
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetX, face, EnumFacing.Axis.X),
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetY, face, EnumFacing.Axis.Y),
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetZ, face, EnumFacing.Axis.Z),
                rotateSteps,
                PlacementStatePreset.sanitize(statePreset),
                forcePlace,
                skipIfOccupied,
                overwriteExisting && quickBuild && player.capabilities.isCreativeMode,
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
            PlaceBatchJob job, Object registryAccess) {
        if (job == null) throw new IllegalArgumentException("job 不能为空");
        NBTTagCompound definition = job.toNbt(registryAccess);
        definition.removeTag(PlaceBatchJob.NBT_INDEX);
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
            PlacementTaskState state, Object registryAccess) {
        if (state == null) {
            throw new IllegalArgumentException("state 不能为空");
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
            EntityPlayerMP player, PlacementTaskState state, int strategy) {
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
            EntityPlayerMP player, RtsStorageSession session, PlacementTaskState state,
            int maxBlocks, long deadlineNanos) {
        if (player == null || session == null || state == null) {
            throw new IllegalArgumentException("player/session/state 不能为空");
        }
        PlaceBatchJob job = restoreDetachedJob(state, null);
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
                                player.getServerWorld(), clickedPos, job.face());
                if (!player.getServerWorld().isBlockLoaded(targetPos)) {
                    job.unconsumeLast();
                    break;
                }
                IBlockState targetState = player.getServerWorld().getBlockState(targetPos);
                boolean alreadyExpected = targetState.getBlock() == expectedBlock;
                boolean conflict = !alreadyExpected && targetState.getBlock() != Blocks.AIR
                        && !targetState.getMaterial().isReplaceable();
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
        ResourceLocation id;
        try { id = new ResourceLocation(itemId); } catch (RuntimeException invalid) { return null; }
        Item registered = Item.REGISTRY.getObject(id);
        if (!(registered instanceof ItemBlock)) return null;
        ItemBlock blockItem = (ItemBlock) registered;
        Block expectedBlock = blockItem.getBlock();
        return expectedBlock == Blocks.AIR ? null : expectedBlock;
    }

    /** 覆盖策略的单格世界事务；调用点已经受 TaskStore revision ACK 与 slice 预算保护。 */
    private static boolean prepareOverwriteConflict(
            EntityPlayerMP player, BlockPos pos, IBlockState current, List<BlockPos> dropPositions) {
        WorldServer level = player.getServerWorld();
        if (!RtsClaimProtectionService.canBreakBlock(player, pos, EnumFacing.UP)) return false;

        List<ItemStack> drops = current.getBlock().getDrops(level, pos, current, 0);
        level.setBlockToAir(pos);
        if (current.getBlock().canHarvestBlock(level, pos, player) || player.capabilities.isCreativeMode) {
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    Block.spawnAsEntity(level, pos, drop);
                }
            }
            if (!drops.isEmpty()) dropPositions.add(pos.toImmutable());
        } else {
            player.sendStatusMessage(new TextComponentTranslation(
                    "message.rtsbuilding.placement.tool_required", current.getBlock().getLocalizedName()), true);
        }
        return true;
    }

    /**
     * detached 任务首次进入终态时写入一次历史，并把页面/工作流/存档副作用交给合并器。
     */
    public static void recordDetachedHistory(EntityPlayerMP player, PlacementTaskState state) {
        if (player == null || state == null) return;
        PlaceBatchJob definition = PlaceBatchJob.fromNbt(state.definition(), null);
        if (!state.placedPositions().isEmpty()) {
            ServerHistoryManager.recordPlacement(player, state.placedPositions(), definition.face());
        }
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUniqueID(), player.dimension);
        RtsEffectAccumulator.INSTANCE.markWorkflow(player.getUniqueID(), player.dimension);
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUniqueID(), player.dimension);
    }

    private static boolean processOnePlacement(
            EntityPlayerMP player, RtsStorageSession session, PlaceBatchJob job, BlockPos clickedPos) {
        RtsPlacementQuickBuild.StatePlacementPlan statePlan = job.quickBuild()
                ? job.statePlacementPlan(player) : null;
        boolean keepGoing;
        if (statePlan != null) {
            BlockPos trackedPos = clickedPos;
            IBlockState beforeState = player.getServerWorld().getBlockState(trackedPos);
            boolean creativeOverwrite = job.overwriteExisting() && player.capabilities.isCreativeMode;
            keepGoing = RtsPlacementQuickBuild.placeStateBatchEntry(
                    player, session, clickedPos, statePlan, creativeOverwrite);
            if (keepGoing && (creativeOverwrite || beforeState.getBlock() == Blocks.AIR
                    || beforeState.getMaterial().isReplaceable())
                    && player.getServerWorld().getBlockState(trackedPos).getBlock() != Blocks.AIR) {
                job.placedPositions.add(trackedPos);
            } else if (keepGoing) {
                job.skippedWhileProcessing++;
            }
            return keepGoing;
        }

        Vec3d hitLocation = new Vec3d(
                clickedPos.getX() + job.hitOffsetX(),
                clickedPos.getY() + job.hitOffsetY(),
                clickedPos.getZ() + job.hitOffsetZ());
        BlockPos adjPos = clickedPos.offset(job.face());
        IBlockState beforeClicked = player.getServerWorld().getBlockState(clickedPos);
        IBlockState beforeAdjacent = player.getServerWorld().isBlockLoaded(adjPos)
                ? player.getServerWorld().getBlockState(adjPos) : null;
        keepGoing = RtsPlacementExecutor.placeSelectedInternal(
                player,
                session,
                clickedPos,
                job.face(),
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                job.rotateSteps(),
                job.statePreset(),
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
                    player.getServerWorld(), clickedPos, beforeClicked, adjPos, beforeAdjacent);
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
        private final EnumFacing face;
        private final double hitOffsetX;
        private final double hitOffsetY;
        private final double hitOffsetZ;
        private final byte rotateSteps;
        private final String statePreset;
        private final boolean forcePlace;
        private final boolean skipIfOccupied;
        private final boolean overwriteExisting;
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

        private PlaceBatchJob(List<BlockPos> clickedPositions, EnumFacing face, double hitOffsetX, double hitOffsetY,
                double hitOffsetZ, byte rotateSteps, String statePreset, boolean forcePlace, boolean skipIfOccupied,
                boolean overwriteExisting, String itemId,
                ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX,
                double rayDirY, double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint,
                int workflowEntryId) {
            this.clickedPositions = clickedPositions;
            this.face = face;
            this.hitOffsetX = hitOffsetX;
            this.hitOffsetY = hitOffsetY;
            this.hitOffsetZ = hitOffsetZ;
            this.rotateSteps = rotateSteps;
            this.statePreset = PlacementStatePreset.sanitize(statePreset);
            this.forcePlace = forcePlace;
            this.skipIfOccupied = skipIfOccupied;
            this.overwriteExisting = overwriteExisting;
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
        private static final String NBT_STATE_PRESET = "statePreset";
        private static final String NBT_FORCE_PLACE = "forcePlace";
        private static final String NBT_SKIP_IF_OCCUPIED = "skipIfOccupied";
        private static final String NBT_OVERWRITE_EXISTING = "overwriteExisting";
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
        public NBTTagCompound toNbt(Object ignoredRegistryAccess) {
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagList positions = new NBTTagList();
            for (BlockPos pos : clickedPositions) positions.appendTag(new NBTTagLong(pos.toLong()));
            tag.setTag(NBT_POSITIONS, positions);
            tag.setByte(NBT_FACE, (byte) face.getIndex());
            tag.setDouble(NBT_HIT_OFFSET_X, hitOffsetX);
            tag.setDouble(NBT_HIT_OFFSET_Y, hitOffsetY);
            tag.setDouble(NBT_HIT_OFFSET_Z, hitOffsetZ);
            tag.setByte(NBT_ROTATE_STEPS, rotateSteps);
            tag.setString(NBT_STATE_PRESET, statePreset);
            tag.setBoolean(NBT_FORCE_PLACE, forcePlace);
            tag.setBoolean(NBT_SKIP_IF_OCCUPIED, skipIfOccupied);
            tag.setBoolean(NBT_OVERWRITE_EXISTING, overwriteExisting);
            tag.setString(NBT_ITEM_ID, itemId);
            if (!itemPrototype.isEmpty()) {
                tag.setTag(NBT_ITEM_PROTOTYPE, itemPrototype.writeToNBT(new NBTTagCompound()));
            }
            tag.setDouble(NBT_RAY_ORIGIN_X, rayOriginX);
            tag.setDouble(NBT_RAY_ORIGIN_Y, rayOriginY);
            tag.setDouble(NBT_RAY_ORIGIN_Z, rayOriginZ);
            tag.setDouble(NBT_RAY_DIR_X, rayDirX);
            tag.setDouble(NBT_RAY_DIR_Y, rayDirY);
            tag.setDouble(NBT_RAY_DIR_Z, rayDirZ);
            tag.setBoolean(NBT_QUICK_BUILD, quickBuild);
            tag.setBoolean(NBT_FORCE_EMPTY_HAND, forceEmptyHand);
            tag.setBoolean(NBT_SEND_REMOTE_HINT, sendRemoteHint);
            tag.setInteger(NBT_WORKFLOW_ENTRY_ID, workflowEntryId);
            tag.setInteger(NBT_INDEX, index);
            return tag;
        }

        public NBTTagCompound toNbt() { return toNbt(null); }

        /**
         * 从 {@link CompoundTag} 反序列化 {@link PlaceBatchJob}。
         */
        public static PlaceBatchJob fromNbt(NBTTagCompound tag, Object ignoredRegistryAccess) {
            NBTTagList encodedPositions = tag.getTagList(NBT_POSITIONS, Constants.NBT.TAG_LONG);
            List<BlockPos> positions = new ArrayList<BlockPos>(encodedPositions.tagCount());
            for (int i = 0; i < encodedPositions.tagCount(); i++) {
                positions.add(BlockPos.fromLong(((NBTTagLong) encodedPositions.get(i)).getLong()));
            }
            EnumFacing face = EnumFacing.byIndex(tag.getByte(NBT_FACE));
            double hitOffsetX = tag.getDouble(NBT_HIT_OFFSET_X);
            double hitOffsetY = tag.getDouble(NBT_HIT_OFFSET_Y);
            double hitOffsetZ = tag.getDouble(NBT_HIT_OFFSET_Z);
            byte rotateSteps = tag.getByte(NBT_ROTATE_STEPS);
            String statePreset = tag.getString(NBT_STATE_PRESET);
            boolean forcePlace = tag.getBoolean(NBT_FORCE_PLACE);
            boolean skipIfOccupied = tag.getBoolean(NBT_SKIP_IF_OCCUPIED);
            boolean overwriteExisting = tag.getBoolean(NBT_OVERWRITE_EXISTING);
            String itemId = tag.getString(NBT_ITEM_ID);
            ItemStack itemPrototype = ItemStack.EMPTY;
            if (tag.hasKey(NBT_ITEM_PROTOTYPE, Constants.NBT.TAG_COMPOUND)) {
                itemPrototype = new ItemStack(tag.getCompoundTag(NBT_ITEM_PROTOTYPE));
            }
            double rayOriginX = tag.getDouble(NBT_RAY_ORIGIN_X);
            double rayOriginY = tag.getDouble(NBT_RAY_ORIGIN_Y);
            double rayOriginZ = tag.getDouble(NBT_RAY_ORIGIN_Z);
            double rayDirX = tag.getDouble(NBT_RAY_DIR_X);
            double rayDirY = tag.getDouble(NBT_RAY_DIR_Y);
            double rayDirZ = tag.getDouble(NBT_RAY_DIR_Z);
            boolean quickBuild = tag.getBoolean(NBT_QUICK_BUILD);
            boolean forceEmptyHand = tag.getBoolean(NBT_FORCE_EMPTY_HAND);
            boolean sendRemoteHint = tag.getBoolean(NBT_SEND_REMOTE_HINT);
            int workflowEntryId = tag.getInteger(NBT_WORKFLOW_ENTRY_ID);
            int index = tag.getInteger(NBT_INDEX);

            PlaceBatchJob job = new PlaceBatchJob(
                    positions, face, hitOffsetX, hitOffsetY, hitOffsetZ,
                    rotateSteps, statePreset, forcePlace, skipIfOccupied, overwriteExisting, itemId, itemPrototype,
                    rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ,
                    quickBuild, forceEmptyHand, sendRemoteHint, workflowEntryId);
            job.index = index;
            return job;
        }

        public static PlaceBatchJob fromNbt(NBTTagCompound tag) { return fromNbt(tag, null); }

        BlockPos templatePosition() {
            return this.clickedPositions.isEmpty() ? null : this.clickedPositions.get(0);
        }

        RayTraceResult templateHit(BlockPos templatePos) {
            return new RayTraceResult(
                    new Vec3d(
                            templatePos.getX() + this.hitOffsetX,
                            templatePos.getY() + this.hitOffsetY,
                            templatePos.getZ() + this.hitOffsetZ),
                    this.face,
                    templatePos);
        }

        private RtsPlacementQuickBuild.StatePlacementPlan statePlacementPlan(EntityPlayerMP player) {
            if (!this.statePlanResolved) {
                this.statePlan = RtsPlacementQuickBuild.resolveStatePlacementPlan(player, this);
                this.statePlanResolved = true;
            }
            return this.statePlan;
        }

        public EnumFacing face() {
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

        public String statePreset() {
            return this.statePreset;
        }

        public boolean forcePlace() {
            return this.forcePlace;
        }

        public boolean skipIfOccupied() {
            return this.skipIfOccupied;
        }

        public boolean overwriteExisting() {
            return this.overwriteExisting;
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
