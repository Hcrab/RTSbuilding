package com.rtsbuilding.rtsbuilding.common.shape.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AreaShapeTest {

    @Test
    void ordinalStability() {
        assertEquals(0, AreaShape.BLOCK.ordinal());
        assertEquals(1, AreaShape.LINE.ordinal());
        assertEquals(2, AreaShape.SQUARE.ordinal());
        assertEquals(3, AreaShape.WALL.ordinal());
        assertEquals(4, AreaShape.CIRCLE.ordinal());
        assertEquals(5, AreaShape.BOX.ordinal());
    }

    @Test
    void valueCount() {
        assertEquals(6, AreaShape.values().length);
    }

    @Test
    void valueOfRoundTrip() {
        for (AreaShape shape : AreaShape.values()) {
            assertSame(shape, AreaShape.valueOf(shape.name()));
        }
    }
}
