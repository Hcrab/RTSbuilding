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
        assertFalse(source.contains("\"unknown\""),
                "身份未稳定时不能写入跨存档共享的 unknown 键");
    }
}
