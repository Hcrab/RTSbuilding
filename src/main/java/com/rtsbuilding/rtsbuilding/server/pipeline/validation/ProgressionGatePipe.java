package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

/**
 * 检查玩家是否已解锁所需的进度功能。
 *
 * <p>所需功能通过 record 组件注入；运行时不会查询上下文参数。
 * 此常量提供给需要<b>写入</b>功能到上下文参数供下游消费的 Pipe。</p>
 */
public record ProgressionGatePipe(RtsFeature feature) implements PipelinePipe<PipelineContext> {

    public static final TypedKey<RtsFeature> ARG_FEATURE = new TypedKey<>("feature", RtsFeature.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (!RtsProgressionManager.canUse(ctx.player(), feature)) {
            return PipelineResult.failure("Feature not unlocked: " + feature.name());
        }
        return PipelineResult.success();
    }
}
