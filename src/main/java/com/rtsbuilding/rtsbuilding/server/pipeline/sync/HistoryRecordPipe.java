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

// ── TypedKey 同包内，显式导入以供增量编译器使用 ──


/**
 * 在玩家的历史中记录一批方块破坏操作。
 *
 * <p>支持两种互斥的输入模式：</p>
 * <ul>
 *   <li><b>丰富记录</b> —— {@link #ARG_HISTORY_RECORDS} 作为
 *       {@code List<HistoryBlockRecord>}（由调用者捕获破坏前状态），
 *       委托给 {@link ServerHistoryManager#recordBreakWithRecords}。</li>
 *   <li><b>简单位置</b> —— {@link #ARG_HISTORY_POSITIONS} 作为
 *       {@code List<BlockPos>}，内部捕获当前状态，
 *       委托给 {@link ServerHistoryManager#recordBreak}。</li>
 * </ul>
 *
 * <p>如果两个键都不存在，此 Pipe 为空操作。</p>
 */
public final class HistoryRecordPipe implements PipelinePipe<PipelineContext> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<BlockPos>> ARG_HISTORY_POSITIONS =
            new TypedKey<>("historyPositions", (Class) List.class);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<HistoryBlockRecord>> ARG_HISTORY_RECORDS =
            new TypedKey<>("historyRecords", (Class) List.class);
    public static final TypedKey<Direction> ARG_HISTORY_FACE =
            new TypedKey<>("historyFace", Direction.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        boolean hasRecords = ctx.hasData(ARG_HISTORY_RECORDS);
        boolean hasPositions = ctx.hasData(ARG_HISTORY_POSITIONS);

        if (!hasRecords && !hasPositions) {
            return PipelineResult.success();
        }

        Direction face = ctx.hasData(ARG_HISTORY_FACE)
                ? ctx.getData(ARG_HISTORY_FACE)
                : Direction.DOWN;

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
