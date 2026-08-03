package com.rtsbuilding.rtsbuilding.common.diagnostics;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 为客户端意图生成只在本次 JVM 会话内唯一的 64 位追踪编号。
 *
 * <p>高 32 位是启动随机数，低 32 位是单调计数。零永久保留给没有客户端因果来源的
 * legacy/服务端主动操作。该身份只用于诊断，不得作为业务幂等键。</p>
 */
public final class RtsTraceIds {
    public static final long NONE = 0L;

    private static final int SESSION_NONCE = nonZeroNonce();
    private static final AtomicInteger NEXT = new AtomicInteger();

    private RtsTraceIds() {
    }

    public static long nextClientTraceId() {
        int sequence;
        do {
            sequence = NEXT.incrementAndGet();
        } while (sequence == 0);
        return ((long) SESSION_NONCE << 32) | Integer.toUnsignedLong(sequence);
    }

    /** 固定 16 位小写十六进制，方便直接跨客户端和服务端搜索。 */
    public static String format(long traceId) {
        return String.format(Locale.ROOT, "%016x", traceId);
    }

    /** 当前启动周期的短标识；只用于日志中的 run 字段。 */
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
