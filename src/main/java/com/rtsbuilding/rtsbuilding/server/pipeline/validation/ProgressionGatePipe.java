package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * 检查玩家是否已解锁所需的进度功能。
 *
 * <p>所需功能通过 record 组件注入；运行时不会查询上下文参数。
 * 此常量提供给需要<b>写入</b>功能到上下文参数供下游消费的 Pipe。</p>
 */
public final class ProgressionGatePipe implements PipelinePipe<PipelineContext> {

    private final RtsFeature feature;

    public ProgressionGatePipe(RtsFeature feature) {
        this.feature = java.util.Objects.requireNonNull(feature, "feature");
    }

    public RtsFeature feature() {
        return feature;
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof ProgressionGatePipe
                && feature == ((ProgressionGatePipe) other).feature;
    }
    @Override public int hashCode() { return feature.hashCode(); }
    @Override public String toString() { return "ProgressionGatePipe[feature=" + feature + "]"; }

    public static final TypedKey<RtsFeature> ARG_FEATURE = new TypedKey<>("feature", RtsFeature.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (!RtsProgressionManager.canUse(ctx.player(), feature)) {
            ResourceLocation pluginId = BuiltInRtsPluginCatalog.requiredPluginFor(feature);
            ITextComponent pluginName = pluginId == null
                    ? new TextComponentString(feature.name())
                    : new TextComponentTranslation("item." + pluginId.getNamespace()
                            + "." + pluginId.getPath());
            ctx.player().sendStatusMessage(
                    new TextComponentTranslation("message.rtsbuilding.plugin_required", pluginName), true);
            return PipelineResult.failure("Feature not unlocked: " + feature.name());
        }
        return PipelineResult.success();
    }
}
