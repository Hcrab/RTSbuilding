package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import javax.annotation.Nullable;

/**
 * 密封结果类型，由每个 {@link TickablePipe#tick(PipelineContext)} 返回。
 *
 * <p>存在三种变体：</p>
 * <ul>
 *   <li>{@link Running} —— Pipe 仍在工作中；继续 Tick。</li>
 *   <li>{@link Done} —— Pipe 正常完成；取消注册并继续。</li>
 *   <li>{@link Error} —— Pipe 失败；取消注册并使管道失败。</li>
 * </ul>
 */
public sealed interface TickResult {

    /** Pipe 仍在工作中——下一帧再次调用 {@code tick()}。 */
    record Running() implements TickResult {}

    /** Pipe 正常完成了它的工作。 */
    record Done() implements TickResult {}

    /** Pipe 遇到了错误。 */
    record Error(String message, @Nullable Throwable cause) implements TickResult {
        public Error(String message) {
            this(message, null);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  便捷工厂方法
    // ──────────────────────────────────────────────────────────────────

    /** "仍在工作中"的快捷方式。 */
    static TickResult running() {
        return new Running();
    }

    /** "成功完成"的快捷方式。 */
    static TickResult done() {
        return new Done();
    }

    /** "失败并带消息"的快捷方式。 */
    static TickResult error(String message) {
        return new Error(message);
    }
}
