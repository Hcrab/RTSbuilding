package com.rtsbuilding.rtsbuilding.server.diagnostic;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskRepository;

/**
 * durable task 持久化的只读诊断出口。
 *
 * <p>这里只记录批次尺寸、耗时和 ACK 结果，不记录磁盘绝对路径、NBT、蓝图内容或玩家库存。
 * 所有入口都 fail-open，诊断失败不能改变持久化本身的成功或失败判定。</p>
 */
public final class RtsPersistenceDiagnostics {
    private RtsPersistenceDiagnostics() {
    }

    public static void loadBegin() {
        event(true, false, "PERSIST_LOAD_BEGIN", "status", "BEGIN");
    }

    public static void loadResult(int tasks, int active, int terminal, long elapsedMs) {
        event(true, false, "PERSIST_LOAD_RESULT",
                "status", "OK",
                "tasks", Math.max(0, tasks),
                "active", Math.max(0, active),
                "terminal", Math.max(0, terminal),
                "elapsed_ms", Math.max(0L, elapsedMs));
    }

    public static void loadFailed(Throwable failure, long elapsedMs) {
        event(true, true, "PERSIST_LOAD_RESULT",
                "status", "FAILED",
                "failure", failureKind(failure),
                "elapsed_ms", Math.max(0L, elapsedMs));
    }

    public static void saveScheduled(
            TaskPersistenceCoordinator.PreparationResult preparation,
            int dirtyAfterSchedule) {
        if (preparation == null || preparation.preparedCommit() == null) return;
        TaskRepository.PreparedCommit commit = preparation.preparedCommit();
        event(false, false, "PERSIST_SAVE_SCHEDULED",
                "ticket", shortTicket(commit.ticketId().toString()),
                "records", commit.recordCount(),
                "estimated_bytes", Math.max(0L, preparation.estimatedBytes()),
                "deferred", preparation.deferredTaskIds().size(),
                "dirty", Math.max(0, dirtyAfterSchedule));
    }

    public static void saveAck(
            TaskPersistenceCoordinator.CommitAckResult ack,
            long elapsedMs,
            int dirtyRemaining) {
        if (ack == null) return;
        boolean failed = ack.outcome() != TaskPersistenceCoordinator.AckOutcome.ACKNOWLEDGED;
        event(failed, failed, failed ? "PERSIST_SAVE_FAILED" : "PERSIST_SAVE_ACK",
                "outcome", ack.outcome(),
                "records", ack.acknowledgedRevisions().size(),
                "purged", ack.purgedReceipts().size(),
                "bytes", ack.bytesWritten(),
                "dirty_remaining", Math.max(0, dirtyRemaining),
                "elapsed_ms", Math.max(0L, elapsedMs),
                "failure", failureKind(ack.failure()));
    }

    public static void writerFailed(Throwable failure, long elapsedMs, int dirtyRemaining) {
        event(true, true, "PERSIST_SAVE_FAILED",
                "outcome", "WRITER_EXCEPTION",
                "dirty_remaining", Math.max(0, dirtyRemaining),
                "elapsed_ms", Math.max(0L, elapsedMs),
                "failure", failureKind(failure));
    }

    public static void stopResult(boolean success, int dirty, long elapsedMs, Throwable failure) {
        event(true, !success, "PERSIST_STOP_RESULT",
                "status", success ? "OK" : "FAILED",
                "dirty", Math.max(0, dirty),
                "elapsed_ms", Math.max(0L, elapsedMs),
                "failure", failureKind(failure));
    }

    private static void event(boolean latestLog, boolean warning, String name, Object... fields) {
        try {
            if (level() == RtsDiagnosticLevel.OFF) return;
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; fields != null && i + 1 < fields.length; i += 2) {
                suffix.append(' ').append(fields[i]).append('=').append(safe(fields[i + 1]));
            }
            if (latestLog) {
                String message = "[RTS-DIAG] schema=2 side=S run={} event={}{}";
                if (warning) RtsbuildingMod.LOGGER.warn(message, RtsTraceIds.runId(), name, suffix);
                else RtsbuildingMod.LOGGER.info(message, RtsTraceIds.runId(), name, suffix);
            }
            Object[] structured = new Object[(fields == null ? 0 : fields.length) + 2];
            structured[0] = "run";
            structured[1] = RtsTraceIds.runId();
            if (fields != null) System.arraycopy(fields, 0, structured, 2, fields.length);
            RtsStructuredDiagnostics.appendServer(name, structured);
        } catch (RuntimeException ignored) {
            // 诊断必须 fail-open，不能反向改变 durable task 的数据安全语义。
        }
    }

    private static RtsDiagnosticLevel level() {
        try {
            return Config.SERVER_DIAGNOSTIC_LEVEL.get();
        } catch (IllegalStateException ignored) {
            return RtsDiagnosticLevel.BASIC;
        }
    }

    private static String shortTicket(String value) {
        return value == null ? "-" : value.substring(0, Math.min(8, value.length()));
    }

    private static String failureKind(Throwable failure) {
        return failure == null ? "NONE" : safe(failure.getClass().getSimpleName());
    }

    private static String safe(Object value) {
        if (value == null) return "-";
        return String.valueOf(value).replace('\r', ' ').replace('\n', ' ')
                .replace('"', '\'').replace(' ', '_');
    }
}
