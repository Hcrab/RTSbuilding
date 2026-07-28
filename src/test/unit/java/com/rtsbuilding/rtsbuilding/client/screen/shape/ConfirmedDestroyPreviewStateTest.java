package com.rtsbuilding.rtsbuilding.client.screen.shape;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已确认破坏工作区的保存、续期、超时和服务端裁剪必须完全由可注入快照决定。
 */
class ConfirmedDestroyPreviewStateTest {
    @Test
    void rangePreviewCopiesInputsAndKeepsConfirmedDestructiveSemantics() {
        AtomicLong now = new AtomicLong(100L);
        ConfirmedDestroyPreviewState state = new ConfirmedDestroyPreviewState(now::get);
        ArrayList<BlockPos> blocks = new ArrayList<>(List.of(new BlockPos(1, 2, 3)));
        ArrayList<BlockPos> envelope = new ArrayList<>(List.of(new BlockPos(4, 5, 6)));

        state.rememberRange(blocks, envelope);
        blocks.clear();
        envelope.clear();
        ShapeDataRecords.GhostPreview preview =
                state.activeRange(new ConfirmedDestroyPreviewState.Progress(null, -1, false), pos -> true);

        assertEquals(List.of(new BlockPos(1, 2, 3)), preview.blocks());
        assertEquals(List.of(new BlockPos(4, 5, 6)), preview.emptyBlocks());
        assertTrue(preview.readyConfirm());
        assertTrue(preview.destructive());
        assertTrue(preview.confirmedWorkArea());
        assertFalse(preview.chainDestroyPreview());
    }

    @Test
    void inactiveRangeExpiresAfterInitialHold() {
        AtomicLong now = new AtomicLong();
        ConfirmedDestroyPreviewState state = new ConfirmedDestroyPreviewState(now::get);
        state.rememberRange(List.of(new BlockPos(1, 2, 3)), List.of());

        now.set(2500L);
        assertFalse(state.activeRanges(progress(null, -1, false), pos -> true).isEmpty());
        now.set(2501L);
        assertSame(
                ShapeDataRecords.GhostPreview.EMPTY,
                state.activeRange(progress(null, -1, false), pos -> true));
    }

    @Test
    void matchingMiningOrWorkflowProgressExtendsTheShortHoldWindow() {
        AtomicLong now = new AtomicLong();
        ConfirmedDestroyPreviewState state = new ConfirmedDestroyPreviewState(now::get);
        BlockPos target = new BlockPos(1, 2, 3);
        state.rememberRange(List.of(target), List.of());

        now.set(3000L);
        assertFalse(state.activeRange(progress(target, 0, false), pos -> true)
                == ShapeDataRecords.GhostPreview.EMPTY);
        now.set(3850L);
        assertFalse(state.activeRange(progress(null, -1, false), pos -> true)
                == ShapeDataRecords.GhostPreview.EMPTY);
        now.set(3851L);
        assertSame(
                ShapeDataRecords.GhostPreview.EMPTY,
                state.activeRange(progress(null, -1, false), pos -> true));

        state.rememberRange(List.of(target), List.of());
        now.set(7000L);
        assertFalse(state.activeRange(progress(target, -1, true), pos -> true)
                == ShapeDataRecords.GhostPreview.EMPTY);
    }

    @Test
    void foreignMiningProgressImmediatelyClearsChainPreview() {
        AtomicLong now = new AtomicLong();
        ConfirmedDestroyPreviewState state = new ConfirmedDestroyPreviewState(now::get);
        BlockPos target = new BlockPos(1, 2, 3);
        state.rememberChain(List.of(target));

        ShapeDataRecords.GhostPreview cleared = state.activeChain(
                progress(new BlockPos(9, 9, 9), 0, false),
                pos -> true);

        assertSame(ShapeDataRecords.GhostPreview.EMPTY, cleared);
        assertSame(
                ShapeDataRecords.GhostPreview.EMPTY,
                state.activeChain(progress(target, 0, false), pos -> true));
    }

    @Test
    void vanishedWorldTargetsClearBothPreviewKinds() {
        AtomicLong now = new AtomicLong();
        ConfirmedDestroyPreviewState state = new ConfirmedDestroyPreviewState(now::get);
        BlockPos target = new BlockPos(1, 2, 3);
        state.rememberRange(List.of(target), List.of());
        state.rememberChain(List.of(target));

        assertSame(
                ShapeDataRecords.GhostPreview.EMPTY,
                state.activeRange(progress(null, -1, false), pos -> false));
        assertSame(
                ShapeDataRecords.GhostPreview.EMPTY,
                state.activeChain(progress(null, -1, false), pos -> false));
        assertFalse(state.hasAnyActive(progress(null, -1, false), pos -> false));
    }

    @Test
    void pruningRejectedBlocksAlsoRemovesMatchingEnvelopeCells() {
        BlockPos accepted = new BlockPos(1, 2, 3);
        BlockPos skipped = new BlockPos(4, 5, 6);
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(accepted, skipped),
                true,
                true,
                List.of(skipped, new BlockPos(7, 8, 9)),
                false,
                true);

        ShapeDataRecords.GhostPreview pruned =
                ConfirmedDestroyPreviewState.prune(preview, List.of(skipped));

        assertEquals(List.of(accepted), pruned.blocks());
        assertEquals(List.of(new BlockPos(7, 8, 9)), pruned.emptyBlocks());
        assertTrue(pruned.confirmedWorkArea());
    }

    @Test
    void emptyChainInputExplicitlyClearsThePreviousWorkArea() {
        AtomicLong now = new AtomicLong();
        ConfirmedDestroyPreviewState state = new ConfirmedDestroyPreviewState(now::get);
        state.rememberChain(List.of(new BlockPos(1, 2, 3)));

        state.clearChain();

        assertSame(
                ShapeDataRecords.GhostPreview.EMPTY,
                state.activeChain(progress(null, -1, false), pos -> true));
    }

    private static ConfirmedDestroyPreviewState.Progress progress(
            BlockPos pos,
            int mineStage,
            boolean workflow) {
        return new ConfirmedDestroyPreviewState.Progress(pos, mineStage, workflow);
    }
}
