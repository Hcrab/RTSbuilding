package com.rtsbuilding.rtsbuilding.client.screen.guide;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 从 latest.log 提取固定数量的尾部记录，供玩家主动复制给 AI 排障。
 *
 * <p>本类单次流式读取日志，只在内存保留两个定长队列；它不监听文件、不参与游戏 tick，
 * 也不解释日志内容。通用尾部与 RTSBuilding 尾部故意同时保留：前者提供模组环境和异常
 * 上下文，后者让 AI 快速找到本模组最近发生的操作。
 */
public final class RtsLatestLogExcerpt {
    public static final int LATEST_LINE_LIMIT = 200;
    public static final int RTS_LINE_LIMIT = 200;

    private RtsLatestLogExcerpt() {
    }

    /**
     * 按可靠性顺序尝试多个 latest.log 候选路径。
     *
     * <p>NeoForge 的规范游戏目录是首选；Minecraft 或启动器有时仍会暴露相对的
     * {@code .}，因此调用方可以继续提供兼容回退。这里只读取第一个成功候选，
     * 不会把多个运行目录的日志混在一起。</p>
     */
    public static Result readFirstAvailable(Path... candidates) {
        if (candidates == null || candidates.length == 0) {
            return Result.unavailable();
        }
        Set<Path> visited = new LinkedHashSet<>();
        for (Path candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Path normalized;
            try {
                normalized = candidate.toAbsolutePath().normalize();
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!visited.add(normalized)) {
                continue;
            }
            Result result = read(normalized);
            if (result.available()) {
                return result;
            }
        }
        return Result.unavailable();
    }

    public static Result read(Path latestLog) {
        if (latestLog == null || !Files.isRegularFile(latestLog)) {
            return Result.unavailable();
        }
        Deque<String> latest = new ArrayDeque<>(LATEST_LINE_LIMIT);
        Deque<String> rts = new ArrayDeque<>(RTS_LINE_LIMIT);
        // 整合包日志可能混入第三方模组直接写出的非 UTF-8 字节。诊断摘录应替换坏字符并继续读取，
        // 不能因为一处编码瑕疵把整份存在且可读的 latest.log 判定为“不可用”。
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(latestLog), decoder))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendBounded(latest, line, LATEST_LINE_LIMIT);
                if (isRtsBuildingLine(line)) {
                    appendBounded(rts, line, RTS_LINE_LIMIT);
                }
            }
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
        return normalized.contains("[rts-trace]")
                || normalized.contains("[rts-diag]")
                || normalized.contains("[rts-server-health]")
                || normalized.contains("rtsbuilding")
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
