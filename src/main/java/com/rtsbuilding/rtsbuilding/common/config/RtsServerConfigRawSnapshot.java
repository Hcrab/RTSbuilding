package com.rtsbuilding.rtsbuilding.common.config;

import com.electronwill.nightconfig.toml.TomlWriter;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** ConfigSpec 接管前保存的完整原始 TOML；读取失败不伪装成新文件。 */
public record RtsServerConfigRawSnapshot(String source, String contents, boolean failure) {
    public RtsServerConfigRawSnapshot { source = source == null ? "" : source; contents = contents == null ? "" : contents; }
    public static RtsServerConfigRawSnapshot read(String source, java.util.function.Supplier<String> reader) { Objects.requireNonNull(reader, "reader"); try { return new RtsServerConfigRawSnapshot(source, reader.get(), false); } catch (RuntimeException failure) { return new RtsServerConfigRawSnapshot(source, "读取配置原始文本失败: " + failure.getClass().getSimpleName(), true); } }
    /** 在 Forge ConfigSpec 修正前把 loader 交来的完整对象归一化为 TOML 快照。 */
    public static RtsServerConfigRawSnapshot fromConfig(String source, com.electronwill.nightconfig.core.UnmodifiableConfig config) {
        if (config == null) return new RtsServerConfigRawSnapshot(source, "配置对象为空", true);
        try { StringWriter writer = new StringWriter(); new TomlWriter().write(config, writer); return new RtsServerConfigRawSnapshot(source, writer.toString(), false); }
        catch (RuntimeException failure) { return new RtsServerConfigRawSnapshot(source, "序列化配置原始对象失败: " + failure.getClass().getSimpleName(), true); }
    }
    public boolean isNewFile() { return !failure && contents.isBlank(); }
    public boolean hasBytes() { return !failure && !contents.isEmpty(); }
    public byte[] utf8() { return contents.getBytes(StandardCharsets.UTF_8); }
}
