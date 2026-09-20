package com.rtsbuilding.rtsbuilding.common.config;

import com.electronwill.nightconfig.toml.TomlWriter;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 记录配置框架接管前读取到的完整 TOML 文本。
 *
 * <p>适配器必须在调用 ConfigSpec 的迁移/保存入口前创建此快照；空文本只表示真实
 * 新文件，读取失败用 {@code failure=true} 区分，绝不能退化成“新文件”并覆盖旧值。</p>
 */
public record RtsServerConfigRawSnapshot(String source, String contents, boolean failure) {
    public RtsServerConfigRawSnapshot {
        source = source == null ? "" : source;
        contents = contents == null ? "" : contents;
    }

    public static RtsServerConfigRawSnapshot read(String source, java.util.function.Supplier<String> reader) {
        Objects.requireNonNull(reader, "reader");
        try {
            String contents = reader.get();
            return new RtsServerConfigRawSnapshot(source, contents, false);
        } catch (RuntimeException failure) {
            return new RtsServerConfigRawSnapshot(source,
                    "读取配置原始文本失败: " + failure.getClass().getSimpleName(), true);
        }
    }

    /**
     * 在 loader 已经读到、但尚未交给 ConfigSpec 修正的配置对象上制作完整 TOML 快照。
     * 适配器只负责调用本方法和转交 loader；迁移算法仍然只读取快照文本。
     */
    public static RtsServerConfigRawSnapshot fromConfig(String source,
            com.electronwill.nightconfig.core.UnmodifiableConfig config) {
        if (config == null) {
            return new RtsServerConfigRawSnapshot(source, "配置对象为空", true);
        }
        try {
            StringWriter writer = new StringWriter();
            new TomlWriter().write(config, writer);
            return new RtsServerConfigRawSnapshot(source, writer.toString(), false);
        } catch (RuntimeException failure) {
            return new RtsServerConfigRawSnapshot(source,
                    "序列化配置原始对象失败: " + failure.getClass().getSimpleName(), true);
        }
    }

    public boolean isNewFile() {
        return !failure && contents.isBlank();
    }

    public boolean hasBytes() {
        return !failure && !contents.isEmpty();
    }

    public byte[] utf8() {
        return contents.getBytes(StandardCharsets.UTF_8);
    }
}
