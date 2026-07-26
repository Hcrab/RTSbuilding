package com.rtsbuilding.rtsbuilding.client.rendering;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPrivateBufferLifecycleTest {
    @Test
    void emptyBatchCanRestartWithoutAlreadyBuildingCrash() {
        BufferBuilder buffer = new BufferBuilder(256);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        assertDoesNotThrow(() -> RtsPrivateBufferLifecycle.begin(
                buffer,
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR));
        assertTrue(buffer.building());
        buffer.endOrDiscardIfEmpty();
    }

    @Test
    void unfinishedNonEmptyBatchIsReleasedBeforeRestart() {
        BufferBuilder buffer = new BufferBuilder(256);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(0.0D, 0.0D, 0.0D).color(255, 255, 255, 255).endVertex();

        assertDoesNotThrow(() -> RtsPrivateBufferLifecycle.begin(
                buffer,
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR));
        assertTrue(buffer.building());
        buffer.endOrDiscardIfEmpty();
    }
}
