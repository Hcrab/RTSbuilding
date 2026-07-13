package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintPersistence;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintTickPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.*;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PendingPlacementPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.UiRefreshPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.ProgressionGatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionDimensionPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsUltimineProcessor;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

/**
 * 在模组初始化时注册所有工作流管道。
 *
 * <p>每个 {@link RtsWorkflowType} 获得其自己的有序 {@link PipelinePipe}
 * 阶段序列。这里的管道定义代表了每个操作类型的规范"应该做什么"，
 * 作为工作流编排的单一事实来源。</p>
 *
 * <p>在 {@code FMLCommonSetupEvent} 期间调用 {@link #registerAll()}
 * 以填充 {@link PipelineRegistry}。</p>
 *
 * <h3>管道设计</h3>
 *
 * <pre>{@code
 * MINE_SINGLE:
 *   ProgressionGate(REMOTE_BREAK) → SessionValidate → SessionDimension →
 *   StopPrevious → WorkflowStart → ToolBorrow → MiningExecute → UiRefresh
 *   [然后异步：tickActiveMining → finalizeMiningOperation 完成工作流，
 *    归还工具，并触发 COMPLETED 事件（真正完成，条目移除）]
 *
 * ULTIMINE / AREA_MINE / AREA_DESTROY:
 *   ProgressionGate → SessionValidate → SessionDimension → StopPrevious →
 *   ToolBorrow → WorkflowStart → UltimineExecute → WorkflowProgress →
 *   NetworkSync → UiRefresh
 *   [然后可 Tick：UltimineTickPipe] // 异步逐 Tick 监控
 *
 * PLACE_SINGLE / QUICK_BUILD:
 *   SessionValidate → WorkflowStart → PlacementExecute → UiRefresh
 *
 * PLACE_BATCH:
 *   SessionValidate → WorkflowStart → PlacementExecute → PendingPlacement → UiRefresh
 *
 * BLUEPRINT_BUILD:
 *   ProgressionGate(BLUEPRINTS) → SessionValidate → WorkflowStart →
 *   BlueprintExecute → UiRefresh
 *   [然后可 Tick：BlueprintTickPipe] // 异步逐 Tick 放置
 * }</pre>
 */
public final class RtsPipelineRegistration {

    private RtsPipelineRegistration() {
    }

    /**
     * 注册所有工作流管道。多次调用是安全的
     *（后续调用通过 {@link PipelineRegistry} 检查变为空操作）。
     */
    public static void registerAll() {
        registerMineSingle();
        registerUltimine();
        registerAreaMine();
        registerAreaDestroy();
        registerPlaceSingle();
        registerPlaceBatch();
        registerQuickBuild();
        registerBlueprintBuild();
        registerStopMining();

        // 注册蓝图工作流恢复处理器
        RtsWorkflowEngine.setBlueprintRestoreHandler(BlueprintPersistence.createRestoreHandler());
    }

    // ──────────────────────────────────────────────────────────────────
    //  挖掘管道
    // ──────────────────────────────────────────────────────────────────

    /**
     * MINE_SINGLE —— 单方块远程挖掘。
     *
     * <p>标准管道流程：功能门控 → 会话 →
     * 维度 → 停止前一个 → 启动工作流 → 借用工具 →
     * 执行 → 刷新 UI。
     *
     * <p>工作流完成、工具归还和历史记录在方块实际被破坏后
     * <b>异步</b>发生：
     * {@link RtsMiningStateMachine#tickActiveMining}
     * 检测方块破坏完成并调用
     * 完成工作流条目（在引擎事件总线上触发<b>真正的</b> COMPLETED 事件，
     * 条目被移除），归还借用的工具，并记录历史。
     * 这确保工作流生命周期与实际挖掘操作对齐，
     * 而非管道执行。</p>
     */
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

