package com.rtsbuilding.rtsbuilding.common.diagnostics;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 开发者诊断专用的有界、批量、可轮转 JSONL 写入器。
 *
 * <p>调用线程只做一次非阻塞 offer。队列满或磁盘失败时丢弃诊断并累计计数，绝不阻塞客户端帧、 服务端 Tick 或把诊断故障升级成玩法故障。后台线程会将同一路径的多行合并为一次写入。
 */
public final class RtsAsyncJsonlWriter {
  static final int MAX_PENDING_LINES = 512;
  static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;
  static final int MAX_BATCH_LINES = 64;
  private static final long DROP_REPORT_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

  private static final ArrayBlockingQueue<PendingLine> QUEUE =
      new ArrayBlockingQueue<>(MAX_PENDING_LINES);
  private static final AtomicLong DROPPED = new AtomicLong();
  private static final AtomicLong WRITE_FAILURES = new AtomicLong();
  private static final AtomicLong LAST_REPORTED_DROPS = new AtomicLong();
  private static final AtomicLong LAST_DROP_REPORT_NANOS = new AtomicLong();
  private static final AtomicLong OUTSTANDING = new AtomicLong();
  private static final AtomicBoolean WRITING = new AtomicBoolean();
  private static final Object FLUSH_MONITOR = new Object();

  static {
    Thread worker =
        new Thread(RtsAsyncJsonlWriter::runWriter, "RTSBuilding diagnostic JSONL writer");
    worker.setDaemon(true);
    worker.start();
  }

  private RtsAsyncJsonlWriter() {}

  public static void append(Path file, String line) {
    if (file == null || line == null || line.isEmpty()) return;
    Path normalized;
    try {
      normalized = file.toAbsolutePath().normalize();
    } catch (RuntimeException failure) {
      WRITE_FAILURES.incrementAndGet();
      return;
    }
    OUTSTANDING.incrementAndGet();
    if (!QUEUE.offer(new PendingLine(normalized, line))) {
      OUTSTANDING.decrementAndGet();
      DROPPED.incrementAndGet();
      reportDropsIfDue();
    }
  }

  public static long droppedLines() {
    return DROPPED.get();
  }

  public static long writeFailures() {
    return WRITE_FAILURES.get();
  }

  public static int pendingLines() {
    return QUEUE.size();
  }

  /** 正常停服时尽力冲刷；超时只返回 false，不改变停服语义。 */
  public static boolean flush(Duration timeout) {
    if (timeout == null || timeout.isNegative()) return false;
    long timeoutNanos = timeout.toNanos();
    long now = System.nanoTime();
    long deadline = now > Long.MAX_VALUE - timeoutNanos ? Long.MAX_VALUE : now + timeoutNanos;
    synchronized (FLUSH_MONITOR) {
      while (OUTSTANDING.get() > 0L) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) return false;
        try {
          TimeUnit.NANOSECONDS.timedWait(FLUSH_MONITOR, remaining);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return true;
  }

  private static void runWriter() {
    List<PendingLine> batch = new ArrayList<>(MAX_BATCH_LINES);
    while (true) {
      try {
        PendingLine first = QUEUE.take();
        batch.add(first);
        QUEUE.drainTo(batch, MAX_BATCH_LINES - 1);
        WRITING.set(true);
        writeBatch(batch);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return;
      } catch (RuntimeException ignored) {
        WRITE_FAILURES.incrementAndGet();
      } finally {
        OUTSTANDING.addAndGet(-batch.size());
        batch.clear();
        WRITING.set(false);
        synchronized (FLUSH_MONITOR) {
          FLUSH_MONITOR.notifyAll();
        }
      }
    }
  }

  private static void writeBatch(List<PendingLine> batch) {
    Map<Path, StringBuilder> grouped = new LinkedHashMap<>();
    for (PendingLine pending : batch) {
      grouped
          .computeIfAbsent(pending.file(), ignored -> new StringBuilder())
          .append(pending.line());
    }
    for (Map.Entry<Path, StringBuilder> entry : grouped.entrySet()) {
      write(entry.getKey(), entry.getValue().toString());
    }
  }

  private static void write(Path file, String text) {
    try {
      Path parent = file.getParent();
      if (parent != null) Files.createDirectories(parent);
      rotateIfNeeded(file, text.getBytes(StandardCharsets.UTF_8).length);
      Files.writeString(
          file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException | RuntimeException failure) {
      WRITE_FAILURES.incrementAndGet();
    }
  }

  private static void rotateIfNeeded(Path file, int incomingBytes) throws IOException {
    long currentBytes = Files.isRegularFile(file) ? Files.size(file) : 0L;
    if (currentBytes + incomingBytes <= MAX_FILE_BYTES) return;
    Path second = sibling(file, 2);
    Path third = sibling(file, 3);
    if (Files.exists(second)) Files.move(second, third, StandardCopyOption.REPLACE_EXISTING);
    if (Files.exists(file)) Files.move(file, second, StandardCopyOption.REPLACE_EXISTING);
  }

  private static Path sibling(Path file, int index) {
    return file.resolveSibling(file.getFileName() + "." + index);
  }

  private static void reportDropsIfDue() {
    long now = System.nanoTime();
    long previous = LAST_DROP_REPORT_NANOS.get();
    if (now - previous < DROP_REPORT_INTERVAL_NANOS
        || !LAST_DROP_REPORT_NANOS.compareAndSet(previous, now)) return;
    long total = DROPPED.get();
    long delta = total - LAST_REPORTED_DROPS.getAndSet(total);
    if (delta > 0L) {
      RtsbuildingMod.LOGGER.warn(
          "[RTS-DIAG] schema=2 event=JSONL_DROPPED dropped={} dropped_total={} pending={}",
          delta,
          total,
          QUEUE.size());
    }
  }

  private record PendingLine(Path file, String line) {}
}
