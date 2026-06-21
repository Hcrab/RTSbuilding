package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import javax.annotation.Nullable;

/**
 * 可 tick pipe 的单帧执行结果。
 */
public sealed interface TickResult {
    record Running() implements TickResult {}

    record Done() implements TickResult {}

    record Error(String message, @Nullable Throwable cause) implements TickResult {
        public Error(String message) {
            this(message, null);
        }
    }

    static TickResult running() {
        return new Running();
    }

    static TickResult done() {
        return new Done();
    }

    static TickResult error(String message) {
        return new Error(message);
    }
}
