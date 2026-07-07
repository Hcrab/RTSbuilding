package com.rtsbuilding.rtsbuilding.client.screen.culling;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingRoutingContractTest {
    @Test
    void builderScreenRangeCullingWorldActionDelegatesToDedicatedInput() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "private boolean handleRangeCullingWorldAction");

        assertTrue(body.contains("RtsCullingWorldInput.handleWorldAction(this.cullingManager, this.cursorPicker)"));
        assertFalse(body.contains("pickBlockHitIgnoringRangeCulling"),
                "range-culling world action must not use the raw picker");
    }

    @Test
    void screenCursorPickerCullingAwareContractUsesNormalBlockHit() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenCursorPicker.java"));
        String body = methodBody(source, "public BlockHitResult pickCullingAwareBlockHit");

        assertTrue(body.contains("return pickBlockHit(false);"));
        assertFalse(body.contains("pickBlockHitIgnoringRangeCulling"));
    }

    @Test
    void yellowInteractionTargetUsesCullingAwareRaycast() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/overlay/InteractionTargetRenderer.java"));

        assertTrue(source.contains("raycastBlockFromCursorThroughCulling"),
                "yellow interaction target must use the culling-aware raycast");
        assertFalse(source.contains("BlockHitResult blockHit = RaycastHelper.raycastBlockFromCursor(minecraft, camPos, rayEnd, false);"));
    }

    @Test
    void cullingModeOnlySwallowsLeftDragSoRightDragCanRotateCamera() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "public boolean mouseDragged");

        assertTrue(body.contains("this.cullingManager.isManagementMode() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT"),
                "range-culling mode should only consume left-button box-selection drags");
    }

    @Test
    void cullingPanelCloseButtonClosesManagementMode() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/culling/RtsCullingPanel.java"));
        String constructor = methodBody(source, "public RtsCullingPanel");
        String closeBody = methodBody(source, "protected void onClose");

        assertTrue(constructor.contains("this.closable = true"));
        assertTrue(closeBody.contains("manager.closeManagementMode()"));
    }

    @Test
    void placementPacketsRevealLikelyCulledPlacementPositions() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/network/RtsClientPacketGateway.java"));

        assertTrue(source.contains("RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.getDirection())"),
                "client placement packets should reveal likely placement positions inside culling boxes");
    }

    @Test
    void selectedCullingBoxRendersWorldAxisHandles() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/culling/RtsCullingRenderer.java"));

        assertTrue(source.contains("RtsCullingAxisHandle.handles(box)"),
                "selected range-culling boxes should expose world-space axis handles");
        assertTrue(source.contains("manager.hoveredHandleDirection()"),
                "hovered direction handle must get a distinct visual state");
        assertTrue(source.contains("manager.activeHandleDirection()"),
                "clicked direction handle must get a locked visual state");
        assertTrue(source.contains("ACTIVE_R"),
                "locked axis handles should render as the gold active state");
    }

    @Test
    void selectedCullingBoxAxisHandlesRenderWithoutDepthTesting() throws IOException {
        String overlay = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/RtsVisualOverlayRenderer.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/culling/RtsCullingRenderer.java"));

        assertTrue(overlay.contains("CULLING_HANDLE_NO_DEPTH_FILL"),
                "range-culling axis handle fill should have a dedicated no-depth render type");
        assertTrue(overlay.contains("CULLING_HANDLE_NO_DEPTH_LINES"),
                "range-culling axis handle lines should have a dedicated no-depth render type");
        assertTrue(overlay.contains("drawNoDepth(CULLING_HANDLE_NO_DEPTH_FILL, cullingHandleFillBuffer)"));
        assertTrue(overlay.contains("drawNoDepth(CULLING_HANDLE_NO_DEPTH_LINES, cullingHandleLineBuffer)"));
        assertTrue(renderer.contains("handleLineBuffer"));
        assertTrue(renderer.contains("handleFillBuffer"));
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
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
