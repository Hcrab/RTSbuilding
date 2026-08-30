package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 UI 滚轮优先于世界缩放的生产路由。 */
class BuilderScreenScrollRouterContractTest {
    @Test
    void uiOwnersConsumeScrollBeforeWorldFallback() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/"
                        + "BuilderScreenScrollRouter.java"));

        int wheels = source.indexOf("this.placementStateWheel.isOpen()");
        int floating = source.indexOf("this.floatingWindowLayer.mouseScrolled");
        int capture = source.indexOf("BlueprintPanel.mouseScrolledCaptureHeight");
        int culling = source.indexOf("CullingUiAdapter.handleScroll");
        int shapeHandle = source.indexOf(
                "this.shapeController.scrollAdvancedRangeDestroyHandle");
        int bottom = source.indexOf("this.bottomPanel.handleMouseScrolled");
        int shapeHeight = source.indexOf(
                "this.shapeController.handleShapeHeightMouseScrolled");
        int areaMine = source.indexOf("this.controller.adjustAreaMineHeightOffset");
        int camera = source.indexOf("this.controller.queueScroll(scrollY)");

        assertTrue(wheels >= 0);
        assertTrue(wheels < floating);
        assertTrue(floating < capture);
        assertTrue(capture < culling);
        assertTrue(culling < shapeHandle);
        assertTrue(shapeHandle < bottom);
        assertTrue(bottom < shapeHeight);
        assertTrue(shapeHeight < areaMine);
        assertTrue(areaMine < camera);
    }

    @Test
    void mainScreenKeepsScaleRemapBeforeRouter() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source,
                "public boolean mouseScrolled(double mouseX, double mouseY, "
                        + "double scrollX, double scrollY)");

        assertTrue(body.contains("this.guiScaleCoordinator.beginInput()"));
        assertTrue(body.contains("this.scrollRouter.mouseScrolled("));
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
