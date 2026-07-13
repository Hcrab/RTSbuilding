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
        String controller = source("client/controller/ClientRtsController.java");
        String state = source("client/state/RtsClientUiStateStore.java");
        String gear = source("client/screen/gear/GearMenuPanel.java");

        assertTrue(controller.contains("float scale = getPanDragSensitivityScale()"));
        assertTrue(controller.contains("getRotateViewSensitivityScale() * this.rotateSensitivity"));
        assertTrue(controller.contains("input.forward * getKeyboardMoveSensitivityScale()"));
        assertTrue(controller.contains("scrollY * getWheelZoomSensitivityScale()"));
        assertTrue(state.contains("panDragSensitivityIndex") && state.contains("wheelZoomSensitivityIndex"));
        assertTrue(gear.contains("PAN_DRAG(\"screen.rtsbuilding.settings.sensitivity.pan_drag\")")
                && gear.contains("ROTATE_VIEW(\"screen.rtsbuilding.settings.sensitivity.rotate_view\")")
                && gear.contains("KEYBOARD_MOVE(\"screen.rtsbuilding.settings.sensitivity.keyboard_move\")")
                && gear.contains("WHEEL_ZOOM(\"screen.rtsbuilding.settings.sensitivity.wheel_zoom\")"));
    }

    @Test
    void forgeSoundPayloadIsClientboundAndUsesBothLimits() throws Exception {
        String packets = source("network/builder/RtsBuilderPackets.java");
        String server = source("server/service/placement/RtsPlacementSound.java");
        String client = source("client/sound/RtsBlockActionSoundPlayer.java");
        String state = source("client/state/RtsClientUiStateStore.java");

        assertTrue(packets.contains("registrar.playToClient(")
                && packets.contains("S2CRtsBlockActionSoundPayload.TYPE"));
        assertTrue(server.contains("Config.remotePlaceSoundsPerTick()"));
        assertTrue(client.contains("RtsClientUiStateStore.getRtsBlockSoundsPerTick()"));
        assertTrue(state.contains("public int rtsBlockSoundsPerTick = 8"));
    }

    @Test
    void nudgeRoutesAreSharedByBlueprintCullingAndQuickBuild() throws Exception {
        String builder = source("client/screen/BuilderScreen.java");
        String blueprint = source("blueprint/client/BlueprintPanel.java");
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
