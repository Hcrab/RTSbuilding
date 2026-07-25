package com.rtsbuilding.rtsbuilding.client.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLegacySurfaceCleanupContractTest {
    @Test
    void uiStateAndHistoryUseTheFormalSharedConstants() throws IOException {
        Path legacyConstants = Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreenConstants.java");
        String uiStateManager = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/state/RtsScreenUiStateManager.java"));
        String historyManager = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/history/ServerHistoryManager.java"));

        assertFalse(Files.exists(legacyConstants));
        assertTrue(uiStateManager.contains(
                "import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;"));
        assertTrue(historyManager.contains("RtsHistoryConstants.SHAPE_HISTORY_LIMIT"));
        assertFalse(historyManager.contains("BuilderScreenConstants"));
    }

    @Test
    void obsoleteQuickBuildShapeStateTexturesStayRemoved() throws IOException {
        Path quickBuildTextures = Path.of(
                "src/main/resources/assets/rtsbuilding/textures/gui/quickbuild");
        try (Stream<Path> files = Files.list(quickBuildTextures)) {
            assertFalse(files.map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.matches("shape_.+_(inactive|hover|active)\\.png")));
        }
    }
}
