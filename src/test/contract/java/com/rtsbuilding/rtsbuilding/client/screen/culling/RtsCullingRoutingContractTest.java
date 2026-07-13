package com.rtsbuilding.rtsbuilding.client.screen.culling;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingRoutingContractTest {
    @Test
    void runtimeVerifierRejectsAnUntransformedWorldSlice() {
        assertFalse(RtsCullingMixinVerifier.hasWorldSliceBridge(UntransformedWorldSlice.class));
        assertTrue(RtsCullingMixinVerifier.hasWorldSliceBridge(TransformedWorldSlice.class));
    }

    @Test
    void forge1201EmbeddiumUsesTheActualWorldSliceEntryPoints() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/EmbeddiumWorldSliceMixin.java"));

        assertTrue(body.contains("me.jellysquid.mods.sodium.client.world.WorldSlice"),
                "Forge 1.20.1 Embeddium still uses Sodium's legacy WorldSlice package");
        assertTrue(body.contains("m_8055_"), "production block-state reads must be intercepted");
        assertTrue(body.contains("getBlockState(III)"), "integer block-state reads must be intercepted");
        assertTrue(body.contains("m_6425_"), "production fluid-state reads must be intercepted");
        assertTrue(body.contains("m_7702_"), "production block-entity reads must be intercepted");
        assertTrue(body.contains("getBlockEntity(III)"), "integer block-entity reads must be intercepted");
    }

    @Test
    void builderScreenRangeCullingWorldActionDelegatesToDedicatedInput() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
        String body = methodBody(source, "private boolean handleRangeCullingSelectionClick");

        assertTrue(body.contains("RtsCullingWorldInput.handleWorldAction(this.cullingManager, this.cursorPicker)"));
        assertFalse(body.contains("pickBlockHitIgnoringRangeCulling"),
                "range-culling world action must not use the raw picker");
    }

    @Test
    void screenCursorPickerCullingAwareContractUsesNormalBlockHit() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/ScreenCursorPicker.java"));
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
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
        String body = methodBody(source, "public boolean mouseDragged");

        assertFalse(body.contains("this.cullingManager.isManagementMode() && button != GLFW.GLFW_MOUSE_BUTTON_LEFT"),
                "range-culling mode must not consume right-button drags that should rotate the camera");
    }

    @Test
    void activeBoxHandleDragRoutesBeforeCullingDragSwallow() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
        String body = methodBody(source, "public boolean mouseDragged");

        int handleDrag = body.indexOf("handleBoxHandleDrag(button, dragX, dragY)");
        int cullingSwallow = body.indexOf("this.cullingManager.isManagementMode()");
        int rightDrag = body.indexOf("this.cameraInput.handleRightDrag");
        assertTrue(handleDrag >= 0, "active blueprint/culling handles should receive drag input");
        assertTrue(cullingSwallow < 0 || handleDrag < cullingSwallow,
                "active axis-handle dragging must run before any range-culling drag swallow");
        assertTrue(rightDrag < 0 || handleDrag < rightDrag,
                "active axis-handle dragging should run before camera drag fallback");
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
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/culling/RtsCullingRenderer.java"));
        String handles = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/selection/RtsBoxHandleRenderer.java"));

        assertTrue(renderer.contains("RtsBoxHandleRenderer.renderAxisHandles"),
                "selected range-culling boxes should use the shared world-space axis handle renderer");
        assertTrue(handles.contains("RtsCullingAxisHandle.handles(box, allowedDirections)"),
                "selected range-culling boxes should expose world-space axis handles");
        assertTrue(renderer.contains("manager.hoveredHandleDirection()"),
                "hovered direction handle must get a distinct visual state");
        assertTrue(renderer.contains("manager.activeHandleDirection()"),
                "clicked direction handle must get a locked visual state");
        assertTrue(handles.contains("ACTIVE_R"),
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
        assertTrue(overlay.contains("drawNoDepth(CULLING_HANDLE_NO_DEPTH_FILL, cullingHandleFillBuffer)")
                || overlay.contains("drawBuiltBufferNoDepth(CULLING_HANDLE_NO_DEPTH_FILL, cullingHandleFillBuffer)"));
        assertTrue(overlay.contains("drawNoDepth(CULLING_HANDLE_NO_DEPTH_LINES, cullingHandleLineBuffer)")
                || overlay.contains("drawBuiltBufferNoDepth(CULLING_HANDLE_NO_DEPTH_LINES, cullingHandleLineBuffer)"));
        assertTrue(renderer.contains("handleLineBuffer"));
        assertTrue(renderer.contains("handleFillBuffer"));
    }

    @Test
    void blueprintCaptureUsesSharedAnimatedBoxAndHandleRenderer() throws IOException {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/blueprint/BlueprintCaptureRenderer.java"));
        String panel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/blueprint/client/BlueprintPanel.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/blueprint/client/BlueprintCaptureController.java"));

        assertTrue(renderer.contains("BlueprintPanel.getCapturePreviewAabbForRender()"),
                "blueprint capture outline should render from the animated AABB path");
        assertTrue(renderer.contains("RtsBoxHandleRenderer.renderAxisHandles"),
                "blueprint capture handles should share culling handle visuals");
        assertTrue(panel.contains("CAPTURE.previewAabbForRender()"));
        assertTrue(controller.contains("RtsSelectionBoxAnimator"));
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

    private static final class UntransformedWorldSlice {
    }

    private static final class TransformedWorldSlice implements RtsCullingWorldSliceBridge {
    }
}
