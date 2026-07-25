package com.rtsbuilding.rtsbuilding.client.screen.mode;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacedBlockRotationGestureTest {
    @Test
    void verticalGestureUsesCameraRightAsItsSignedAxis() {
        assertEquals(
                Direction.EAST,
                PlacedBlockRotationGesture.VERTICAL_UP.axisDirection(Direction.NORTH));
        assertEquals(
                Direction.SOUTH,
                PlacedBlockRotationGesture.VERTICAL_UP.axisDirection(Direction.EAST));
    }

    @Test
    void arrowsAndNumpadShareTheSameFourGestures() {
        assertEquals(
                PlacedBlockRotationGesture.HORIZONTAL_LEFT,
                PlacedBlockRotationGesture.fromKey(GLFW.GLFW_KEY_LEFT));
        assertEquals(
                PlacedBlockRotationGesture.HORIZONTAL_RIGHT,
                PlacedBlockRotationGesture.fromKey(GLFW.GLFW_KEY_KP_6));
        assertEquals(
                PlacedBlockRotationGesture.VERTICAL_UP,
                PlacedBlockRotationGesture.fromKey(GLFW.GLFW_KEY_KP_8));
        assertEquals(
                PlacedBlockRotationGesture.VERTICAL_DOWN,
                PlacedBlockRotationGesture.fromKey(GLFW.GLFW_KEY_DOWN));
    }
}
