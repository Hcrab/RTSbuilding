package com.rtsbuilding.rtsbuilding.common.diagnostics;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** 将关键诊断事件写入有界 JSONL；写盘失败永远不影响玩法线程。 */
public final class RtsStructuredDiagnostics {
  private RtsStructuredDiagnostics() {}

  public static void appendClient(String event, Object... fields) {
    append("client", event, fields);
  }

  public static void appendServer(String event, Object... fields) {
    append("server", event, fields);
  }

  private static void append(String side, String event, Object... fields) {
    try {
      Path file =
          FabricLoader.getInstance()
              .getGameDir()
              .resolve("logs")
              .resolve("rtsbuilding")
              .resolve("diagnostics-" + side + ".jsonl");
      StringBuilder json =
          new StringBuilder(256)
              .append('{')
              .append("\"schema\":2,\"side\":\"")
              .append(escape(side))
              .append("\",\"event\":\"")
              .append(escape(event))
              .append('\"');
      if (fields != null) {
        for (int i = 0; i + 1 < fields.length; i += 2) {
          String key = String.valueOf(fields[i]);
          Object value = fields[i + 1];
          json.append(',').append('\"').append(escape(key)).append("\":");
          appendValue(json, value);
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
    else json.append('\"').append(escape(String.valueOf(value))).append('\"');
  }

  private static String escape(String value) {
    if (value == null) return "";
    StringBuilder result = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> result.append("\\\\");
        case '\"' -> result.append("\\\"");
        case '\r' -> result.append("\\r");
        case '\n' -> result.append("\\n");
        case '\t' -> result.append("\\t");
        default -> {
          if (c < 0x20) result.append(String.format("\\u%04x", (int) c));
          else result.append(c);
        }
      }
    }
    return result.toString();
  }
}
