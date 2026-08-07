package com.rtsbuilding.rtsbuilding.compat.jei;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * JEI 运行时搜索桥。
 *
 * <p>合成终端只依赖 JDK 回调，不在字段、参数或返回值中暴露 JEI 类型。JEI 插件在运行时可用时接入其搜索框； 未安装 JEI 时两个回调都为空，终端保持正常加载和搜索行为。
 */
public final class RtsJeiSearchBridge {
  private static volatile Supplier<String> searchReader;
  private static volatile Consumer<String> searchWriter;

  private RtsJeiSearchBridge() {}

  public static void attach(Supplier<String> reader, Consumer<String> writer) {
    searchReader = reader;
    searchWriter = writer;
  }

  public static void detach() {
    searchReader = null;
    searchWriter = null;
  }

  public static boolean isAvailable() {
    return searchReader != null && searchWriter != null;
  }

  public static String getSearchText() {
    Supplier<String> reader = searchReader;
    String value = reader == null ? "" : reader.get();
    return value == null ? "" : value;
  }

  public static void setSearchText(String value) {
    Consumer<String> writer = searchWriter;
    if (writer != null) {
      writer.accept(value == null ? "" : value);
    }
  }
}
