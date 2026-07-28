package com.rtsbuilding.rtsbuilding.client.bootstrap;

import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientKeyMappingsTest {
    @Test
    void primaryInteractionKeepsPlainRightClickWhileMovePlayerRequiresControlRightClick() {
        assertEquals(ClientKeyMappings.MOUSE_RIGHT, ClientKeyMappings.ACTION_PRIMARY.getKeyCode());
        assertEquals(KeyModifier.NONE, ClientKeyMappings.ACTION_PRIMARY.getKeyModifier());

        assertEquals(ClientKeyMappings.MOUSE_RIGHT, ClientKeyMappings.MOVE_PLAYER.getKeyCode());
        assertEquals(KeyModifier.CONTROL, ClientKeyMappings.MOVE_PLAYER.getKeyModifier());
        assertEquals(KeyConflictContext.GUI, ClientKeyMappings.MOVE_PLAYER.getKeyConflictContext());
    }
}
