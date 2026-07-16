package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

/**
 * 在新放置批处理入队后，尝试恢复任何挂起（已暂停）的放置作业。
 *
 * <p>此 Pipe 通常包含在 {@link PlacementExecutePipe} 之后，
 * 位于 {@link RtsWorkflowType#PLACE_BATCH}
 * 管道中。如果没有挂起的作业或上下文中没有会话，则为空操作。</p>
 *
 * <p>从共享数据中读取：</p>
 * <ul>
 *   <li>{@code "session"} —— 由 {@link SessionValidatePipe} 解析</li>
 * </ul>
 */
public final class PendingPlacementPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null || session.placement.pendingJobs.isEmpty()) {
            return PipelineResult.success();
        }

        // 新放置命令本身不会产生库存流入；等待任务只由精确 changed-item 事件或玩家显式重试唤醒。
        return PipelineResult.success();
    }
}
