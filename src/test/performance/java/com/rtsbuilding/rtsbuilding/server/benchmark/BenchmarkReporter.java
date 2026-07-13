package com.rtsbuilding.rtsbuilding.server.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 基准测试结果收集器 / Benchmark Result Collector
 *
 * <p>替代 {@link System#out} 直接输出，将所有 benchmark 结果收集到内存中，
 * 在 JVM 退出时写入 {@code benchmark-results.txt}。</p>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * BenchmarkReporter.record("[Module] metric: %d ns/op", value);
 * }</pre>
 */
public final class BenchmarkReporter {

    private static final String OUTPUT_FILE = "benchmark-results.txt";
    private static final Object LOCK = new Object();
    private static final List<String> RESULTS = new ArrayList<>();
    private static volatile boolean hookRegistered = false;

    private BenchmarkReporter() {
    }

    /**
     * 记录一条 benchmark 结果（格式同 {@link String#format}）。
     * 同时输出到 stdout 和内存队列，JVM 退出时自动写入文件。
     */
    public static void record(String format, Object... args) {
        String line = String.format(format, args);
        synchronized (LOCK) {
            RESULTS.add(line);
        }
        // Also print to stdout for real-time viewing
        System.out.println(line);
        ensureShutdownHook();
    }

    private static void ensureShutdownHook() {
        if (!hookRegistered) {
            synchronized (LOCK) {
                if (!hookRegistered) {
                    Runtime.getRuntime().addShutdownHook(new Thread(BenchmarkReporter::flush));
                    hookRegistered = true;
                }
            }
        }
    }

    /**
     * 将收集到的所有结果写入 benchmark-results.txt。
     * 由 shutdown hook 自动调用，也可手动调用（例如在 Gradle task 中）。
     */
    public static void flush() {
        synchronized (LOCK) {
            if (RESULTS.isEmpty()) {
                return;
            }
            try {
                Path outputPath = Paths.get(OUTPUT_FILE);
                List<String> lines = new ArrayList<>();
                lines.add("╔══════════════════════════════════════════════════════╗");
                lines.add("║  RTS Building — 极限性能测试报告                       ║");
                lines.add("║  Extreme Performance Benchmark Report                ║");
                lines.add("╚══════════════════════════════════════════════════════╝");
                lines.add("生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                lines.add("");

                // Add section separators by detecting category changes
                String currentCategory = "";
                for (String result : RESULTS) {
                    // Extract category from "[RtsXXX]" prefix
                    String category = extractCategory(result);
                    if (!category.equals(currentCategory)) {
                        if (!currentCategory.isEmpty()) {
                            lines.add("");
                        }
                        lines.add("── " + category + " ──────────────────────");
                        currentCategory = category;
                    }
                    lines.add("  " + result);
                }

                lines.add("");
                lines.add("── 测试完成 ──────────────────────────");
                lines.add("共计 " + RESULTS.size() + " 项基准测试记录");

                Files.write(outputPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("[BenchmarkReporter] 结果已写入: " + outputPath.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("[BenchmarkReporter] 写入失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 从一行结果中提取分类名（例如 "[RtsPageCache]" → "RtsPageCache"）。
     */
    private static String extractCategory(String line) {
        if (line == null || line.isEmpty()) {
            return "Unknown";
        }
        int start = line.indexOf('[');
        if (start < 0) return "General";
        int end = line.indexOf(']', start);
        if (end < 0) return "General";
        String tag = line.substring(start + 1, end);
        // Remove trailing benchmark suffix if present
        if (tag.endsWith("Benchmark")) {
            tag = tag.substring(0, tag.length() - "Benchmark".length());
        }
        return tag;
    }
}
