package com.rtsbuilding.rtsbuilding.common.diagnostics;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** 为一次客户端 JVM 会话生成仅用于诊断关联的 64 位追踪编号。 */
public final class RtsTraceIds {
  public static final long NONE = 0L;

  private static final int SESSION_NONCE = nonZeroNonce();
  private static final AtomicInteger NEXT = new AtomicInteger();

  private RtsTraceIds() {}

  public static long nextClientTraceId() {
    int sequence;
    do {
      sequence = NEXT.incrementAndGet();
    } while (sequence == 0);
    return ((long) SESSION_NONCE << 32) | Integer.toUnsignedLong(sequence);
  }

  public static String format(long traceId) {
    return String.format(Locale.ROOT, "%016x", traceId);
  }

  public static String runId() {
    return String.format(Locale.ROOT, "%08x", SESSION_NONCE);
  }

  private static int nonZeroNonce() {
    int value;
    SecureRandom random = new SecureRandom();
    do {
      value = random.nextInt();
    } while (value == 0);
    return value;
  }
}
