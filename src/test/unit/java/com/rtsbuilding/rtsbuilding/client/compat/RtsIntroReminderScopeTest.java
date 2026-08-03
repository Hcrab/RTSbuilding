package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.io.File;
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
        Path root = Path.of("build", "client-smoke", "saves", "Demo", "..", "Demo");

        assertEquals(
                RtsIntroReminderScope.singleplayerKey(root.normalize()),
                RtsIntroReminderScope.singleplayerKey(root));
    }

    @Test
    void differentWorldDirectoriesRemainIndependent() {
        assertNotEquals(
                RtsIntroReminderScope.singleplayerKey(Path.of("build", "saves", "World-A")),
                RtsIntroReminderScope.singleplayerKey(Path.of("build", "saves", "World-B")));
    }

    @Test
    void unstableScopeDoesNotCreateGlobalFallbackKey() {
        assertEquals("", RtsIntroReminderScope.serverKey(" "));
        assertEquals("", RtsIntroReminderScope.singleplayerKey(null));
    }

    @Test
    void integratedServerFolderBuildsStableWorldKeyWithoutClientSaveHandler() {
        Path gameDirectory = Path.of("build", "client-smoke");

        assertEquals(
                RtsIntroReminderScope.singleplayerKey(gameDirectory.resolve("saves").resolve("New World")),
                RtsIntroReminderScope.singleplayerKey(gameDirectory, "New World"));
    }

    @Test
    void unavailableOrEscapingIntegratedServerFolderDefersReminder() {
        Path gameDirectory = Path.of("build", "client-smoke");

        assertEquals("", RtsIntroReminderScope.singleplayerKey(null, "New World"));
        assertEquals("", RtsIntroReminderScope.singleplayerKey(gameDirectory, null));
        assertEquals("", RtsIntroReminderScope.singleplayerKey(gameDirectory, "  "));
        assertEquals("", RtsIntroReminderScope.singleplayerKey(
                gameDirectory, ".." + File.separator + "outside"));
    }
}
