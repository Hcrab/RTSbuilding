package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsRightClickPriorityRoutingTest {
    @Test
    void selectedStorageItemSingleBlockNormalRightClickInteractsBeforePlacement() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "private boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton)");

        int selectedItemBranch = body.indexOf("if (this.controller.hasSelectedItem())");
        assertTrue(selectedItemBranch >= 0, "selected item branch missing");

        int normalInteractGuard = body.indexOf(
                "if (!forcePlace && !rangeDestroyMode && this.controller.getBuildShape() == BuildShape.BLOCK)",
                selectedItemBranch);
        int interactPinnedItem = body.indexOf("this.controller.interactBlockWithPinnedItem", selectedItemBranch);
        int forcePlacementBranch = body.indexOf("if (rangeDestroyMode)", selectedItemBranch);

        assertTrue(normalInteractGuard >= 0,
                "普通右键交互优先只能截获单方块模式，形状/范围放置不能被提前返回。");
        assertTrue(interactPinnedItem > normalInteractGuard,
                "normal right-click with a selected storage item should send interact before placement");
        assertTrue(forcePlacementBranch > interactPinnedItem,
                "placement branch should come after the normal interaction branch");
    }

    @Test
    void selectedStorageItemShapePlacementBypassesNormalInteractBranch() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "private boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton)");

        int selectedItemBranch = body.indexOf("if (this.controller.hasSelectedItem())");
        assertTrue(selectedItemBranch >= 0, "selected item branch missing");

        int normalInteractGuard = body.indexOf(
                "this.controller.getBuildShape() == BuildShape.BLOCK",
                selectedItemBranch);
        int shapePlacement = body.indexOf("this.shapeController.placeWithShape(", selectedItemBranch);

        assertTrue(normalInteractGuard >= 0, "selected storage item routing must guard interact-first by shape");
        assertTrue(shapePlacement > normalInteractGuard,
                "形状建造需要继续进入 placeWithShape，不能被储存栏物品普通交互吞掉。");
    }

    @Test
    void mainHandNormalRightClickInteractsAndShiftRightClickPlacesFirst() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "private boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton)");

        int toolSlotInteract = body.indexOf("this.controller.interactBlockWithToolSlot");
        assertTrue(toolSlotInteract >= 0, "normal main-hand right-click should send tool-slot interaction");

        int shiftPlace = body.lastIndexOf("this.controller.placeSelected(target.blockHit(), true", toolSlotInteract);
        int forceGuard = body.lastIndexOf("if (forcePlace)", toolSlotInteract);

        assertTrue(forceGuard >= 0, "main-hand block action must keep the Shift force-place branch");
        assertTrue(shiftPlace > forceGuard,
                "Shift right-click should run placeSelected before the normal interaction fallback");
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
