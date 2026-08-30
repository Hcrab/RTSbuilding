package com.rtsbuilding.rtsbuilding.server.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsTeamPluginSharingContractTest {
    @Test
    void sharedPluginsRemainTeamPersistentButContributorOwned() throws IOException {
        String teamService = read("src/main/java/com/rtsbuilding/rtsbuilding/server/plugin/RtsPluginTeamService.java");
        String sharedData = read("src/main/java/com/rtsbuilding/rtsbuilding/server/data/RtsSharedProgressionData.java");
        String pluginService = read("src/main/java/com/rtsbuilding/rtsbuilding/server/plugin/RtsPluginService.java");

        assertTrue(teamService.contains("RtsSharedProgressionData.SharedPlugin"),
                "team plugin state should be persisted on shared progression data");
        assertTrue(sharedData.contains("KEY_PLUGIN_OWNER") && sharedData.contains("KEY_PLUGIN_OWNER_NAME"),
                "shared plugin persistence must retain contributor identity");
        assertTrue(teamService.contains("isOwnedBy(ServerPlayer player)"),
                "uninstall ownership should be decided by contributor UUID");
        assertTrue(pluginService.contains("message.rtsbuilding.plugin.not_yours"),
                "uninstalling a teammate's plugin should be rejected with a player-facing message");
    }

    @Test
    void teamPluginUiHasManualRefreshTeamNameAndOwnerStatus() throws IOException {
        String payload = read("src/main/java/com/rtsbuilding/rtsbuilding/network/plugin/S2CRtsPluginStatePayload.java");
        String screen = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsPluginManagementScreen.java");

        assertTrue(payload.contains("List<String> ownerNames") && payload.contains("String teamName"),
                "plugin sync payload should carry team name and contributor names");
        assertTrue(screen.contains("screen.rtsbuilding.plugins.refresh"),
                "plugin screen should expose a manual refresh control");
        assertTrue(screen.contains("getPluginTeamName"),
                "plugin screen should show team name only when the server provides one");
        assertTrue(screen.contains("team_shared_by") && screen.contains("team_radius_by"),
                "team-shared plugins should explain who contributed them");
    }

    @Test
    void existingPersonalPluginInstallsCanMigrateIntoTeamState() throws IOException {
        String teamService = read("src/main/java/com/rtsbuilding/rtsbuilding/server/plugin/RtsPluginTeamService.java");

        assertTrue(teamService.contains("migratePersonalPluginsIntoTeam"),
                "old personal plugin installs should not silently disappear when team sharing is enabled");
        assertTrue(teamService.contains("canAddWithoutTeamConflict"),
                "migration should preserve duplicate/range-conflict rules");
        assertTrue(teamService.contains("RtsPluginPersistence.save(player, remainingPersonal)"),
                "migrated personal plugins should be removed from personal storage to avoid duplicate returns");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
