package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

/**
 * Checks whether the player has unlocked the required progression feature.
 *
 * <p>The required feature is injected via a record component; context arguments
 * are not queried at runtime. This constant is provided for Pipes that need to
 * <b>write</b> a feature into context arguments for downstream consumption.</p>
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
