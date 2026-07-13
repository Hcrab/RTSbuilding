package com.rtsbuilding.rtsbuilding.client.rendering;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostBlockModelRendererContractTest {
    @Test
    void sharedGhostRendererPassesWorldPositionToBlockModelTessellator() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/util/GhostBlockModelRenderer.java"));

        assertTrue(source.contains("tesselateBlock("),
                "Ghost block previews must use the world-position-aware model tessellator.");
        assertTrue(source.contains("minecraft.level"),
                "Position-sensitive block colors, such as TFC seasonal leaves, need a real client world.");
        assertTrue(source.contains("BlockPos pos"),
                "The shared renderer must keep the target BlockPos in its public contract.");
        assertTrue(source.contains("state.getSeed(pos)"),
                "Model randomness must remain tied to the rendered world position.");
        assertFalse(source.contains(".renderSingleBlock("),
                "renderSingleBlock can call BlockColors with a null position and crash position-sensitive mods.");
    }

    @Test
    void ghostModelCallSitesUseTheSharedPositionAwareRenderer() throws Exception {
        for (String file : ghostModelCallSites()) {
            String source = Files.readString(Path.of(file));
            assertTrue(source.contains("GhostBlockModelRenderer.renderAt("),
                    file + " should render ghost block models through the shared context-aware helper.");
            assertFalse(source.contains(".renderSingleBlock("),
                    file + " must not reintroduce null-position block color rendering.");
        }
    }

    private static List<String> ghostModelCallSites() {
        return List.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/animation/DestroyGhostRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/animation/PendingGhostRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/animation/ConfirmedPlacementRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/BuildGhostModelRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/blueprint/BlueprintGhostBlockModelRenderer.java"
        );
    }
}
