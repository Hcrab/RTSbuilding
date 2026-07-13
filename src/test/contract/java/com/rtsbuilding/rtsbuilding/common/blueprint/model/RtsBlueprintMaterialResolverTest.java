package com.rtsbuilding.rtsbuilding.common.blueprint.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBlueprintMaterialResolverTest {
    @Test
    void materialResolutionDoesNotTrustImportedMaterialItemOrBlockEntityNbt() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/blueprint/model/RtsBlueprint.java"));
        String body = methodBody(source, "public static List<ResourceLocation> materialItemIds(RtsBlueprintBlock block)");

        assertTrue(body.contains("BlueprintMaterialResolver.materialItemIds(block.state())"),
                "Blueprint material cost must be derived from the trusted block state.");
        assertFalse(body.contains("block.materialItemId()"),
                "Imported rtsbuilding_material_item must not decide survival material cost.");
        assertFalse(body.contains("block.blockEntityTag()"),
                "Block entity NBT must not be scanned for arbitrary material item IDs.");
        assertFalse(source.contains("collectMaterialItemIds("),
                "Recursive NBT string scanning would re-open material ID injection.");
    }

    @Test
    void materialResolverKeepsExplicitFallbacksForKnownVanillaBlocks() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/blueprint/material/BlueprintMaterialResolver.java"));

        assertTrue(source.contains("Blocks.FARMLAND") && source.contains("Items.DIRT"),
                "Farmland should still cost dirt after removing imported material overrides.");
        assertTrue(source.contains("Blocks.DIRT_PATH") && source.contains("Items.DIRT"),
                "Dirt path should still cost dirt after removing imported material overrides.");
        assertTrue(source.contains("Blocks.TALL_GRASS") && source.contains("Items.SHORT_GRASS"),
                "Tall grass should still cost short grass after removing imported material overrides.");
        assertTrue(source.contains("Blocks.LARGE_FERN") && source.contains("Items.FERN"),
                "Large fern should still cost fern after removing imported material overrides.");
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
