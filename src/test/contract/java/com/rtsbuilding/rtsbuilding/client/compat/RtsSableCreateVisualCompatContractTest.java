package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsSableCreateVisualCompatContractTest {
    @Test
    void sableHighlightsAndGhostsUsePrecisionSafeBlockFrames() throws Exception {
        String spatialCompat = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/sable/RtsSableClientSpatialCompat.java");
        String targetRenderer = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/overlay/InteractionTargetRenderer.java");
        String ghostRenderer = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/BuildGhostModelRenderer.java");

        assertTrue(spatialCompat.contains("renderPose.transformPosition(Vec3.atLowerCornerOf(logicalPos))"),
                "Sable block frames must project the logical block origin with double precision first.");
        assertTrue(spatialCompat.contains("poseStack.mulPose(new Quaternionf().set(renderPose.orientation()))"),
                "Sable block frames must preserve the ship rotation after anchoring the block origin.");
        assertTrue(targetRenderer.contains("applyBlockRenderFrame"),
                "Hovered block outlines must use the precision-safe Sable block frame.");
        assertTrue(ghostRenderer.contains("renderAtLocal"),
                "Block ghosts must render local model vertices inside the Sable block frame.");
    }

    @Test
    void createGlueReceivesTheRtsCursorAndScreenClicks() throws Exception {
        String hitBridge = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsVanillaCursorHitBridge.java");
        String createCompat = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/create/RtsCreateGlueCompat.java");
        String pointerOwner = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreenPointerActionOwner.java");

        assertTrue(hitBridge.contains("minecraft.hitResult = hit"),
                "Third-party previews must see the current RTS block hit through Minecraft.hitResult.");
        assertTrue(createCompat.contains("onMouseInput") && createCompat.contains("Class.forName"),
                "Create glue clicks must be forwarded without introducing a hard Create dependency.");
        assertTrue(pointerOwner.contains("RtsCreateGlueCompat.handleWorldClick"),
                "BuilderScreen must offer world clicks to the Create glue handler before camera/mining actions.");
    }

    @Test
    void honeyGlueAndWorldshaperPreviewsUseOnlyTheirTargetedRtsRayAdapters() throws Exception {
        String hitBridge = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsVanillaCursorHitBridge.java");
        String honeyGlueMixin = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/SimulatedHoneyGlueClientHandlerMixin.java");
        String worldshaperMixin = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/CreateWorldshaperRenderHandlerMixin.java");
        String mixinConfig = source("src/main/resources/rtsbuilding.mixins.json");

        assertTrue(hitBridge.contains("BlockHitResult currentRtsBlockHit()"),
                "Targeted third-party previews should share one guarded RTS block-hit query.");
        assertTrue(honeyGlueMixin.contains("HoneyGlueClientHandler")
                        && honeyGlueMixin.contains("method = \"getHitResult\""),
                "Honey glue must replace only its private first-person ray entry point.");
        assertTrue(worldshaperMixin.contains("WorldshaperRenderHandler")
                        && worldshaperMixin.contains("method = \"createBrushOutline\""),
                "The worldshaper must replace only the ray used to gather its preview blocks.");
        assertTrue(mixinConfig.contains("SimulatedHoneyGlueClientHandlerMixin")
                        && mixinConfig.contains("CreateWorldshaperRenderHandlerMixin"),
                "Both optional client preview adapters must be registered.");
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
