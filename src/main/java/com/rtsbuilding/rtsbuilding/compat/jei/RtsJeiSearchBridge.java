package com.rtsbuilding.rtsbuilding.compat.jei;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * JEI 运行时搜索桥。主模组侧只保存纯 JDK 回调，不在字段、参数或返回值中暴露
 * 任何 JEI 类型；这样正式包未安装 JEI 时，合成终端也可以安全完成类加载。
 * JEI 插件适配层负责把官方运行时接口转换成这两个回调，本类不拥有终端状态。
 */
public final class RtsJeiSearchBridge {
    private static volatile Supplier<String> searchReader;
    private static volatile Consumer<String> searchWriter;

    public static void attach(Supplier<String> reader, Consumer<String> writer) {
        searchReader = reader;
        searchWriter = writer;
    }

    public static void detach() {
        searchReader = null;
        searchWriter = null;
    }

    public static String getSearchText() {
        Supplier<String> reader = searchReader;
        return reader == null ? "" : reader.get();
    }

    public static boolean isAvailable() {
        return searchReader != null && searchWriter != null;
    }

    public static void setSearchText(String value) {
        Consumer<String> writer = searchWriter;
        if (writer != null) {
            writer.accept(value == null ? "" : value);
        }
    }

    private RtsJeiSearchBridge() {
    }
}
