package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
     * Entry point for single-item remote placement. Delegates to
     * {@link #enqueuePlaceBatch} with a single-position job.
     */
    public static void placeSelected(ServerPlayer player, RtsStorageSession session, BlockPos clickedPos, Direction face,
                                     double hitX, double hitY, double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                                     String itemId, ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                                     double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild) {
        double hitOffsetX = clickedPos == null ? 0.5D : hitX - clickedPos.getX();
        double hitOffsetY = clickedPos == null ? 0.5D : hitY - clickedPos.getY();
        double hitOffsetZ = clickedPos == null ? 0.5D : hitZ - clickedPos.getZ();
        enqueuePlaceBatch(
                player,
                session,
                clickedPos == null ? List.of() : List.of(clickedPos),
                face,
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId,
                itemPrototype,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                quickBuild,
                false,
                true);
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
    public static void enqueuePlaceBatch(ServerPlayer player, RtsStorageSession session, List<BlockPos> clickedPositions,
            Direction face, double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX, double rayDirY,
            double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint) {
        if (!RtsProgressionManager.canUse(
                player, RtsFeature.REMOTE_PLACE)) {
            return;
        }
        if (session == null || clickedPositions == null || clickedPositions.isEmpty() || face == null) {
            return;
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
            return;
        }
        // Quick-build jobs (shape builds) are limited to BUILD_BATCH_MAX_QUEUED_JOBS;
        // reject when full. Single-block placements bypass this limit.
        if (quickBuild && session.placement.placeBatchJobs.size() >= BUILD_BATCH_MAX_QUEUED_JOBS) {
            return;
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
                sendRemoteHint));
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
        for (PlaceBatchJob j : session.placement.placeBatchJobs) {
            totalBlocks += j.totalCount();
        }
        int remaining = Math.min(BUILD_BATCH_MAX_BLOCKS_PER_TICK, Math.max(1, totalBlocks / 10));
        boolean finishedJob = false;
        PlaceBatchJob completedJobRef = null;
        while (remaining > 0 && !session.placement.placeBatchJobs.isEmpty()) {
            PlaceBatchJob job = session.placement.placeBatchJobs.peekFirst();
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
                    completedJobRef = job;
                    session.placement.placeBatchJobs.removeFirst();
                    finishedJob = true;
                    break;
                }
            }
            if (!session.placement.placeBatchJobs.isEmpty() && session.placement.placeBatchJobs.peekFirst() == job && !job.hasNext()) {
                completedJobRef = job;
                session.placement.placeBatchJobs.removeFirst();
                finishedJob = true;
            }
        }
        if (finishedJob && completedJobRef != null && !completedJobRef.placedPositions.isEmpty()) {
            ServerHistoryManager.recordPlacement(player, completedJobRef.placedPositions, completedJobRef.face());
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            RtsSessionService.saveToPlayerNbt(player, session);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
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
        private int index;
        private boolean statePlanResolved;
        private RtsPlacementQuickBuild.StatePlacementPlan statePlan;
        final List<BlockPos> placedPositions = new ArrayList<>();

        private PlaceBatchJob(List<BlockPos> clickedPositions, Direction face, double hitOffsetX, double hitOffsetY,
                double hitOffsetZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
                ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX,
                double rayDirY, double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint) {
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

        Direction face() {
            return this.face;
        }

        double hitOffsetX() {
            return this.hitOffsetX;
        }

        double hitOffsetY() {
            return this.hitOffsetY;
        }

        double hitOffsetZ() {
            return this.hitOffsetZ;
        }

        byte rotateSteps() {
            return this.rotateSteps;
        }

        private boolean forcePlace() {
            return this.forcePlace;
        }

        private boolean skipIfOccupied() {
            return this.skipIfOccupied;
        }

        String itemId() {
            return this.itemId;
        }

        ItemStack itemPrototype() {
            return this.itemPrototype.copy();
        }

        private double rayOriginX() {
            return this.rayOriginX;
        }

        private double rayOriginY() {
            return this.rayOriginY;
        }

        private double rayOriginZ() {
            return this.rayOriginZ;
        }

        private double rayDirX() {
            return this.rayDirX;
        }

        private double rayDirY() {
            return this.rayDirY;
        }

        private double rayDirZ() {
            return this.rayDirZ;
        }

        boolean quickBuild() {
            return this.quickBuild;
        }

        private boolean forceEmptyHand() {
            return this.forceEmptyHand;
        }

        private boolean sendRemoteHint() {
            return this.sendRemoteHint;
        }
    }
}

