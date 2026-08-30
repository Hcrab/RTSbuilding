package com.rtsbuilding.rtsbuilding.client.screen.shape;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 范围破坏的真实目标与空包络必须稳定分离，并保持玩家选区的输入顺序。
 */
class ShapeDestroyTargetClassifierTest {
    @Test
    void breakableTargetsPreserveOrderDeduplicateAndIgnoreNulls() {
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos second = new BlockPos(4, 5, 6);

        List<BlockPos> result = ShapeDestroyTargetClassifier.breakableTargets(
                java.util.Arrays.asList(first, null, second, first),
                pos -> true);

        assertEquals(List.of(first, second), result);
    }

    @Test
    void classificationSplitsRejectedCellsIntoTheEnvelope() {
        BlockPos acceptedA = new BlockPos(1, 0, 0);
        BlockPos rejected = new BlockPos(2, 0, 0);
        BlockPos acceptedB = new BlockPos(3, 0, 0);

        ShapeDestroyTargetClassifier.Selection selection =
                ShapeDestroyTargetClassifier.classify(
                        List.of(acceptedA, rejected, acceptedB, rejected),
                        pos -> !pos.equals(rejected));

        assertEquals(List.of(acceptedA, acceptedB), selection.breakableBlocks());
        assertEquals(List.of(rejected), selection.envelopeBlocks());
        assertFalse(selection.isEmpty());
    }

    @Test
    void nullPredicateTreatsEveryNonNullCandidateAsBreakable() {
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);

        ShapeDestroyTargetClassifier.Selection selection =
                ShapeDestroyTargetClassifier.classify(List.of(first, second), null);

        assertEquals(List.of(first, second), selection.breakableBlocks());
        assertTrue(selection.envelopeBlocks().isEmpty());
    }

    @Test
    void selectionDefensivelyCopiesAndNormalizesBothLists() {
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);
        ArrayList<BlockPos> breakable = new ArrayList<>(List.of(first, first));
        ArrayList<BlockPos> envelope = new ArrayList<>(List.of(second, second));

        ShapeDestroyTargetClassifier.Selection selection =
                new ShapeDestroyTargetClassifier.Selection(breakable, envelope);
        breakable.clear();
        envelope.clear();

        assertEquals(List.of(first), selection.breakableBlocks());
        assertEquals(List.of(second), selection.envelopeBlocks());
        assertNotSame(breakable, selection.breakableBlocks());
        assertNotSame(envelope, selection.envelopeBlocks());
    }

    @Test
    void emptyOrFullyRejectedSelectionsFailClosedWithoutMutableLists() {
        assertTrue(ShapeDestroyTargetClassifier.classify(null, pos -> true).isEmpty());

        BlockPos rejected = new BlockPos(1, 0, 0);
        ShapeDestroyTargetClassifier.Selection selection =
                ShapeDestroyTargetClassifier.classify(List.of(rejected), pos -> false);

        assertTrue(selection.breakableBlocks().isEmpty());
        assertEquals(List.of(rejected), selection.envelopeBlocks());
        assertFalse(selection.isEmpty());
    }
}
