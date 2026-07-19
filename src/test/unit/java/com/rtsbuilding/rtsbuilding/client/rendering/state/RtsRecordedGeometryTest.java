package com.rtsbuilding.rtsbuilding.client.rendering.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsRecordedGeometryTest {
    @Test
    void recorderFinishesThePreviousVertexWhenTheNextOneStarts() {
        RtsRecordedGeometry.Recorder recorder = new RtsRecordedGeometry.Recorder();
        recorder.addVertex(1.0F, 2.0F, 3.0F)
                .setColor(10, 20, 30, 40)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setLineWidth(2.0F);
        recorder.addVertex(4.0F, 5.0F, 6.0F)
                .setColor(0x80402010);

        var vertices = recorder.freeze();

        assertEquals(2, vertices.size());
        assertEquals(1.0F, vertices.getFirst().x());
        assertEquals(40, vertices.getFirst().alpha());
        assertTrue(vertices.getFirst().normalUsed());
        assertTrue(vertices.getFirst().lineWidthUsed());
        assertFalse(vertices.getFirst().packedColorUsed());
        assertTrue(vertices.getLast().packedColorUsed());
        assertEquals(0x80402010, vertices.getLast().packedColor());
    }

    @Test
    void recorderRejectsAttributesBeforeTheFirstVertex() {
        RtsRecordedGeometry.Recorder recorder = new RtsRecordedGeometry.Recorder();
        assertThrows(IllegalStateException.class, () -> recorder.setColor(255, 255, 255, 255));
    }
}
