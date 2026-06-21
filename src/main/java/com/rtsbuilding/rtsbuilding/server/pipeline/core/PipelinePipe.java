package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * pipeline 中一个同步阶段。
 */
@FunctionalInterface
public interface PipelinePipe<C extends PipelineContext> {
    PipelineResult execute(C ctx);
}
