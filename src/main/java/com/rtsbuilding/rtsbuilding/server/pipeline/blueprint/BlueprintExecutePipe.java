package com.rtsbuilding.rtsbuilding.server.pipeline.blueprint;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintNetworkHandlers;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * 蓝图放置的同步执行管道——校验蓝图数据并初始化进度追踪。
 *
 * <p>此 Pipe 在同步阶段运行，负责：</p>
 * <ol>
 *   <li>校验蓝图启用的配置和权限</li>
 *   <li>校验蓝图为非空</li>
 *   <li>校验方块数未超上限</li>
 *   <li>初始化进度追踪计数器到共享数据中</li>
 *   <li>计算旋转偏移量并存放到共享数据</li>
 * </ol>
 *
 * <p>校验失败时会直接向客户端发送错误状态消息。
 * 实际的逐 Tick 放置由 {@link BlueprintTickPipe} 执行。</p>
 */
public final class BlueprintExecutePipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        BlueprintContext bctx = BlueprintContext.require(ctx);

        // ── 校验蓝图是否启用 ────────────────────────────────────
        if (!Config.areBlueprintsEnabled()) {
            send(bctx.player(), S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.disabled", "");
            return PipelineResult.failure("Blueprints disabled by config");
        }

        // ── 校验蓝图数据 ────────────────────────────────────────
        RtsBlueprint blueprint = bctx.getBlueprint();
        if (blueprint == null || blueprint.blocks().isEmpty()) {
            send(bctx.player(), S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.empty", "");
            return PipelineResult.failure("Blueprint is empty");
        }

        int maxBlocks = Config.maxBlueprintBlocks();
        if (blueprint.blockCount() > maxBlocks) {
            send(bctx.player(), S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.too_many_blocks",
                    blueprint.blockCount() + "/" + maxBlocks);
            return PipelineResult.failure("Blueprint exceeds max block count " + maxBlocks);
        }

        // ── 校验锚点 ────────────────────────────────────────────
        BlockPos anchor = bctx.getAnchor();
        if (anchor == null) {
            send(bctx.player(), S2CBlueprintStatusPayload.ERROR, "screen.rtsbuilding.blueprints.status.parse_failed", "");
            return PipelineResult.failure("Invalid anchor position");
        }

        // ── 计算并存放旋转中心偏移量 ─────────────────────────────
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(
                blueprint.size(),
                bctx.getYRotationSteps(),
                bctx.getXRotationSteps(),
                bctx.getZRotationSteps());
        bctx.setData(BlueprintContext.KEY_CENTER_OFFSET, centerOffset);

        // ── 预计算所有方块的放置计划 ─────────────────────────────
        List<PlacementPlan> plans = BlockPlacementPlanner.compute(
                blueprint, anchor, centerOffset,
                bctx.getYRotationSteps(),
                bctx.getXRotationSteps(),
                bctx.getZRotationSteps());
        bctx.setPlacementPlans(plans);

        // ── 初始化环形队列（所有有效索引，逐层从下往上，每层内辐射）─────
        BlockPos centerPos = anchor.offset(centerOffset);
        List<Integer> indices = new ArrayList<>(blueprint.blockCount());
        for (int i = 0; i < blueprint.blockCount(); i++) {
            indices.add(i);
        }
        indices.sort(Comparator.<Integer, Integer>comparing(i -> {
            PlacementPlan plan = plans.get(i);
            if (plan == null) return Integer.MAX_VALUE;
            return plan.target().getY();
        }).thenComparingDouble(i -> {
            PlacementPlan plan = plans.get(i);
            if (plan == null) return Double.MAX_VALUE;
            return plan.target().distSqr(centerPos);
        }));
        LinkedList<Integer> remaining = new LinkedList<>(indices);
        bctx.setRemainingQueue(remaining);

        // ── 向客户端发送排队成功消息 ─────────────────────────────
        send(bctx.player(), S2CBlueprintStatusPayload.INFO, "screen.rtsbuilding.blueprints.status.queued",
                Integer.toString(blueprint.blockCount()));

        // ── 初始化进度计数器 ─────────────────────────────────────
        bctx.setPlacedCount(0);
        bctx.setSkippedMissing(0);
        bctx.setSkippedUnsupported(0);
        bctx.setSkippedMissingBlocks(0);
        bctx.setSkippedBlocked(0);

        // ── 持久化初始状态到工作流条目 ─────────────────────────────
        int entryId = ctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        BlueprintPersistence.saveToEntry(ctx.player(), entryId, bctx);

        return PipelineResult.success();
    }

    private static void send(net.minecraft.server.level.ServerPlayer player, byte status, String messageKey, String detail) {
        BlueprintNetworkHandlers.send(player, status, messageKey, detail);
    }
}
