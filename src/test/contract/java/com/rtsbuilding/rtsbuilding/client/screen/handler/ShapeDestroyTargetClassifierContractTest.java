package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定范围破坏目标分类与 Minecraft 世界查询的适配边界。
 */
class ShapeDestroyTargetClassifierContractTest {
    @Test
    void previewCostAndConfirmationUseTheSharedClassifier() throws IOException {
        String controller = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String preview = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeGhostPreviewProvider.java"));

        assertTrue(preview.contains("ShapeDestroyTargetClassifier.classify"));
        assertTrue(controller.contains("ShapeDestroyTargetClassifier.breakableTargets"));
        assertTrue(controller.contains("ShapeDestroyTargetClassifier.envelopeTargets"));
        assertTrue(controller.contains("this::isBreakableDestroyTarget"));
        assertFalse(controller.contains("private List<BlockPos> collectBreakableTargets"));
        assertFalse(controller.contains("collectRangeDestroyEnvelopeBlocks"));
        assertFalse(controller.contains("private record RangeDestroyPreview"));
    }

    @Test
    void classifierCannotReadWorldConfigScreenItemsOrNetwork() throws IOException {
        String classifier = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeDestroyTargetClassifier.java"));

        assertFalse(classifier.contains("import net.minecraft.client"));
        assertFalse(classifier.contains("BuilderScreen"));
        assertFalse(classifier.contains("ClientRtsController"));
        assertFalse(classifier.contains("import com.rtsbuilding.rtsbuilding.Config"));
        assertFalse(classifier.contains("BlockState"));
        assertFalse(classifier.contains("ItemStack"));
        assertFalse(classifier.contains("Packet"));
        assertTrue(classifier.contains("Predicate<BlockPos> breakableTarget"),
                "世界可破坏判断必须由生产适配器注入");
    }
}
