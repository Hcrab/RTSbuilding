package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * 跨多个服务端 tick 推进的 pipeline 阶段。
 */
@FunctionalInterface
public interface TickablePipe {
    TickResult tick(PipelineContext ctx);
}
