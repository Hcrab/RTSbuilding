package com.rtsbuilding.rtsbuilding.client.screen.guide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 从 latest.log 提取固定数量的尾部记录，供玩家主动复制给 AI 排障。
 *
 * <p>本类单次流式读取日志，只在内存保留两个定长队列；它不监听文件、不参与游戏 tick，
 * 也不解释日志内容。通用尾部与 RTSBuilding 尾部故意同时保留：前者提供模组环境和异常
 * 上下文，后者让 AI 快速找到本模组最近发生的操作。
 */
public final class RtsLatestLogExcerpt {
    public static final int LATEST_LINE_LIMIT = 200;
    public static final int RTS_LINE_LIMIT = 50;

    private RtsLatestLogExcerpt() {
    }

    public static Result read(Path latestLog) {
        if (latestLog == null || !Files.isRegularFile(latestLog)) {
            return Result.unavailable();
        }
        Deque<String> latest = new ArrayDeque<>(LATEST_LINE_LIMIT);
        Deque<String> rts = new ArrayDeque<>(RTS_LINE_LIMIT);
        try (Stream<String> lines = Files.lines(latestLog, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                appendBounded(latest, line, LATEST_LINE_LIMIT);
                if (isRtsBuildingLine(line)) {
                    appendBounded(rts, line, RTS_LINE_LIMIT);
                }
            });
            return new Result(String.join("\n", latest), String.join("\n", rts), true);
        } catch (IOException | RuntimeException ignored) {
            return Result.unavailable();
        }
    }

    static boolean isRtsBuildingLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.contains("rtsbuilding")
                || normalized.contains("[workflow]")
                || normalized.contains("[pipeline]")
                || normalized.contains("[ultimine")
                || normalized.contains("[blueprint");
    }

    private static void appendBounded(Deque<String> lines, String line, int limit) {
        if (lines.size() == limit) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    public record Result(String latestLines, String rtsLines, boolean available) {
        private static Result unavailable() {
            return new Result("", "", false);
        }
    }
}
