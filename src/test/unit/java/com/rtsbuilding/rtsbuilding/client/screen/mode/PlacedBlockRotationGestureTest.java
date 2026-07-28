package com.rtsbuilding.rtsbuilding.client.screen.mode;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacedBlockRotationGestureTest {
    @Test
    void horizontalGestureTurnsCameraFacingSideTowardItsScreenDirection() {
        assertEquals(-1, PlacedBlockRotationGesture.HORIZONTAL_RIGHT.quarterTurns());
        assertEquals(1, PlacedBlockRotationGesture.HORIZONTAL_LEFT.quarterTurns());
    }

    @Test
    void verticalGestureUsesCameraRightAsItsSignedAxis() {
        assertEquals(
                EnumFacing.EAST,
                PlacedBlockRotationGesture.VERTICAL_UP.axisDirection(EnumFacing.NORTH));
        assertEquals(
                EnumFacing.SOUTH,
                PlacedBlockRotationGesture.VERTICAL_UP.axisDirection(EnumFacing.EAST));
    }

    @Test
    void arrowsAndNumpadShareTheSameFourGestures() {
        assertEquals(
                PlacedBlockRotationGesture.HORIZONTAL_LEFT,
                PlacedBlockRotationGesture.fromKey(Keyboard.KEY_LEFT));
        assertEquals(
                PlacedBlockRotationGesture.HORIZONTAL_RIGHT,
                PlacedBlockRotationGesture.fromKey(Keyboard.KEY_NUMPAD6));
        assertEquals(
                PlacedBlockRotationGesture.VERTICAL_UP,
                PlacedBlockRotationGesture.fromKey(Keyboard.KEY_NUMPAD8));
        assertEquals(
                PlacedBlockRotationGesture.VERTICAL_DOWN,
                PlacedBlockRotationGesture.fromKey(Keyboard.KEY_DOWN));
    }
}
