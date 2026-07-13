package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientKeyMappingsTest {
    @Test
    void primaryInteractionKeepsPlainRightClickWhileMovePlayerRequiresControlRightClick() {
        var rightClick = InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        assertEquals(rightClick, ClientKeyMappings.ACTION_PRIMARY.getKey());
        assertEquals(KeyModifier.NONE, ClientKeyMappings.ACTION_PRIMARY.getKeyModifier());

        assertEquals(rightClick, ClientKeyMappings.MOVE_PLAYER.getKey());
        assertEquals(KeyModifier.CONTROL, ClientKeyMappings.MOVE_PLAYER.getKeyModifier());
        assertEquals(KeyConflictContext.GUI, ClientKeyMappings.MOVE_PLAYER.getKeyConflictContext());
    }
}
