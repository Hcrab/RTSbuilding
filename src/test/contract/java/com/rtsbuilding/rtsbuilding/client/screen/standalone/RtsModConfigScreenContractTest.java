package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsModConfigScreenContractTest {
    @Test
    void moduleConfigScreenDoesNotDuplicateClientVisualSettings() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsModConfigScreen.java"));

        assertFalse(source.contains("placementBlockGhostPreview"));
        assertFalse(source.contains("placeBlockGhostAnimation"));
        assertFalse(source.contains("destroyBlockGhostAnimation"));
        assertFalse(source.contains("placementWireframePreview"));
        assertFalse(source.contains("placeWireframeAnimation"));
        assertFalse(source.contains("destroyWireframeAnimation"));
        assertFalse(source.contains("rangeDestroySkeleton"));
        assertFalse(source.contains("config.rtsbuilding.section.rendering"));
    }

    @Test
    void moduleConfigScreenExposesServerAreaMineLimits() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsModConfigScreen.java"));

        assertTrue(source.contains("RtsServerConfigField"));
        assertTrue(source.contains("saveServerConfig"));
        assertTrue(source.contains("baselineRevision"));
        assertFalse(source.contains("Config.saveGeneralSettings"));
        assertFalse(source.contains("Config.saveAreaMineLimitSettings"));
        assertFalse(source.contains("getSingleplayerServer"));
        assertFalse(source.contains("area_mine_max_width"));
        assertFalse(source.contains("area_mine_max_height"));
        assertFalse(source.contains("area_mine_max_depth"));
        assertFalse(source.contains("area_destroy_max_targets"));
    }

    @Test
    void screenLifecyclePreservesDraftsAndUsesImmediateWorldInputState() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsModConfigScreen.java"));

        assertTrue(source.contains("if (!this.initialized)"));
        assertTrue(source.contains("captureVisibleDrafts();"));
        assertTrue(source.contains("box.setResponder"));
        assertTrue(source.contains("worldLayout()"));
        assertTrue(source.contains("GLFW.GLFW_KEY_ESCAPE"));
        assertTrue(source.contains("closeWithoutSaving()"));
        assertTrue(source.contains("setFocused(null)"));
        assertFalse(source.contains("? savePersonal() : saveWorld()"));
    }

    @Test
    void generalSettingsSavePathDoesNotWriteClientVisualConfig() throws IOException {
        String config = Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String generalSave = slice(config, "public static void saveGeneralSettings", "public static void saveAreaMineLimitSettings");
        String areaSave = slice(config, "public static void saveAreaMineLimitSettings", "public static boolean isPlacementBlockGhostPreviewEnabled");

        assertFalse(generalSave.contains("CLIENT_SPEC.save()"));
        assertFalse(generalSave.contains("USE_BLOCK_GHOST_PREVIEW"));
        assertFalse(generalSave.contains("USE_WIREFRAME_PREVIEW"));
        assertTrue(areaSave.contains("SERVER_SPEC.save()"));
        assertTrue(areaSave.contains("MAX_SELECTION_VOLUME.set"));
        assertFalse(areaSave.contains("AREA_MINE_MAX_WIDTH.set"));
        assertFalse(areaSave.contains("AREA_MINE_MAX_HEIGHT.set"));
        assertFalse(areaSave.contains("AREA_MINE_MAX_DEPTH.set"));
        assertFalse(areaSave.contains("AREA_DESTROY_MAX_TARGETS.set"));
    }

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, "Missing start marker: " + start);
        assertTrue(endIndex > startIndex, "Missing end marker after: " + start);
        return source.substring(startIndex, endIndex);
    }
}
