package com.rtsbuilding.rtsbuilding.client.diagnostic;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 摄像机所有权诊断的纯状态机。
 *
 * <p>这个类只负责连续观察计数、事件转换、恢复尝试摘要和调用者去重，
 * 不依赖 Minecraft，也不负责写日志、读取实体或改变摄像机。
 * 所有阈值都集中在这里，方便在不启动客户端的情况下证明高频路径不会逐帧
 * 刷屏。</p>
 */
final class CameraOwnershipDiagnosticState {
    static final int MISMATCH_REPORT_THRESHOLD = 3;
    static final int STABLE_RECOVERY_OBSERVATIONS = 40;
    static final long REMINDER_INTERVAL_NANOS = 30_000_000_000L;

    private final Set<String> reportedCallers = new LinkedHashSet<>();

    private int consecutiveMismatchObservations;
    private int currentEventObservations;
    private int lastEventObservations;
    private int stableRecoveryObservations;
    private boolean eventActive;
    private long lastReportNanos;
    private int restoreAttempts;

    private boolean callerCaptureAttempted;
    private String firstCaller = "unknown";

    /**
     * 记录一次 expected/actual 观察，并只返回需要输出的状态转换。
     *
     * <p>匹配不会立即结束异常事件：必须连续拥有 40 次才输出恢复摘要。
     * 因此第三方模组在“丢失→恢复→再次丢失”的快速振荡只会继续累积同一
     * 事件，不会重复输出成对的丢失/恢复日志。计数只影响诊断，不会阻止或
     * 延迟任何摄像机调用。</p>
     */
    Transition observe(boolean ownsCamera, long nowNanos) {
        if (ownsCamera) {
            consecutiveMismatchObservations = 0;
            if (!eventActive) {
                return Transition.NONE;
            }

            stableRecoveryObservations = saturatingIncrement(stableRecoveryObservations);
            if (stableRecoveryObservations < STABLE_RECOVERY_OBSERVATIONS) {
                return Transition.NONE;
            }

            eventActive = false;
            lastEventObservations = currentEventObservations;
            currentEventObservations = 0;
            stableRecoveryObservations = 0;
            return Transition.RECOVERY;
        }

        stableRecoveryObservations = 0;
        if (!eventActive && consecutiveMismatchObservations == 0) {
            // 新一轮错位从零开始统计恢复尝试，避免新事件沿用旧摘要。
            restoreAttempts = 0;
        }
        consecutiveMismatchObservations = saturatingIncrement(consecutiveMismatchObservations);

        if (!eventActive && consecutiveMismatchObservations >= MISMATCH_REPORT_THRESHOLD) {
            eventActive = true;
            currentEventObservations = consecutiveMismatchObservations;
            lastReportNanos = nowNanos;
            return Transition.LOSS;
        }

        if (eventActive) {
            currentEventObservations = saturatingIncrement(currentEventObservations);
            if (elapsedAtLeast(nowNanos, lastReportNanos, REMINDER_INTERVAL_NANOS)) {
                lastReportNanos = nowNanos;
                return Transition.REMINDER;
            }
        }
        return Transition.NONE;
    }

    /** 记录一次已经执行的现有摄像机恢复尝试；只用于日志摘要。 */
    void recordRestoreAttempt() {
        restoreAttempts = saturatingIncrement(restoreAttempts);
    }

    /**
     * 尝试领取本次会话唯一一次调用栈识别机会。
     *
     * <p>即使首个栈因第三方混入或运行时限制只能得到 unknown，也不再在高频
     * setter 路径重复遍历调用栈。</p>
     */
    boolean claimCallerCapture() {
        if (callerCaptureAttempted) {
            return false;
        }
        callerCaptureAttempted = true;
        return true;
    }

    /**
     * 记录一个外部调用类名，并返回它是否是本次 RTS 会话首次出现。
     * unknown 不进入去重表，避免一次无法识别的栈覆盖后来可识别的类名。
     */
    boolean rememberCaller(String callerClassName) {
        String caller = sanitizeCaller(callerClassName);
        if (caller.equals("unknown")) {
            return false;
        }
        if (firstCaller.equals("unknown")) {
            firstCaller = caller;
        }
        return reportedCallers.add(caller);
    }

    /** 清空一次 RTS 会话的全部诊断状态，防止跨世界或跨会话复用。 */
    void reset() {
        consecutiveMismatchObservations = 0;
        currentEventObservations = 0;
        lastEventObservations = 0;
        stableRecoveryObservations = 0;
        eventActive = false;
        lastReportNanos = 0L;
        restoreAttempts = 0;
        callerCaptureAttempted = false;
        firstCaller = "unknown";
        reportedCallers.clear();
    }

    int consecutiveMismatchObservations() {
        return consecutiveMismatchObservations;
    }

    int continuousObservations() {
        return eventActive ? currentEventObservations : lastEventObservations;
    }

    int stableRecoveryObservations() {
        return stableRecoveryObservations;
    }

    int restoreAttempts() {
        return restoreAttempts;
    }

    String firstCaller() {
        return firstCaller;
    }

    int reportedCallerCount() {
        return reportedCallers.size();
    }

    boolean eventActive() {
        return eventActive;
    }

    /** 饱和递增诊断计数，防止长时间高频 setter 让摘要计数回绕成负数。 */
    static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    private static boolean elapsedAtLeast(long nowNanos, long previousNanos, long intervalNanos) {
        // nanoTime 的差值比较可跨过 long 回绕，只要间隔远小于 long 的一半。
        return nowNanos - previousNanos >= intervalNanos;
    }

    private static String sanitizeCaller(String callerClassName) {
        if (callerClassName == null || callerClassName.isBlank()) {
            return "unknown";
        }
        String sanitized = callerClassName.trim().replaceAll("[^A-Za-z0-9_.$]", "_");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }

    enum Transition {
        NONE,
        LOSS,
        REMINDER,
        RECOVERY
    }

}
