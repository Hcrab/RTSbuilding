package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.StopMiningPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.StopPreviousPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineTickPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PendingPlacementPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.NetworkSyncPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.UiRefreshPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.ProgressionGatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionDimensionPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowProgressPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

/**
 * Forge 1.20.1 的 pipeline 注册入口。
 *
 * <p>这里保持 ddf72515 边界内的工作流编排形状：放置、单方块挖掘、
 * 批量挖掘和停止挖掘都通过 pipeline 注册。真实逐 tick 方块操作仍由
 * Forge 现有服务负责，pipeline 只负责启动工作流、校验会话、借用工具和
 * 接入 tickable 生命周期。</p>
 */
public final class RtsPipelineRegistration {
    private static boolean registered;

    private RtsPipelineRegistration() {
    }

    public static synchronized void registerAll() {
        if (registered) {
            return;
        }
        registerPlaceSingle();
        registerPlaceBatch();
        registerQuickBuild();
        registerMineSingle();
        registerUltimine();
        registerAreaMine();
        registerAreaDestroy();
        registerStopMining();
        registered = true;
        RtsbuildingMod.LOGGER.info("[PipelineRegistry] Forge placement and mining pipelines registered");
    }

    private static void registerPlaceSingle() {
        PipelineRegistry.placementPipeline(RtsWorkflowType.PLACE_SINGLE)
                .pipe(new SessionValidatePipe())
                .pipe(new WorkflowStartPipe(RtsWorkflowType.PLACE_SINGLE, RtsWorkflowPriority.NORMAL))
                .pipe(new PlacementExecutePipe())
                .pipe(new UiRefreshPipe())
                .asyncCompletion()
                .register();
    }

    private static void registerPlaceBatch() {
        PipelineRegistry.placementPipeline(RtsWorkflowType.PLACE_BATCH)
                .pipe(new SessionValidatePipe())
                .pipe(new WorkflowStartPipe(RtsWorkflowType.PLACE_BATCH, RtsWorkflowPriority.NORMAL))
                .pipe(new PlacementExecutePipe())
                .pipe(new PendingPlacementPipe())
                .pipe(new UiRefreshPipe())
                .asyncCompletion()
                .register();
    }

    private static void registerQuickBuild() {
        PipelineRegistry.placementPipeline(RtsWorkflowType.QUICK_BUILD)
                .pipe(new SessionValidatePipe())
                .pipe(new WorkflowStartPipe(RtsWorkflowType.QUICK_BUILD, RtsWorkflowPriority.NORMAL))
                .pipe(new PlacementExecutePipe())
                .pipe(new UiRefreshPipe())
                .asyncCompletion()
                .register();
    }

    private static void registerMineSingle() {
        PipelineRegistry.miningPipeline(RtsWorkflowType.MINE_SINGLE)
                .pipe(new ProgressionGatePipe(RtsFeature.REMOTE_BREAK))
                .pipe(new SessionValidatePipe())
                .pipe(new SessionDimensionPipe())
                .pipe(new StopPreviousPipe(false))
                .pipe(new WorkflowStartPipe(RtsWorkflowType.MINE_SINGLE, RtsWorkflowPriority.NORMAL))
                .pipe(new ToolBorrowPipe())
                .pipe(new MiningExecutePipe())
                .pipe(new UiRefreshPipe())
                .asyncCompletion()
                .register();
    }

    private static void registerUltimine() {
        PipelineRegistry.miningPipeline(RtsWorkflowType.ULTIMINE)
                .pipe(new ProgressionGatePipe(RtsFeature.ULTIMINE))
                .pipe(new SessionValidatePipe())
                .pipe(new SessionDimensionPipe())
                .pipe(new StopPreviousPipe(true))
                .pipe(new WorkflowStartPipe(RtsWorkflowType.ULTIMINE, RtsWorkflowPriority.HIGH))
                .pipe(new ToolBorrowPipe())
                .pipe(new UltimineExecutePipe(RtsWorkflowType.ULTIMINE))
                .pipe(new WorkflowProgressPipe(0))
                .pipe(new NetworkSyncPipe())
                .pipe(new UiRefreshPipe())
                .tickable(new UltimineTickPipe())
                .register();
    }

    private static void registerAreaMine() {
        PipelineRegistry.miningPipeline(RtsWorkflowType.AREA_MINE)
                .pipe(new ProgressionGatePipe(RtsFeature.ULTIMINE))
                .pipe(new SessionValidatePipe())
                .pipe(new SessionDimensionPipe())
                .pipe(new StopPreviousPipe(true))
                .pipe(new WorkflowStartPipe(RtsWorkflowType.AREA_MINE, RtsWorkflowPriority.HIGH))
                .pipe(new ToolBorrowPipe())
                .pipe(new UltimineExecutePipe(RtsWorkflowType.AREA_MINE))
                .pipe(new WorkflowProgressPipe(0))
                .pipe(new NetworkSyncPipe())
                .pipe(new UiRefreshPipe())
                .tickable(new UltimineTickPipe())
                .register();
    }

    private static void registerAreaDestroy() {
        PipelineRegistry.miningPipeline(RtsWorkflowType.AREA_DESTROY)
                .pipe(new ProgressionGatePipe(RtsFeature.AREA_DESTROY))
                .pipe(new SessionValidatePipe())
                .pipe(new SessionDimensionPipe())
                .pipe(new StopPreviousPipe(true))
                .pipe(new WorkflowStartPipe(RtsWorkflowType.AREA_DESTROY, RtsWorkflowPriority.HIGH))
                .pipe(new ToolBorrowPipe())
                .pipe(new UltimineExecutePipe(RtsWorkflowType.AREA_DESTROY))
                .pipe(new WorkflowProgressPipe(0))
                .pipe(new NetworkSyncPipe())
                .pipe(new UiRefreshPipe())
                .tickable(new UltimineTickPipe())
                .register();
    }

    private static void registerStopMining() {
        PipelineRegistry.register(RtsWorkflowType.STOP_MINING)
                .pipe(new SessionValidatePipe())
                .pipe(new StopMiningPipe())
                .register();
    }
}
