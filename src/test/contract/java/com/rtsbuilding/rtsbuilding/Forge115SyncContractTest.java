package com.rtsbuilding.rtsbuilding;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Forge115SyncContractTest {
    @Test
    void legacyMigrationAndDependencyTooltipsAreWired() throws Exception {
        String progression = source("server/progression/RtsProgressionManager.java");
        String migration = source("server/plugin/RtsLegacySkillTreeMigration.java");
        String tooltip = source("server/plugin/RtsPluginItem.java");

        assertTrue(progression.contains("RtsPluginService.migrateLegacySkillTree(player)"));
        assertTrue(migration.contains("plugin_migration_version"));
        assertTrue(migration.contains("legacyUnlockedNodes(sharedKey)"));
        assertTrue(tooltip.contains("dependencies.hold_ctrl"));
        assertTrue(tooltip.contains("chain_break_plugin\", \"area_destroy_plugin\", \"blueprint_plugin"));
    }

    @Test
    void fourSensitivityChannelsReachTheirOwnInputPaths() throws Exception {
        String camera = source("client/service/CameraOrbitService.java");
        String state = source("common/persist/RtsClientUiStateStore.java");
        String settings = Files.readString(Path.of(
                "src/uiCore/java/com/rtsbuilding/rtsbuilding/uicore/settings/SettingsId.java"));

        assertTrue(camera.contains("float scale = getPanDragSensitivityScale()"));
        assertTrue(camera.contains("getRotateViewSensitivityScale() * this.rotateSensitivity"));
        assertTrue(camera.contains("float keyboardScale = getKeyboardMoveSensitivityScale()"));
        assertTrue(camera.contains("scrollY * getWheelZoomSensitivityScale()"));
        assertTrue(state.contains("panDragSensitivityIndex") && state.contains("wheelZoomSensitivityIndex"));
        assertTrue(settings.contains("PAN_DRAG_SENSITIVITY")
                && settings.contains("ROTATE_VIEW_SENSITIVITY")
                && settings.contains("KEYBOARD_MOVE_SENSITIVITY")
                && settings.contains("WHEEL_ZOOM_SENSITIVITY"));
    }

    @Test
    void forgeSoundPayloadIsClientboundAndUsesBothLimits() throws Exception {
        String packets = source("network/builder/RtsBuilderPackets.java");
        String server = source("server/service/placement/RtsPlacementSound.java");
        String client = source("client/sound/RtsBlockActionSoundPlayer.java");
        String state = source("common/persist/RtsClientUiStateStore.java");

        assertTrue(packets.contains("registrar.playToClient(")
                && packets.contains("S2CRtsBlockActionSoundPayload.TYPE"));
        assertTrue(server.contains("Config.remotePlaceSoundsPerTick()"));
        assertTrue(client.contains("RtsClientUiStateStore.getRtsBlockSoundsPerTick()"));
        assertTrue(state.contains("public int blockSoundsPerTick = 8"));
    }

    @Test
    void nudgeRoutesAreSharedByBlueprintCullingAndQuickBuild() throws Exception {
        String builder = source("client/screen/standalone/BuilderScreen.java");
        String blueprint = source("client/screen/blueprint/BlueprintPanel.java");
        String keys = source("client/bootstrap/ClientKeyMappings.java");

        assertTrue(builder.contains("this.cullingManager.nudgeSelectedBox"));
        assertTrue(builder.contains("this.shapeController.nudgeCurrentShapeSelection"));
        assertTrue(blueprint.contains("RtsSelectionNudge.fromKey"));
        assertTrue(keys.contains("GLFW.GLFW_KEY_LEFT_SHIFT")
                && keys.contains("SELECTION_NUDGE_FORWARD"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
