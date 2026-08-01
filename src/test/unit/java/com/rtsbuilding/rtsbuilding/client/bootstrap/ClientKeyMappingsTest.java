package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientKeyMappingsTest {
    @Test
    void primaryInteractionKeepsPlainRightClickWhileMovePlayerRequiresControlRightClick() {
        var rightClick = InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        assertEquals(rightClick, ClientKeyMappings.ACTION_PRIMARY.getDefaultKey());
        assertEquals(rightClick, ClientKeyMappings.MOVE_PLAYER.getDefaultKey());
        assertFalse(ClientKeyMappings.matchesMovePlayerMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false));
        assertTrue(ClientKeyMappings.matchesMovePlayerMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT, true));
    }
}
