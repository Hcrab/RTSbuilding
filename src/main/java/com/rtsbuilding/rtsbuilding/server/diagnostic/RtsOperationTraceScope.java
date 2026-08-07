package com.rtsbuilding.rtsbuilding.server.diagnostic;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext;

/**
 * 把网络线程切回主线程后的一次请求上下文交给同步创建的 Pipeline。
 *
 * <p>作用域严格由 try/finally 清理；它不跨 Tick 保存状态，长期关联由注册表负责。</p>
 */
public final class RtsOperationTraceScope {
    private static final ThreadLocal<RtsOperationTraceContext> CURRENT =
            new ThreadLocal<RtsOperationTraceContext>();

    private RtsOperationTraceScope() {}

    public static RtsOperationTraceContext currentOrLegacy(String packet) {
        RtsOperationTraceContext value = CURRENT.get();
        return value == null ? RtsOperationTraceContext.legacy(packet) : value;
    }

    public static void run(RtsOperationTraceContext trace, Runnable action) {
        RtsOperationTraceContext previous = CURRENT.get();
        CURRENT.set(trace == null ? RtsOperationTraceContext.legacy("UNKNOWN") : trace);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
