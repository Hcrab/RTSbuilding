package com.rtsbuilding.rtsbuilding.server.service.fluids;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceFluidBatchPayload;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能放置批量流体放置处理器，集成工作流系统。
 */
public final class SmartPlaceFluidBatch {

    static final int FLUID_BATCH_MAX_BLOCKS_PER_TICK = 16;

    private SmartPlaceFluidBatch() {}

    public static void enqueuePlaceFluidBatch(ServerPlayer player, RtsStorageSession session,
            List<BlockPos> positions, String fluidId) {
        if (player == null || session == null || positions == null || positions.isEmpty()
                || fluidId == null || fluidId.isBlank()) {
            return;
        }
        List<BlockPos> sanitized = new ArrayList<>(Math.min(positions.size(),
                (int) C2SRtsPlaceFluidBatchPayload.MAX_POSITIONS));
        for (BlockPos pos : positions) {
            if (pos != null) {
                sanitized.add(pos.immutable());
                if (sanitized.size() >= C2SRtsPlaceFluidBatchPayload.MAX_POSITIONS) break;
            }
        }
        if (sanitized.isEmpty()) {
            return;
        }

        FluidBatchJob existing = getActiveJob(session);
        if (existing != null) {
            RtsWorkflowEngine.getInstance().deleteWorkflow(player, existing.workflowEntryId);
        }

        int entryId = RtsWorkflowEngine.getInstance()
                .start(player, RtsWorkflowType.SMART_PLACE_FLUID, RtsWorkflowPriority.NORMAL, sanitized.size())
                .map(token -> token.entryId())
                .orElse(-1);

        if (entryId < 0) {
            return;
        }

        session.sessionFlags.pendingFluidBatch = new FluidBatchJob(sanitized, fluidId, entryId);
    }

    public static int tickFluidBatchJobs(ServerPlayer player, RtsStorageSession session) {
        FluidBatchJob job = getActiveJob(session);
        if (job == null) return 0;

        var engine = RtsWorkflowEngine.getInstance();
        var tokenOpt = engine.from(player, job.workflowEntryId);
        if (tokenOpt.isEmpty()) {
            session.sessionFlags.pendingFluidBatch = null;
            return 0;
        }
        var token = tokenOpt.get();
        var progress = token.getProgress();
        if (progress == null) {
            session.sessionFlags.pendingFluidBatch = null;
            return 0;
        }

        if (progress.paused()) {
            return 0;
        }
        if (progress.suspended()) {
            return 0;
        }

        List<LinkedFluidHandler> fluidHandlers = com.rtsbuilding.rtsbuilding.server.storage.resolver
                .RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);

        int placed = 0;
        boolean fluidDepleted = false;
        BlockPos lastFailedPos = null;
        while (placed < FLUID_BATCH_MAX_BLOCKS_PER_TICK && job.cursor < job.positions.size()) {
            BlockPos pos = job.positions.get(job.cursor);
            boolean success = RtsStorageFluids.placeFluidAtPosition(
                    player, session, fluidHandlers, pos, job.fluidId);
            job.cursor++;
            if (success) {
                job.completed++;
                placed++;
            } else {
                lastFailedPos = pos;
                fluidDepleted = true;
                token.recordFailure();
                break;
            }
        }

        if (placed > 0) {
            token.markProgress();
            if (job.completed > 0 && progress.totalBlocks() == 0) {
                token.setTotalBlocks(job.positions.size());
            }
        }

        boolean allDone = job.cursor >= job.positions.size();
        if (allDone || fluidDepleted) {
            // 记录已放置位置用于 Ctrl+Z 撤回
            if (job.completed > 0) {
                List<BlockPos> placedPositions = new ArrayList<>(job.positions.subList(0, job.completed));
                ServerHistoryManager.recordPlacement(player, placedPositions, Direction.UP);
            }
            if (fluidDepleted && !allDone) {
                token.suspend();
            } else {
                token.recordFailures(job.positions.size() - job.completed);
                token.complete();
            }
            session.sessionFlags.pendingFluidBatch = null;
            ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
        }

        return placed;
    }

    private static FluidBatchJob getActiveJob(RtsStorageSession session) {
        if (session == null) return null;
        Object raw = session.sessionFlags.pendingFluidBatch;
        return raw instanceof FluidBatchJob job ? job : null;
    }

    public static final class FluidBatchJob {
        public final List<BlockPos> positions;
        public final String fluidId;
        public final int workflowEntryId;
        public int cursor;
        public int completed;

        FluidBatchJob(List<BlockPos> positions, String fluidId, int workflowEntryId) {
            this.positions = positions;
            this.fluidId = fluidId;
            this.workflowEntryId = workflowEntryId;
            this.cursor = 0;
            this.completed = 0;
        }
    }
}
