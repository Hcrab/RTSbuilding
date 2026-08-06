package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁住 1.7.10 的旧式语言资源命名与核心翻译键。
 *
 * <p>现代版本使用 en_us.json，而 1.7.10 会精确请求 en_US.lang。仅仅生成内容正确的
 * 小写 .lang 并不能被 JAR 内的资源管理器找到，因此这里同时检查大小写、核心键和值。</p>
 */
class LegacyLanguageResourceContractTest {
    private static final Path LANG = Paths.get("src/main/resources/assets/rtsbuilding/lang");

    @Test
    void legacyLocaleFilesUseCanonicalCase() throws IOException {
        Set<String> names = new HashSet<String>();
        java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(LANG);
        try {
            for (Path file : files) names.add(file.getFileName().toString());
        } finally {
            files.close();
        }

        assertTrue(names.contains("en_US.lang"));
        assertTrue(names.contains("zh_CN.lang"));
        assertTrue(names.contains("zh_TW.lang"));
        assertTrue(names.contains("zh_HK.lang"));
        assertFalse(names.contains("en_us.lang"));
        assertFalse(names.contains("zh_cn.lang"));
    }

    @Test
    void representativeUiKeyHasRealValues() throws IOException {
        assertTranslation("en_US.lang", "screen.rtsbuilding.plugins", "RTS Plugins");
        assertTranslation("zh_CN.lang", "screen.rtsbuilding.plugins", "RTS 插件");
    }

    private static void assertTranslation(String file, String key, String expected) throws IOException {
        List<String> lines = Files.readAllLines(LANG.resolve(file), StandardCharsets.UTF_8);
        assertTrue(lines.contains(key + "=" + expected), file + " 缺少 " + key);
    }
}
