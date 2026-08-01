package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 锁定鼠标按下的前后层级，避免拆出路由器后浮窗或特殊选择模式被世界点击穿透。
 */
class BuilderScreenPointerClickRouterContractTest {
    @Test
    void routesFrontLayersBeforePanelsAndWorld() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/"
                        + "BuilderScreenPointerClickRouter.java"));

        int placementWheel = source.indexOf("handlePlacementStateWheelClick");
        int modeWheel = source.indexOf("handleModeWheelClick");
        int overlay = source.indexOf("handleOverlayClicks");
        int capture = source.indexOf("handleBlueprintCaptureClicks");
        int home = source.indexOf("handleHomeSelectionClicks");
        int culling = source.indexOf("handleRangeCullingSelectionClick");
        int areaMine = source.indexOf("handleAreaMineClickBlock");
        int panel = source.indexOf("handleLeftClickInteractions");
        int world = source.indexOf("handleWorldClickActions");
        int fallback = source.indexOf("forwardUnhandledMouseClicked");

        assertTrue(placementWheel >= 0);
        assertTrue(placementWheel < modeWheel);
        assertTrue(modeWheel < overlay);
        assertTrue(overlay < capture);
        assertTrue(capture < home);
        assertTrue(home < culling);
        assertTrue(culling < areaMine);
        assertTrue(areaMine < panel);
        assertTrue(panel < world);
        assertTrue(world < fallback);
    }

    @Test
    void mainScreenOwnsScaleRemapAndDelegatesOnlyOnce() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String method = methodBody(source,
                "public boolean mouseClicked(double mouseX, double mouseY, int button)");

        assertTrue(method.contains("this.guiScaleCoordinator.beginInput()"));
        assertTrue(method.contains("this.pointerClickRouter.mouseClicked(mouseX, mouseY, button)"));
    }

    @Test
    void primaryActionRouterDependsOnNarrowHostInsteadOfWholeScreen() throws IOException {
        String router = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/"
                        + "BuilderScreenPrimaryActionRouter.java"));
        String host = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/"
                        + "BuilderScreenPrimaryActionHost.java"));
        String pointer = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/"
                        + "BuilderScreenPointerClickRouter.java"));

        assertTrue(router.contains(
                "private final BuilderScreenPrimaryActionHost host"));
        assertFalse(router.contains("private final BuilderScreen screen"));
        assertTrue(host.contains("boolean isWorldArea("));
        assertTrue(host.contains("boolean handleRangeCullingWorldAction("));
        assertTrue(router.contains("BuilderScreenItemActionHandler itemActions"));
        assertTrue(pointer.contains("private final BuilderScreenInputHost host"));
        assertFalse(pointer.contains("private final BuilderScreen screen"));
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
