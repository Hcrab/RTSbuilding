package com.rtsbuilding.rtsbuilding.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;


public final class RtsKeyMappings {

    private RtsKeyMappings() {}

    
    public static final String CATEGORY_FUNCTION = "key.categories.rtsbuilding.function";

    
    public static final String CATEGORY_CAMERA = "key.categories.rtsbuilding.camera";

    
    public static final KeyMapping TOGGLE_RTS_KEY = new KeyMapping(
            "key.rtsbuilding.toggleRts",
            GLFW.GLFW_KEY_G,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping OPEN_GEAR_MENU_KEY = new KeyMapping(
            "key.rtsbuilding.open_gear_menu",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_DEBUG_OVERLAY_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_debug_overlay",
            KeyConflictContext.GUI,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_CAMERA_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_camera_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping MOVE_PLAYER_KEY = new KeyMapping(
            "key.rtsbuilding.move_player",
            KeyConflictContext.GUI,
            KeyModifier.ALT,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_SELECT_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_select_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_BIND_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_bind_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_DIRECTION_ROTATE_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_direction_rotate_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_ITEM_PICKUP_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_item_pickup_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping CYCLE_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.cycle_mode",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            CATEGORY_FUNCTION
    );

    
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_RTS_KEY);
        event.register(OPEN_GEAR_MENU_KEY);
        event.register(TOGGLE_DEBUG_OVERLAY_KEY);
        event.register(TOGGLE_CAMERA_MODE_KEY);
        event.register(MOVE_PLAYER_KEY);
        event.register(TOGGLE_SELECT_MODE_KEY);
        event.register(TOGGLE_BIND_MODE_KEY);
        event.register(TOGGLE_DIRECTION_ROTATE_MODE_KEY);
        event.register(TOGGLE_ITEM_PICKUP_MODE_KEY);
        event.register(CYCLE_MODE_KEY);
    }
}
