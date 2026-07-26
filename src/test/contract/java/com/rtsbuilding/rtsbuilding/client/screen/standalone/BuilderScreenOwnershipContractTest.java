package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 BuilderScreen 终局拆分，防止完整业务职责重新回流主屏幕。 */
class BuilderScreenOwnershipContractTest {
    private static final Path ROOT = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone");
    private static final List<String> OWNERS = List.of(
            "BuilderScreenComponentState.java",
            "BuilderScreenLifecycleOwner.java",
            "BuilderScreenPointerActionOwner.java",
            "BuilderScreenPointerGestureOwner.java",
            "BuilderScreenKeyboardActionOwner.java",
            "BuilderScreenKeyboardSessionOwner.java",
            "BuilderScreenRenderOwner.java",
            "BuilderScreenWindowActionOwner.java",
            "BuilderScreenModeSessionOwner.java",
            "BuilderScreenWorldQueryOwner.java",
            "BuilderScreenPreviewQueryOwner.java");

    @Test
    void screenAndEveryDedicatedOwnerStayBelowTheirHardLimits() throws IOException {
        assertLineLimit("BuilderScreen.java", 700);
        for (String owner : OWNERS) {
            assertLineLimit(owner, 500);
        }
    }

    @Test
    void mainScreenRetainsOnlyBoundariesAndDelegatesConcreteDuties() throws IOException {
        String screen = source("BuilderScreen.java");
        assertTrue(screen.contains(
                "public final class BuilderScreen extends BuilderScreenComponentState"));
        assertTrue(screen.contains("this.lifecycleOwner.tick()"));
        assertTrue(screen.contains("this.pointerActionOwner.handleWorldClickActions("));
        assertTrue(screen.contains("this.renderOwner.render("));
        assertTrue(screen.contains("this.previewQueryOwner.getBlueprintGhostPreview()"));

        assertFalse(screen.contains("RtsCullingWorldInput.handleWorldAction("));
        assertFalse(screen.contains("this.cameraInput.handleRightDrag("));
        assertFalse(screen.contains("guiGraphics.fill(0, 0, this.width"));
        assertFalse(screen.contains("RtsUltimineCollector.collect("));
    }

    private static String source(String file) throws IOException {
        return Files.readString(ROOT.resolve(file), StandardCharsets.UTF_8);
    }

    private static void assertLineLimit(String file, int limit) throws IOException {
        long lines;
        try (var stream = Files.lines(ROOT.resolve(file), StandardCharsets.UTF_8)) {
            lines = stream.count();
        }
        assertTrue(lines <= limit, file + " 超过 BuilderScreen 专属硬门禁：" + lines + " > " + limit);
    }
}
