package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

/**
 * 检查玩家是否已解锁指定功能。
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
