package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * 记录远程破坏历史，供撤销/历史同步使用。
 */
public final class HistoryRecordPipe implements PipelinePipe<PipelineContext> {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<BlockPos>> ARG_HISTORY_POSITIONS =
            new TypedKey("historyPositions", (Class) List.class);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<HistoryBlockRecord>> ARG_HISTORY_RECORDS =
            new TypedKey("historyRecords", (Class) List.class);
    public static final TypedKey<Direction> ARG_HISTORY_FACE =
            new TypedKey<>("historyFace", Direction.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        boolean hasRecords = ctx.hasData(ARG_HISTORY_RECORDS);
        boolean hasPositions = ctx.hasData(ARG_HISTORY_POSITIONS);
        if (!hasRecords && !hasPositions) {
            return PipelineResult.success();
        }

        Direction face = ctx.hasData(ARG_HISTORY_FACE) ? ctx.getData(ARG_HISTORY_FACE) : Direction.DOWN;
        if (hasRecords) {
            List<HistoryBlockRecord> records = ctx.getData(ARG_HISTORY_RECORDS);
            if (!records.isEmpty()) {
                ServerHistoryManager.recordBreakWithRecords(ctx.player(), records, face);
            }
        } else {
            List<BlockPos> positions = ctx.getData(ARG_HISTORY_POSITIONS);
            if (!positions.isEmpty()) {
                ServerHistoryManager.recordBreak(ctx.player(), positions, face);
            }
        }
        return PipelineResult.success();
    }
}
