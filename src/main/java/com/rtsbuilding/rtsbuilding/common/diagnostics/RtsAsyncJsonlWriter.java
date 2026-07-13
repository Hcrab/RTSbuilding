package com.rtsbuilding.rtsbuilding.common.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 开发者诊断专用的有界异步 JSONL 写入器。
 *
 * <p>它不负责组织指标，也不保证进程崩溃时刷新最后一行。队列满时宁可丢弃诊断行，
 * 也不能让磁盘速度或日志洪水阻塞客户端帧线程、服务端 Tick 或无限占用内存。</p>
 */
public final class RtsAsyncJsonlWriter {
    private static final int MAX_PENDING_LINES = 256;
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_PENDING_LINES),
            task -> {
                Thread thread = new Thread(task, "RTSBuilding developer JSONL writer");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    private RtsAsyncJsonlWriter() {
    }

    public static void append(Path file, String line) {
        if (file == null || line == null || line.isEmpty()) return;
        WRITER.execute(() -> write(file, line));
    }

    private static void write(Path file, String line) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 诊断日志失败不能改变游戏状态，也不能递归制造更多日志。
        }
    }
}
