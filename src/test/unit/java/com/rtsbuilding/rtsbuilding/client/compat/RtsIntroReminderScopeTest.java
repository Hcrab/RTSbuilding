package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RtsIntroReminderScopeTest {
    @Test
    void serverAddressIsTrimmedAndCaseInsensitive() {
        assertEquals("server:play.example.com:25565",
                RtsIntroReminderScope.serverKey("  PLAY.Example.COM:25565  "));
    }

    @Test
    void singleplayerScopeUsesNormalizedWorldDirectory() {
        Path root = Path.of("E:", "Minecraft", "saves", "Demo", "..", "Demo");

        assertEquals(
                RtsIntroReminderScope.singleplayerKey(root.normalize()),
                RtsIntroReminderScope.singleplayerKey(root));
    }

    @Test
    void differentWorldDirectoriesRemainIndependent() {
        assertNotEquals(
                RtsIntroReminderScope.singleplayerKey(Path.of("E:", "Minecraft", "saves", "World-A")),
                RtsIntroReminderScope.singleplayerKey(Path.of("E:", "Minecraft", "saves", "World-B")));
    }

    @Test
    void unstableScopeDoesNotCreateGlobalFallbackKey() {
        assertEquals("", RtsIntroReminderScope.serverKey(" "));
        assertEquals("", RtsIntroReminderScope.singleplayerKey(null));
    }
}
