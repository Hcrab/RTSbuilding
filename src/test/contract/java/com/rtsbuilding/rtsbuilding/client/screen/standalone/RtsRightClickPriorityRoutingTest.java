package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsRightClickPriorityRoutingTest {
    @Test
    void selectedStorageItemNormalRightClickInteractsBeforePlacement() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
        String body = methodBody(source, "private boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton)");

        int selectedItemBranch = body.indexOf("if (this.controller.hasSelectedItem())");
        assertTrue(selectedItemBranch >= 0, "selected item branch missing");

        int normalInteractGuard = body.indexOf(
                "if (!forceBackpackPlacement && !forcePlace && !rangeDestroyMode", selectedItemBranch);
        int interactPinnedItem = body.indexOf("this.controller.interactBlockWithPinnedItem", selectedItemBranch);
        int forcePlacementBranch = body.indexOf("if (rangeDestroyMode)", selectedItemBranch);

        assertTrue(normalInteractGuard >= 0, "normal right-click must be explicitly routed to interaction first");
        assertTrue(body.indexOf("this.controller.getBuildShape() == BuildShape.BLOCK", normalInteractGuard)
                        > normalInteractGuard,
                "normal item interaction must remain limited to single-block mode");
        assertTrue(interactPinnedItem > normalInteractGuard,
                "normal right-click with a selected storage item should send interact before placement");
        assertTrue(forcePlacementBranch > interactPinnedItem,
                "placement branch should come after the normal interaction branch");
    }

    @Test
    void mainHandNormalRightClickInteractsAndShiftRightClickPlacesFirst() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
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
