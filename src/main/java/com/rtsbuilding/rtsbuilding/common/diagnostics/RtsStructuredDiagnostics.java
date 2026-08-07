package com.rtsbuilding.rtsbuilding.common.diagnostics;

import java.nio.file.Path;
import java.nio.file.Paths;

/** 将关键诊断写入 schema-2 有界 JSONL；写盘失败永远不反向影响玩法线程。 */
public final class RtsStructuredDiagnostics {
    private RtsStructuredDiagnostics() {}

    public static void appendClient(String event, Object... fields) { append("client", event, fields); }
    public static void appendServer(String event, Object... fields) { append("server", event, fields); }

    private static void append(String side, String event, Object... fields) {
        try {
            Path file = Paths.get(".").toAbsolutePath().normalize().resolve("logs")
                    .resolve("rtsbuilding").resolve("diagnostics-" + side + ".jsonl");
            StringBuilder json = new StringBuilder(256)
                    .append('{').append("\"schema\":2,\"side\":\"").append(escape(side))
                    .append("\",\"event\":\"").append(escape(event)).append('"');
            if (fields != null) {
                for (int i = 0; i + 1 < fields.length; i += 2) {
                    json.append(',').append('"').append(escape(String.valueOf(fields[i]))).append("\":");
                    appendValue(json, fields[i + 1]);
                }
            }
            json.append("}\n");
            RtsAsyncJsonlWriter.append(file, json.toString());
        } catch (RuntimeException ignored) {
            // 诊断必须 fail-open，不能改变游戏行为。
        }
    }

    private static void appendValue(StringBuilder json, Object value) {
        if (value == null) json.append("null");
        else if (value instanceof Number || value instanceof Boolean) json.append(value);
        else json.append('"').append(escape(String.valueOf(value))).append('"');
    }

    static String escape(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': result.append("\\\\"); break;
                case '"': result.append("\\\""); break;
                case '\r': result.append("\\r"); break;
                case '\n': result.append("\\n"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (c < 0x20) result.append(String.format("\\u%04x", Integer.valueOf(c)));
                    else result.append(c);
            }
        }
        return result.toString();
    }
}
