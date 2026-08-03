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
        assertTrue(invalidator.contains("minecraft.renderGlobal.markBlockRangeForRenderUpdate"),
                "vanilla rendering must retain its own dirty-region path");
    }

    @Test
    void legacyDispatcherMixinsCullBlocksAndBlockEntities() throws IOException {
        String blockMixin = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/BlockRenderDispatcherMixin.java"));
        String blockEntityMixin = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/BlockEntityRenderDispatcherMixin.java"));
        String config = Files.readString(Path.of("src/main/resources/mixins.rtsbuilding.json"));

        assertTrue(blockMixin.contains("@Mixin(BlockRendererDispatcher.class)"));
        assertTrue(blockMixin.contains("@Inject(method = \"renderBlock\""));
        assertTrue(blockMixin.contains("RtsCullingClientState.shouldCull(pos)"));
        assertTrue(blockEntityMixin.contains("@Mixin(TileEntityRendererDispatcher.class)"));
        assertTrue(blockEntityMixin.contains("RtsCullingClientState.shouldCull(tileEntity.getPos())"));
        assertTrue(config.contains("BlockRenderDispatcherMixin"));
        assertTrue(config.contains("BlockEntityRenderDispatcherMixin"));
    }

    @Test
    void builderScreenRangeCullingWorldActionDelegatesToDedicatedInput() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreenWorldQueryOwner.java"));
        String body = methodBody(source, "boolean handleRangeCullingWorldAction");

        assertTrue(body.contains("RtsCullingWorldInput.handleWorldAction(screen.cullingManager, screen.cursorPicker)"));
        assertFalse(body.contains("pickBlockHitIgnoringRangeCulling"),
                "range-culling world action must not use the raw picker");
    }

    @Test
    void screenCursorPickerCullingAwareContractUsesNormalBlockHit() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenCursorPicker.java"));
        String body = methodBody(source, "public RayTraceResult pickCullingAwareBlockHit");

        assertTrue(body.contains("return pickBlockHit(false);"));
        assertFalse(body.contains("pickBlockHitIgnoringRangeCulling"));
    }

    @Test
    void yellowInteractionTargetUsesCullingAwareRaycast() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/overlay/InteractionTargetRenderer.java"));

        assertTrue(source.contains("RtsCullingRayClipper.clip(origin, direction, MAX_REACH"),
                "yellow interaction target must use the culling-aware raycast");
        assertTrue(source.contains("RtsCullingClientState.shouldCull(pos)"));
    }

    @Test
    void cullingModeOnlySwallowsLeftDragSoRightDragCanRotateCamera() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreenPointerGestureOwner.java"));
        String body = methodBody(source, "public boolean mouseDragged");

        assertTrue(body.contains("screen.cullingManager.isManagementMode() && button == 0"),
                "range-culling mode should only consume left-button box-selection drags");
    }

    @Test
    void activeBoxHandleDragRoutesBeforeCullingDragSwallow() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreenPointerGestureOwner.java"));
        String body = methodBody(source, "public boolean mouseDragged");

        int handleDrag = body.indexOf("handleBoxHandleDrag(button, dragX, dragY)");
        int cullingSwallow = body.indexOf("screen.cullingManager.isManagementMode() && button == 0");
        assertTrue(handleDrag >= 0, "active blueprint/culling handles should receive drag input");
        assertTrue(cullingSwallow >= 0, "range-culling left drag guard should still exist");
        assertTrue(handleDrag < cullingSwallow,
                "active axis-handle dragging must run before range-culling mode consumes left drags");
    }

    @Test
    void cullingPanelCloseButtonClosesManagementMode() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/culling/RtsCullingPanel.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/culling/CullingUiAdapter.java"));
        String constructor = methodBody(source, "public RtsCullingPanel");
        String closeBody = methodBody(source, "protected void onClose");

        assertTrue(constructor.contains("this.closable = true"));
        assertTrue(closeBody.contains("CullingUiAction.Type.CLOSE"),
                "关闭按钮必须提交共享 Core 的 CLOSE 动作");
        assertTrue(adapter.contains("case CLOSE: manager.closeManagementMode(); break;"),
                "生产适配器必须把共享 CLOSE 命令落到真实管理器");
    }

    @Test
    void placementPacketsRevealLikelyCulledPlacementPositions() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/network/RtsClientPacketGateway.java"));

        assertTrue(source.contains("RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.sideHit)"),
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
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/culling/RtsCullingRenderer.java"));
        String handleRenderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/selection/RtsBoxHandleRenderer.java"));
        String drawOwnedBuffers = methodBody(handleRenderer, "private static void drawOwnedBuffers");

        assertTrue(renderer.contains("private static final BufferBuilder FILL_BUFFER"));
        assertTrue(renderer.contains("private static final BufferBuilder LINE_BUFFER"));
        assertTrue(renderer.contains("RtsBoxHandleRenderer.renderAxisHandles"));
        assertTrue(handleRenderer.contains("private static final BufferBuilder FILL_BUFFER"));
        assertTrue(handleRenderer.contains("private static final BufferBuilder LINE_BUFFER"));
        assertTrue(drawOwnedBuffers.contains("GlStateManager.disableDepth()"),
                "range-culling axis handles should render without depth testing");
        assertTrue(drawOwnedBuffers.contains("GlStateManager.depthMask(false)"));
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
