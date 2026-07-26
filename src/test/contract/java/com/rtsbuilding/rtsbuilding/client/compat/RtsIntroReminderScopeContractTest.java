package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsIntroReminderScopeContractTest {
    @Test
    void onboardingUsesOnlyStableWorldOrServerIdentity() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsClientOnboardingReminder.java"));

        assertTrue(source.contains("getWorldPath(LevelResource.ROOT)"),
                "单人提醒必须绑定实际世界根目录，不能只按可重复的存档显示名区分");
        assertTrue(source.contains("RtsIntroReminderScope.serverKey"),
                "多人提醒必须绑定当前服务器地址");
        assertFalse(source.contains("\"level:\""),
                "维度不能成为独立提醒作用域，否则跨维度后会重复提醒");
        String identityMethod = methodBody(source, "private static String currentReminderKey");
        assertFalse(identityMethod.contains("\"unknown\""),
                "身份未稳定时不能写入跨存档共享的 unknown 键");
    }

    @Test
    void onboardingReadsTheLoadedForgeVersionInsteadOfHardcodingARelease() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsClientOnboardingReminder.java"));

        assertTrue(source.contains("currentModVersion()"));
        assertTrue(source.contains("ModList.get().getModContainerById(RtsbuildingMod.MODID)"));
        for (String language : new String[]{"en_us", "zh_cn", "zh_tw", "zh_hk"}) {
            String translations = Files.readString(Path.of(
                    "src/main/resources/assets/rtsbuilding/lang", language + ".json"));
            String line = translations.lines()
                    .filter(value -> value.contains("chat.rtsbuilding.intro.version_warning"))
                    .findFirst()
                    .orElseThrow();
            assertFalse(line.contains("1.1."), language + " 不能硬编码发布版本或回退版本");
            assertTrue(line.chars().filter(value -> value == '%').count() >= 2,
                    language + " 必须为当前版本与教程链接保留参数");
        }
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(bodyStart, i + 1);
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
