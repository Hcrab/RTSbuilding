package com.rtsbuilding.rtsbuilding.server.pipeline.execution;

import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

public final class SyncPipe implements PipelinePipe<PipelineContext> {

    // ── History record keys (from HistoryRecordPipe) ──
    public static final TypedKey<List<BlockPos>> ARG_HISTORY_POSITIONS = new TypedKey<>("historyPositions", (Class) List.class);
    public static final TypedKey<List<HistoryBlockRecord>> ARG_HISTORY_RECORDS = new TypedKey<>("historyRecords", (Class) List.class);
    public static final TypedKey<Direction> ARG_HISTORY_FACE = new TypedKey<>("historyFace", Direction.class);

    // ── NetworkSyncPipe keys (deprecated, kept for compat) ──
    public static final TypedKey<Integer> ARG_TOTAL_BLOCKS = new TypedKey<>("totalBlocks", Integer.class);
    public static final TypedKey<Integer> ARG_PROCESSED_BLOCKS = new TypedKey<>("processedBlocks", Integer.class);

    // ── UI refresh keys (from UiRefreshPipe) ──
    public static final TypedKey<Integer> ARG_PAGE_NUMBER = new TypedKey<>("pageNumber", Integer.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        recordHistory(ctx);
        refreshUi(ctx);
        return PipelineResult.success();
    }

    private void recordHistory(PipelineContext ctx) {
        boolean hasRecords = ctx.hasData(ARG_HISTORY_RECORDS);
        boolean hasPositions = ctx.hasData(ARG_HISTORY_POSITIONS);
        if (!hasRecords && !hasPositions) return;

        Direction face = ctx.hasData(ARG_HISTORY_FACE) ? ctx.getData(ARG_HISTORY_FACE) : Direction.DOWN;

        if (hasRecords) {
            List<HistoryBlockRecord> records = ctx.getData(ARG_HISTORY_RECORDS);
            if (records != null && !records.isEmpty()) {
                ServerHistoryManager.recordBreakWithRecords(ctx.player(), records, face);
            }
        } else {
            List<BlockPos> positions = ctx.getData(ARG_HISTORY_POSITIONS);
            if (positions != null && !positions.isEmpty()) {
                ServerHistoryManager.recordBreak(ctx.player(), positions, face);
            }
        }
    }

    private void refreshUi(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) return;

        int page = ctx.hasData(ARG_PAGE_NUMBER) ? ctx.getData(ARG_PAGE_NUMBER) : session.browser.page;
        RtsServer.get().page().requestPage(ctx.player(), page,
                session.browser.search, session.browser.category,
                session.browser.sort, session.browser.ascending);
    }
}
