package com.rtsbuilding.rtsbuilding.server.service.crafting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCraftTerminalRefillContractTest {
    @Test
    void resultClickRefillDoesNotCanonicalizeShapelessIngredients() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/crafting/RtsCraftingGridFiller.java"));
        String body = methodBody(source,
                "AbstractContainerMenu craftingMenu, ItemStack[] blueprint)");

        assertFalse(body.contains("mapCraftingIngredients"),
                "结果点击补料不能再用无序配方的规范化 ingredient 顺序决定槽位");
        assertTrue(body.contains("evictUnexpectedRemainders"));
        assertTrue(body.contains("refillCraftGridToSnapshotCounts"),
                "补料必须使用玩家点击前的真实 3×3 槽位与数量快照");
    }

    @Test
    void unmovableRecipeRemainderStaysInCraftingGrid() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/crafting/RtsCraftingGridFiller.java"));
        String body = methodBody(source, "private static void evictUnexpectedRemainders(");

        int playerFirst = body.indexOf("moveToPlayerInventoryOnly");
        int storageFallback = body.indexOf("storeToLinkedOnlyPreferExisting", playerFirst);
        int restoreGrid = body.indexOf("grid.set(remainder)", storageFallback);
        assertTrue(playerFirst >= 0 && storageFallback > playerFirst && restoreGrid > storageFallback,
                "桶/瓶应先入背包、再入储存，仍放不下时必须回到原合成槽");
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(bodyStart, i + 1);
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
