package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch job queuing and tick processing for RTS remote block placement.
 *
 * <p>This helper owns the batch-job lifecycle: queueing placement requests,
 * throttling per-tick block-processing via {@link #tickPlaceBatchJobs}, and
 * the {@link PlaceBatchJob} data holder. It deliberately does not execute
 * individual placement logic, resolve quick-build state plans, play sounds,
 * or extract items — those responsibilities live in their dedicated helpers.
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
        if (!RtsProgressionManager.canUse(
                player, RtsFeature.REMOTE_PLACE)) {
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
        if (quickBuild && session.placement.placeBatchJobs.size() >= BUILD_BATCH_MAX_QUEUED_JOBS) {
            return false;
        }
        session.placement.placeBatchJobs.addLast(new PlaceBatchJob(
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
                workflowEntryId));
        return true;
    }

    /**
     * Tick handler that processes up to {@link #BUILD_BATCH_MAX_BLOCKS_PER_TICK}
     * blocks from queued batch jobs. Quick-build jobs use the pre-resolved
     * state plan fast path; all others fall through to the interactive single
     * placement path. Saves and refreshes the session when a full job
     * completes.
     */
    public static void tickPlaceBatchJobs(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        int totalBlocks = 0;
        int pausedJobsSkipped = 0; // 连续暂停计数，防止无限循环
        for (PlaceBatchJob j : session.placement.placeBatchJobs) {
            totalBlocks += j.totalCount();
        }
        int remaining = Math.min(BUILD_BATCH_MAX_BLOCKS_PER_TICK, Math.max(1, totalBlocks / 10));
        // 记录此 tick 开始前每个 job 的已放置数，用于按 job 独立更新工作流进度
        java.util.Map<Integer, Integer> placedBeforeTick = new java.util.HashMap<>();
        // 收集此 tick 中完成的所有 job，确保每个 job 的工作流都被 complete
        java.util.List<PlaceBatchJob> fullyCompletedJobs = new java.util.ArrayList<>();
        // 先记录每个 job 的 tick 前已放置数
        for (PlaceBatchJob j : session.placement.placeBatchJobs) {
            placedBeforeTick.put(j.workflowEntryId(), j.placedPositions.size());
        }

        while (remaining > 0 && !session.placement.placeBatchJobs.isEmpty()) {
            PlaceBatchJob job = session.placement.placeBatchJobs.peekFirst();
            // Per-entry pause valve: 检查工作流是否存在或已暂停
            var tokenOpt = RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId());
            if (tokenOpt.isEmpty()) {
                // 工作流已被关闭（删除），从队列中移除该 job
                session.placement.placeBatchJobs.removeFirst();
                pausedJobsSkipped = 0;
                continue;
            }
            if (tokenOpt.get().isPaused()) {
                // 暂停：将 job 移到队尾，跳过此 tick
                session.placement.placeBatchJobs.removeFirst();
                session.placement.placeBatchJobs.addLast(job);
                pausedJobsSkipped++;
                if (pausedJobsSkipped >= session.placement.placeBatchJobs.size()) {
                    break; // 所有剩余 job 都已暂停
                }
                continue;
            }
            pausedJobsSkipped = 0;
            boolean madeProgress = false;
            while (remaining > 0 && job.hasNext()) {
                BlockPos clickedPos = job.next();
                RtsPlacementQuickBuild.StatePlacementPlan statePlan = job.quickBuild()
                        ? job.statePlacementPlan(player) : null;
                boolean keepGoing;
                if (statePlan != null) {
                    // 快速建造路径：记录放置前的状态，用于批撤回
                    BlockPos trackedPos = clickedPos;
                    BlockState beforeState = player.serverLevel().getBlockState(trackedPos);
                    keepGoing = RtsPlacementQuickBuild.placeStateBatchEntry(player, session, clickedPos, statePlan);
                    // 如果方块状态发生了变化（空气→方块），说明放置成功
                    if (keepGoing && (beforeState.isAir() || beforeState.canBeReplaced())
                            && !player.serverLevel().getBlockState(trackedPos).isAir()) {
                        job.placedPositions.add(trackedPos);
                    }
                } else {
                    Vec3 hitLocation = new Vec3(
                            clickedPos.getX() + job.hitOffsetX(),
                            clickedPos.getY() + job.hitOffsetY(),
                            clickedPos.getZ() + job.hitOffsetZ());
                    // 记录放置前状态，用于检测实际放置位置
                    BlockPos adjPos = clickedPos.relative(job.face());
                    BlockState beforeClicked = player.serverLevel().getBlockState(clickedPos);
                    BlockState beforeAdjacent = player.serverLevel().hasChunkAt(adjPos)
                            ? player.serverLevel().getBlockState(adjPos) : null;
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
                    // 检测实际放置位置（可能是 clickedPos 或 adjacentPos）
                    if (keepGoing) {
                        BlockPos actualPos = RtsPlacementHelper.detectPlacedPos(
                                player.serverLevel(), clickedPos, beforeClicked, adjPos, beforeAdjacent);
                        if (actualPos != null) {
                            job.placedPositions.add(actualPos);
                        }
                    }
                }
                remaining--;
                if (!keepGoing) {
                    // 放置失败（物品不足），回退索引保留位置，将 job 挂起到 pendingJobs
                    // 后续通过 resumePendingJob / submitPendingPlacement 唤醒
                    job.unconsumeLast();
                    remaining--;
                    session.placement.placeBatchJobs.removeFirst();
                    session.placement.pendingJobs.addLast(job);
                    madeProgress = false;
                    // 搁置当前工作流（通过 token 从 job 的 entryId 重建）
                    RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId()).ifPresent(token -> token.suspend());
                    break;
                }
                madeProgress = true;
            }
            if (!session.placement.placeBatchJobs.isEmpty() && session.placement.placeBatchJobs.peekFirst() == job && !job.hasNext()) {
                session.placement.placeBatchJobs.removeFirst();
                // 立刻处理此 job 的完成：记录历史、更新进度、释放工作流槽位
                fullyCompletedJobs.add(job);
            }
        }

        // 处理所有此 tick 内完成的 job
        for (PlaceBatchJob completedJob : fullyCompletedJobs) {
            int before = placedBeforeTick.getOrDefault(completedJob.workflowEntryId(), 0);
            int delta = completedJob.placedPositions.size() - before;
            if (!completedJob.placedPositions.isEmpty()) {
                ServerHistoryManager.recordPlacement(player, completedJob.placedPositions, completedJob.face());
            }
            if (delta > 0) {
                RtsWorkflowEngine.getInstance().from(player, completedJob.workflowEntryId()).ifPresent(token -> token.updateProgress(delta, null));
            }
            // 每个job独立complete自己的workflow entry，避免已完成job的entry泄漏
            RtsWorkflowEngine.getInstance().from(player, completedJob.workflowEntryId()).ifPresent(token -> token.complete());
        }
        // 只要此 tick 有 job 完成，就刷新一次储存页面（合并刷新）
        if (!fullyCompletedJobs.isEmpty()) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
            ServiceRegistry.getInstance().page().requestPage(player, session.browser.page, session.browser.search,
                    session.browser.category, session.browser.sort, session.browser.ascending);
        }

        // 更新仍在活跃队列中的 job 的中途进度（尚未完成但此 tick 有放置进展）
        for (PlaceBatchJob j : session.placement.placeBatchJobs) {
            int before = placedBeforeTick.getOrDefault(j.workflowEntryId(), 0);
            int delta = j.placedPositions.size() - before;
            if (delta > 0) {
                RtsWorkflowEngine.getInstance().from(player, j.workflowEntryId()).ifPresent(token -> token.updateProgress(delta, null));
                // 中途进度：放置方块消耗了储存物品，触发页面刷新以保证GUI实时更新
                RtsStorageTickService.INSTANCE.forceRefresh(player);
                session.transfer.pageDataVersion.incrementAndGet();
                ServiceRegistry.getInstance().page().requestPage(player, session.browser.page, session.browser.search,
                        session.browser.category, session.browser.sort, session.browser.ascending);
            }
        }

        // 放置完成后扫描世界实际状态，刷新所有工作流进度（不依赖事件触发）
        RtsPendingPlacementService.refreshWorkflowProgress(player, session);
    }

    /**
     * A single batch placement job that holds the shared placement parameters
     * and an ordered list of target positions. Each job is processed by
     * {@link #tickPlaceBatchJobs} at a rate of up to
     * {@link #BUILD_BATCH_MAX_BLOCKS_PER_TICK} blocks per tick.
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

        int remainingCount() {
            return this.clickedPositions.size() - this.index;
        }

        int totalCount() {
            return this.clickedPositions.size();
        }

        private BlockPos next() {
            return this.clickedPositions.get(this.index++);
        }

        /**
         * Returns an immutable list of remaining (unprocessed) positions.
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
        //  NBT serialization — used for session persistence
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
         * Serialises this batch job to a {@link CompoundTag} for persistent storage.
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
                tag.put(NBT_ITEM_PROTOTYPE, itemPrototype.save(registryAccess));
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
         * Deserialises a {@link PlaceBatchJob} from a {@link CompoundTag}.
         */
        public static PlaceBatchJob fromNbt(CompoundTag tag, net.minecraft.core.RegistryAccess registryAccess) {
            long[] posArray = tag.getLongArray(NBT_POSITIONS);
            List<BlockPos> positions = new ArrayList<>(posArray.length);
            for (long l : posArray) {
                positions.add(BlockPos.of(l));
            }
            Direction face = Direction.from3DDataValue(tag.getByte(NBT_FACE));
            double hitOffsetX = tag.getDouble(NBT_HIT_OFFSET_X);
            double hitOffsetY = tag.getDouble(NBT_HIT_OFFSET_Y);
            double hitOffsetZ = tag.getDouble(NBT_HIT_OFFSET_Z);
            byte rotateSteps = tag.getByte(NBT_ROTATE_STEPS);
            boolean forcePlace = tag.getBoolean(NBT_FORCE_PLACE);
            boolean skipIfOccupied = tag.getBoolean(NBT_SKIP_IF_OCCUPIED);
            String itemId = tag.getString(NBT_ITEM_ID);
            ItemStack itemPrototype = ItemStack.EMPTY;
            if (tag.contains(NBT_ITEM_PROTOTYPE, Tag.TAG_COMPOUND)) {
                itemPrototype = ItemStack.parseOptional(registryAccess, tag.getCompound(NBT_ITEM_PROTOTYPE));
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
            int workflowEntryId = tag.getInt(NBT_WORKFLOW_ENTRY_ID);
            int index = tag.getInt(NBT_INDEX);

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
