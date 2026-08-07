package com.rtsbuilding.rtsbuilding.server.diagnostic;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.task.TaskScheduler;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime;
import net.minecraft.server.MinecraftServer;

/** 低成本服务端 tick 健康采样器，只观察时间间隔和已测 RTS 调度量。 */
public final class RtsServerHealthDiagnostics {
  private static final long WARN_GAP_NANOS = 250_000_000L;
  private static final long SEVERE_GAP_NANOS = 1_000_000_000L;
  private static final long SUMMARY_INTERVAL_TICKS = 600L;
  private static final long GAP_OVERLAP_TICKS = 100L;
  private static long lastTickNanos;
  private static long currentServerTick = -1L;
  private static long pendingGapMs;
  private static long recentGapMs;
  private static long recentGapTick = Long.MIN_VALUE;
  private static long lastSummaryTick = Long.MIN_VALUE;

  private RtsServerHealthDiagnostics() {}

  public static synchronized void beginTick(MinecraftServer server) {
    long now = System.nanoTime();
    currentServerTick = server == null ? -1L : server.overworld().getGameTime();
    if (lastTickNanos != 0L) {
      long gapNanos = Math.max(0L, now - lastTickNanos);
      if (gapNanos >= WARN_GAP_NANOS) {
        pendingGapMs = gapNanos / 1_000_000L;
        recentGapMs = pendingGapMs;
        recentGapTick = currentServerTick;
      }
    }
    lastTickNanos = now;
  }

  public static synchronized void completeTick(
      MinecraftServer server, TaskScheduler.TickStats stats, RtsTaskEngine.QueueDiagnostics queue) {
    if (level() == RtsDiagnosticLevel.OFF || server == null || stats == null || queue == null)
      return;
    long tick = server.overworld().getGameTime();
    boolean summaryDue =
        queue.runnable() + queue.waiting() > 0 && tick - lastSummaryTick >= SUMMARY_INTERVAL_TICKS;
    if (pendingGapMs <= 0L && !summaryDue) return;
    String event = pendingGapMs > 0L ? "TICK_GAP" : "SUMMARY";
    long gapMs = pendingGapMs;
    String severity = gapMs * 1_000_000L >= SEVERE_GAP_NANOS ? "SEVERE" : "WARN";
    TaskPersistenceRuntime.Diagnostics persistence = TaskPersistenceRuntime.INSTANCE.diagnostics();
    String message =
        "[RTS-SERVER-HEALTH] schema=2 run={} event={} server_tick={} gap_ms={} severity={} "
            + "rts_runnable={} rts_waiting={} rts_processed_units={} rts_slice_nanos={} "
            + "persistence_dirty={} persistence_inflight={} persistence_asset_pending={}";
    Object[] values = {
      RtsTraceIds.runId(),
      event,
      tick,
      gapMs,
      pendingGapMs > 0L ? severity : "NORMAL",
      queue.runnable(),
      queue.waiting(),
      stats.processedUnits(),
      stats.elapsedNanos(),
      persistence.dirty(),
      persistence.inFlight(),
      persistence.pendingAssetAdmissions()
    };
    if (pendingGapMs > 0L) RtsbuildingMod.LOGGER.warn(message, values);
    else RtsbuildingMod.LOGGER.info(message, values);
    RtsStructuredDiagnostics.appendServer(
        event,
        "run",
        RtsTraceIds.runId(),
        "server_tick",
        tick,
        "gap_ms",
        gapMs,
        "severity",
        pendingGapMs > 0L ? severity : "NORMAL",
        "rts_runnable",
        queue.runnable(),
        "rts_waiting",
        queue.waiting(),
        "rts_processed_units",
        stats.processedUnits(),
        "rts_slice_nanos",
        stats.elapsedNanos(),
        "persistence_dirty",
        persistence.dirty(),
        "persistence_inflight",
        persistence.inFlight(),
        "persistence_asset_pending",
        persistence.pendingAssetAdmissions());
    pendingGapMs = 0L;
    lastSummaryTick = tick;
  }

  public static synchronized long currentServerTick() {
    return currentServerTick;
  }

  public static synchronized long recentGapMs(long serverTick) {
    return serverTick >= recentGapTick && serverTick - recentGapTick <= GAP_OVERLAP_TICKS
        ? recentGapMs
        : 0L;
  }

  public static synchronized void reset() {
    lastTickNanos = 0L;
    currentServerTick = -1L;
    pendingGapMs = 0L;
    recentGapMs = 0L;
    recentGapTick = Long.MIN_VALUE;
    lastSummaryTick = Long.MIN_VALUE;
  }

  private static RtsDiagnosticLevel level() {
    try {
      return Config.SERVER_DIAGNOSTIC_LEVEL.get();
    } catch (IllegalStateException ignored) {
      return RtsDiagnosticLevel.BASIC;
    }
  }
}