    /**
     * ULTIMINE —— 连接方块批处理挖掘（Tick 驱动的生命周期）。
     *
     * <p>同步阶段包括：功能门控 → 会话 → 维度 → 停止
     * 前一个 → 借用工具 → 启动工作流 → 执行（目标收集、
     * 状态设置、beginRemoteMining）。同步成功后，
     * {@link UltimineTickPipe} 每个服务器 Tick 运行，监控批处理
     * 进度并在所有目标处理完成时完成工作流。
     * 可 Tick 管道自动注册到 {@link TickablePipelineRegistry}
     * 并在完成时清理。</p>
     */
    private static void registerUltimine() {
        PipelineRegistry.miningPipeline(RtsWorkflowType.ULTIMINE)
                .pipe(new ProgressionGatePipe(RtsFeature.ULTIMINE))
                .pipe(new SessionValidatePipe())
                .pipe(new SessionDimensionPipe())
                .pipe(new StopPreviousPipe(true))
                .pipe(new WorkflowStartPipe(RtsWorkflowType.ULTIMINE, RtsWorkflowPriority.HIGH))
                .pipe(new ToolBorrowPipe())
                .pipe(new UltimineExecutePipe(RtsWorkflowType.ULTIMINE))
                .pipe(new UiRefreshPipe())
                .tickable(new UltimineTickPipe())
                .register();
    }

    /**
     * AREA_MINE —— 3D 体积区域挖掘（Tick 驱动的生命周期）。
     *
     * <p>结构与 ULTIMINE 相同，但使用区域特定的目标扫描。
     * {@link RtsUltimineProcessor}
     * 使用 {@code AreaOperationExecutor} 进行目标扫描。</p>
     */
    private static void registerAreaMine() {
        PipelineRegistry.miningPipeline(RtsWorkflowType.AREA_MINE)
                .pipe(new ProgressionGatePipe(RtsFeature.AREA_MINE))
                .pipe(new SessionValidatePipe())
                .pipe(new SessionDimensionPipe())
                .pipe(new StopPreviousPipe(true))
                .pipe(new WorkflowStartPipe(RtsWorkflowType.AREA_MINE, RtsWorkflowPriority.HIGH))
                .pipe(new ToolBorrowPipe())
                .pipe(new UltimineExecutePipe(RtsWorkflowType.AREA_MINE))
                .pipe(new UiRefreshPipe())
                .tickable(new UltimineTickPipe())
                .register();
    }

    /**
     * AREA_DESTROY —— 从快速构建预览中破坏形状（异步队列驱动）。
     *
     * <p>对齐范围放置的架构：Pipeline 仅负责入队到 {@code destroyJobs} 队列，
     * 实际破坏由 {@link RtsDestructionBatch#tickDestroyJobs} 在每 tick 中处理。
     * 采用 {@code asyncCompletion} 生命周期，不再使用 tickable 监控。
     * 工具借用、工作流启动仍发生在 Pipeline 同步阶段；
     * 工作流条目的完成和工具归还在 tick 处理中异步完成。</p>
     */
    private static void registerAreaDestroy() {
        PipelineRegistry.miningPipeline(RtsWorkflowType.AREA_DESTROY)
                .pipe(new ProgressionGatePipe(RtsFeature.AREA_DESTROY))
                .pipe(new SessionValidatePipe())
                .pipe(new SessionDimensionPipe())
                .pipe(new StopPreviousPipe(true))
                .pipe(new WorkflowStartPipe(RtsWorkflowType.AREA_DESTROY, RtsWorkflowPriority.HIGH))
                .pipe(new ToolBorrowPipe())
                .pipe(new UltimineExecutePipe(RtsWorkflowType.AREA_DESTROY))
                .pipe(new UiRefreshPipe())
                .asyncCompletion()
                .register();
    }

    // ──────────────────────────────────────────────────────────────────
    //  放置管道
    // ──────────────────────────────────────────────────────────────────

