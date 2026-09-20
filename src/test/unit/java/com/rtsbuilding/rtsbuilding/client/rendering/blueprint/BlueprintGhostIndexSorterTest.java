package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintGhostIndexSorterTest {
    @Test
    void indexOnlyRenderedBufferKeepsCountsAndChangesOrderForOppositeCameras() {
        BufferBuilder source = new BufferBuilder(512);
        source.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        addQuad(source, -4.0F);
        addQuad(source, 4.0F);
        source.setQuadSorting(VertexSorting.byDistance(0.0F, 0.0F, 0.0F));
        BufferBuilder.SortState state = source.getSortState();
        BufferBuilder.RenderedBuffer initial = source.end();
        int vertexCount = initial.drawState().vertexCount();
        int indexCount = initial.drawState().indexCount();
        assertFalse(initial.drawState().indexOnly());
        initial.release();

        BlueprintGhostIndexSorter sorter = new BlueprintGhostIndexSorter(
                512, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        BlueprintGhostIndexSorter.IndexOnlyBuffer left = sorter.prepare(state,
                VertexSorting.byDistance(-100.0F, 0.0F, 0.0F));
        assertNotNull(left);
        int[] leftOrder;
        try {
            BufferBuilder.RenderedBuffer rendered = left.rendered();
            assertTrue(rendered.drawState().indexOnly());
            assertEquals(vertexCount * DefaultVertexFormat.POSITION.getVertexSize(),
                    rendered.vertexBuffer().remaining());
            assertEquals(vertexCount, rendered.drawState().vertexCount());
            assertEquals(indexCount, rendered.drawState().indexCount());
            assertEquals(indexCount * rendered.drawState().indexType().bytes,
                    rendered.indexBuffer().remaining());
            leftOrder = readIndices(rendered);
        } finally {
            left.close();
        }

        BlueprintGhostIndexSorter.IndexOnlyBuffer right = sorter.prepare(state,
                VertexSorting.byDistance(100.0F, 0.0F, 0.0F));
        assertNotNull(right);
        int[] rightOrder;
        try {
            BufferBuilder.RenderedBuffer rendered = right.rendered();
            assertTrue(rendered.drawState().indexOnly());
            assertEquals(vertexCount * DefaultVertexFormat.POSITION.getVertexSize(),
                    rendered.vertexBuffer().remaining());
            assertEquals(vertexCount, rendered.drawState().vertexCount());
            assertEquals(indexCount, rendered.drawState().indexCount());
            assertEquals(indexCount * rendered.drawState().indexType().bytes,
                    rendered.indexBuffer().remaining());
            rightOrder = readIndices(rendered);
        } finally {
            right.close();
            sorter.close();
        }

        assertNotEquals(List.of(leftOrder[0], leftOrder[1], leftOrder[2], leftOrder[3]),
                List.of(rightOrder[0], rightOrder[1], rightOrder[2], rightOrder[3]));
    }

    @Test
    void closedSorterDoesNotCreateAnotherIndexBatch() {
        BufferBuilder source = new BufferBuilder(256);
        source.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        addQuad(source, 0.0F);
        source.setQuadSorting(VertexSorting.byDistance(0.0F, 0.0F, 0.0F));
        BufferBuilder.SortState state = source.getSortState();
        source.end().release();

        BlueprintGhostIndexSorter sorter = new BlueprintGhostIndexSorter(
                256, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        BlueprintGhostIndexSorter.IndexOnlyBuffer open = sorter.prepare(state,
                VertexSorting.DISTANCE_TO_ORIGIN);
        assertNotNull(open);
        sorter.close();
        assertNull(open.rendered());
        assertNull(sorter.prepare(state, VertexSorting.DISTANCE_TO_ORIGIN));
        sorter.close();
    }

    @Test
    void roundRobinBudgetEventuallyVisitsAllMovedBatches() {
        AtomicLong clock = new AtomicLong();
        BlueprintGhostSortScheduler<Integer> scheduler =
                new BlueprintGhostSortScheduler<>(() -> clock.getAndAdd(600_000L));
        List<Integer> batches = List.of(0, 1, 2, 3);
        List<Integer> resorted = new ArrayList<>();

        scheduler.schedule(batches, Vec3.ZERO, ignored -> 4.0D,
                ignored -> true, resorted::add);
        scheduler.schedule(batches, Vec3.ZERO, ignored -> 4.0D,
                ignored -> true, resorted::add);

        assertEquals(List.of(0, 1, 2, 3), resorted);
    }

    @Test
    void stationaryAndSubThresholdFramesDoNotResortAndInvalidBatchesAreSkipped() {
        BlueprintGhostSortScheduler<Integer> scheduler =
                new BlueprintGhostSortScheduler<>(() -> 0L);
        List<Integer> resorted = new ArrayList<>();
        List<Integer> batches = List.of(0, 1);

        scheduler.schedule(batches, Vec3.ZERO, ignored -> 0.0D,
                ignored -> true, resorted::add);
        scheduler.schedule(batches, Vec3.ZERO, ignored -> .99D,
                ignored -> true, resorted::add);
        scheduler.schedule(batches, Vec3.ZERO, ignored -> 4.0D,
                ignored -> false, resorted::add);

        assertTrue(resorted.isEmpty());
    }

    @Test
    void movementAtSquaredDistanceOneTriggersExactlyEligibleBatch() {
        BlueprintGhostSortScheduler<Integer> scheduler =
                new BlueprintGhostSortScheduler<>(() -> 0L);
        List<Integer> resorted = new ArrayList<>();

        scheduler.schedule(List.of(0), Vec3.ZERO, ignored -> 1.0D,
                ignored -> true, resorted::add);

        assertEquals(List.of(0), resorted);
    }

    private static void addQuad(BufferBuilder builder, float x) {
        builder.vertex(x, 0.0F, 0.0F).endVertex();
        builder.vertex(x, 1.0F, 0.0F).endVertex();
        builder.vertex(x, 1.0F, 1.0F).endVertex();
        builder.vertex(x, 0.0F, 1.0F).endVertex();
    }

    private static int[] readIndices(BufferBuilder.RenderedBuffer rendered) {
        ByteBuffer indices = rendered.indexBuffer();
        int[] result = new int[rendered.drawState().indexCount()];
        for (int i = 0; i < result.length; i++) {
            result[i] = rendered.drawState().indexType() == VertexFormat.IndexType.SHORT
                    ? Short.toUnsignedInt(indices.getShort(i * 2))
                    : indices.getInt(i * 4);
        }
        return result;
    }
}
