package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import javax.annotation.Nullable;

/**
 * pipeline 同步阶段的结果类型。
 */
public sealed interface PipelineResult {
    record Success() implements PipelineResult {}

    record Failure(String message, @Nullable Throwable cause) implements PipelineResult {
        public Failure(String message) {
            this(message, null);
        }
    }

    record Skip(String reason) implements PipelineResult {}

    PipelineResult SUCCESS = new Success();

    static PipelineResult success() {
        return SUCCESS;
    }

    static PipelineResult failure(String message) {
        return new Failure(message);
    }

    static PipelineResult skip(String reason) {
        return new Skip(reason);
    }
}