    /**
     * PLACE_SINGLE —— 单方块远程放置。
     *
     * <p>解析会话，将放置作业入队到批处理
     * 队列（由
     * {@link RtsPlacementBatch}
     * 处理），并刷新 UI。</p>
     */
    private static void registerPlaceSingle() {
        PipelineRegistry.placementPipeline(RtsWorkflowType.PLACE_SINGLE)
                .pipe(new ProgressionGatePipe(RtsFeature.REMOTE_PLACE))
                .pipe(new SessionValidatePipe())
                .pipe(new WorkflowStartPipe(RtsWorkflowType.PLACE_SINGLE, RtsWorkflowPriority.NORMAL))
                .pipe(new PlacementExecutePipe())
                .pipe(new UiRefreshPipe())
                .asyncCompletion()
                .register();
    }

    /**
     * PLACE_BATCH —— 多方块批处理放置（交互式）。
     *
     * <p>与 PLACE_SINGLE 相同，但添加了 {@link PendingPlacementPipe}
     * 以在新批处理入队后尝试恢复挂起的作业。</p>
     */
    private static void registerPlaceBatch() {
        PipelineRegistry.placementPipeline(RtsWorkflowType.PLACE_BATCH)
                .pipe(new ProgressionGatePipe(RtsFeature.REMOTE_PLACE))
                .pipe(new SessionValidatePipe())
                .pipe(new WorkflowStartPipe(RtsWorkflowType.PLACE_BATCH, RtsWorkflowPriority.NORMAL))
                .pipe(new PlacementExecutePipe())
                .pipe(new PendingPlacementPipe())
                .pipe(new UiRefreshPipe())
                .asyncCompletion()
                .register();
    }

    /**
     * QUICK_BUILD —— 预解析状态的形状放置。
     *
     * <p>与 PLACE_SINGLE 结构相同，但
     * {@link RtsPlacementBatch}
     * 使用快速构建放置路径（基于状态的计划而非
     * 交互式射线追踪）。</p>
     */
    private static void registerQuickBuild() {
        PipelineRegistry.placementPipeline(RtsWorkflowType.QUICK_BUILD)
                .pipe(new ProgressionGatePipe(RtsFeature.REMOTE_PLACE))
                .pipe(new SessionValidatePipe())
                .pipe(new WorkflowStartPipe(RtsWorkflowType.QUICK_BUILD, RtsWorkflowPriority.NORMAL))
                .pipe(new PlacementExecutePipe())
                .pipe(new UiRefreshPipe())
                .asyncCompletion()
                .register();
    }

    // ──────────────────────────────────────────────────────────────────
    //  蓝图管道
    // ──────────────────────────────────────────────────────────────────

    /**
     * BLUEPRINT_BUILD —— 蓝图文件远程放置构建。
     *
     * <p>同步阶段：功能门控(BLUEPRINTS) → 会话验证 → 启动工作流 →
     * 蓝图校验和执行初始化 → 刷新UI。
     *
     * <p>同步成功后，{@link BlueprintTickPipe} 每个服务器 Tick 运行，
     * 逐步放置蓝图中的方块，并在所有方块放置完成时完成工作流。
     * 可 Tick 管道自动注册到 {@link TickablePipelineRegistry}
     * 并在完成时清理。</p>
     */
    private static void registerBlueprintBuild() {
        PipelineRegistry.register(RtsWorkflowType.BLUEPRINT_BUILD)
                .pipe(new ProgressionGatePipe(RtsFeature.BLUEPRINTS))
                .pipe(new SessionValidatePipe())
                .pipe(new WorkflowStartPipe(RtsWorkflowType.BLUEPRINT_BUILD, RtsWorkflowPriority.NORMAL))
                .pipe(new BlueprintExecutePipe())
                .pipe(new UiRefreshPipe())
                .tickable(new BlueprintTickPipe())
                .register();
    }

    // ──────────────────────────────────────────────────────────────────
    //  独立的停止管道
    // ──────────────────────────────────────────────────────────────────

    /**
     * STOP_MINING —— 停止任何活跃的挖掘/连锁挖掘操作。
     */
    private static void registerStopMining() {
        PipelineRegistry.register(RtsWorkflowType.STOP_MINING)
                .pipe(new SessionValidatePipe())
                .pipe(new StopMiningPipe())
                .register();
    }
}
