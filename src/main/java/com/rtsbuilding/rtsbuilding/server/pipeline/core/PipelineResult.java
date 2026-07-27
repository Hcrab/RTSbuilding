package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import javax.annotation.Nullable;

/**
 * 密封结果类型，由每个 {@link PipelinePipe} 和 {@link WorkflowPipeline}
 * 执行返回。
 *
 * <p>使用密封接口保证穷尽性处理——所有可能的
 * 结果在编译期都被捕获。</p>
 *
 * <p>存在三种变体：</p>
 * <ul>
 *   <li>{@link Success} —— 管道正常完成；继续执行下一个管道。</li>
 *   <li>{@link Failure} —— 管道失败；管道停止（快速失败）。</li>
 *   <li>{@link Skip} —— 管道选择跳过；本次运行的剩余管道被跳过。</li>
 * </ul>
 */
public interface PipelineResult {

    /** 管道正常完成。 */
    final class Success implements PipelineResult {
        private Success() { }
    }

    /** 管道失败。携带人类可读的消息和可选的异常。 */
    final class Failure implements PipelineResult {
        private final String message;
        @Nullable private final Throwable cause;

        public Failure(String message, @Nullable Throwable cause) {
            this.message = java.util.Objects.requireNonNull(message, "message");
            this.cause = cause;
        }

        public Failure(String message) {
            this(message, null);
        }

        public String message() { return message; }
        @Nullable public Throwable cause() { return cause; }
    }

    /**
     * 管道选择跳过剩余的管道阶段。
     * 这<b>不是</b>错误——这是一个有意的提前退出信号
     *（例如创造模式挖掘绕过工具借用和基于 Tick 的执行）。
     */
    final class Skip implements PipelineResult {
        private final String reason;

        public Skip(String reason) {
            this.reason = java.util.Objects.requireNonNull(reason, "reason");
        }

        public String reason() { return reason; }
    }

    /** {@link Success} 的共享单例——无状态，无需新实例。 */
    PipelineResult SUCCESS = new Success();

    // ──────────────────────────────────────────────────────────────────
    //  便捷工厂方法
    // ──────────────────────────────────────────────────────────────────

    /** 成功结果的快捷方式。 */
    static PipelineResult success() {
        return SUCCESS;
    }

    /** 带消息的失败结果的快捷方式。 */
    static PipelineResult failure(String message) {
        return new Failure(message);
    }

    /** 跳过结果的快捷方式。 */
    static PipelineResult skip(String reason) {
        return new Skip(reason);
    }
}
