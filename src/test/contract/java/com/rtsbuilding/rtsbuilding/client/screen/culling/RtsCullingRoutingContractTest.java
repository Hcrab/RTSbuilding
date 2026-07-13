package com.rtsbuilding.rtsbuilding.client.screen.culling;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingRoutingContractTest {
    @Test
    void embeddiumCullingUsesItsAreaRebuildEntryWithoutBecomingARequiredDependency() throws IOException {
        String state = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/culling/RtsCullingClientState.java"));
        String manager = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/culling/RtsCullingManager.java"));
        String invalidator = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/culling/RtsCullingRenderInvalidator.java"));

        assertTrue(state.contains("volatile RtsCullingManager activeManager"),
                "Embeddium worker threads must see the active culling manager");
        assertTrue(manager.contains("RtsCullingRenderInvalidator.markBlocksDirty"));
        assertTrue(invalidator.contains("Class.forName("),
                "optional Embeddium compatibility must not create a hard class link");
        assertTrue(invalidator.contains("scheduleRebuildForBlockArea"));
        assertTrue(invalidator.contains("minecraft.levelRenderer.setBlocksDirty"),
                "vanilla rendering must retain its own dirty-region path");
    }

    @Test
    void sodium0613UsesLevelSliceAndItsOwnAreaRebuildEntry() throws IOException {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/SodiumLevelSliceMixin.java"));
        String config = Files.readString(Path.of("src/main/resources/rtsbuilding.mixins.json"));
        String invalidator = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/culling/RtsCullingRenderInvalidator.java"));

        assertTrue(mixin.contains("net.caffeinemc.mods.sodium.client.world.LevelSlice"));
        assertTrue(mixin.contains("getBlockState(III)"));
        assertTrue(mixin.contains("getFluidState("));
        assertTrue(mixin.contains("getBlockEntity(III)"));
        assertTrue(config.contains("SodiumLevelSliceMixin"));
        assertTrue(invalidator.contains("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer"));
        assertTrue(invalidator.contains("scheduleRebuildForBlockArea"));
    }

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
    void activeBoxHandleDragRoutesBeforeCullingDragSwallow() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "public boolean mouseDragged");

        int handleDrag = body.indexOf("handleBoxHandleDrag(button, dragX, dragY)");
        int cullingSwallow = body.indexOf("this.cullingManager.isManagementMode() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT");
        assertTrue(handleDrag >= 0, "active blueprint/culling handles should receive drag input");
        assertTrue(cullingSwallow >= 0, "range-culling left drag guard should still exist");
        assertTrue(handleDrag < cullingSwallow,
                "active axis-handle dragging must run before range-culling mode consumes left drags");
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
                "selected range-culling boxes should expose world-space axis handles with optional direction filtering");
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
        assertTrue(overlay.contains("drawNoDepth(CULLING_HANDLE_NO_DEPTH_FILL, cullingHandleFillBuffer)"));
        assertTrue(overlay.contains("drawNoDepth(CULLING_HANDLE_NO_DEPTH_LINES, cullingHandleLineBuffer)"));
        assertTrue(renderer.contains("handleLineBuffer"));
        assertTrue(renderer.contains("handleFillBuffer"));
    }

    @Test
    void blueprintCaptureUsesSharedAnimatedBoxAndHandleRenderer() throws IOException {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/blueprint/BlueprintCaptureRenderer.java"));
        String panel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintPanel.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintCaptureController.java"));

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
}
