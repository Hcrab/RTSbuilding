package com.rtsbuilding.rtsbuilding.server.service.crafting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定自动补料必须保留玩家实际 3x3 摆放位置，而不是采用配方左上角归一化位置。 */
class CraftGridRefillPlacementContractTest {
    private static final Path FILLER = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/service/crafting/RtsCraftingGridFiller.java");

    @Test
    void exactBlueprintOccupancyControlsWhichSlotsAreRefilled() throws IOException {
        String source = Files.readString(FILLER, StandardCharsets.UTF_8);
        int loopStart = source.indexOf("for (int i = 0; i < 9; i++)");
        int loopEnd = source.indexOf("if (changed)", loopStart);
        String refillLoop = source.substring(loopStart, loopEnd);

        assertTrue(refillLoop.contains("if (!hasBlueprint)"));
        assertTrue(refillLoop.contains(
                "boolean ingredientMatchesBlueprint = hasIngredient && ingredient.apply(blueprintStack)"));
        assertTrue(refillLoop.contains(
                "ingredientMatchesBlueprint ? ingredient : Ingredient.EMPTY"));
        assertFalse(refillLoop.contains("if (!hasBlueprint && !hasIngredient)"));
    }
}
