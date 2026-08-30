package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService.InstantRecoveryResult;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * 在普通挖掘创建工作流或借用工具之前，尝试完成一次已追踪方块的同步回收。
 *
 * <p>本阶段只拥有“是否应进入瞬时回收”的编排职责：未开启开关或未命中精确
 * tracker 时无副作用地继续普通挖掘；一旦命中，则由回收服务完成权限验证、原版
 * 破坏和 tracker 提交。它不创建工作流、不借用工具，也不接管普通挖掘状态机。</p>
 */
public final class TrackedPlacedRecoveryPipe implements PipelinePipe<MiningContext> {

    @Override
    public PipelineResult execute(MiningContext ctx) {
        if (!ctx.isAllowPlacedBlockRecovery()) {
            return PipelineResult.success();
        }
        RtsStorageSession session = ctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        InstantRecoveryResult result = RtsPlacedRecoveryService.tryInstantRecovery(
                ctx.player(), session, ctx.getPos(), ctx.getFace());
        return switch (result) {
            case NOT_TRACKED -> PipelineResult.success();
            case BROKEN -> PipelineResult.skip("Tracked placed block recovered immediately");
            case REJECTED -> PipelineResult.failure("Tracked placed block recovery was rejected");
            case FAILED -> PipelineResult.failure("Tracked placed block recovery failed");
        };
    }
}
