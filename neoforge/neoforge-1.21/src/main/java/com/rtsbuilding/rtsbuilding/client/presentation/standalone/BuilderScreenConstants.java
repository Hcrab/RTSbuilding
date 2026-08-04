package com.rtsbuilding.rtsbuilding.client.presentation.standalone;

import net.minecraft.resources.ResourceLocation;

public final class BuilderScreenConstants {

    private BuilderScreenConstants() {}

    
    
    public static final int TOP_H = 60;
    
    public static final int TOP_BUTTON_GAP = 5;
    
    public static final int TOP_BUTTON_H = 24;
    
    public static final int MIN_TOP_BUTTON_W = 28;
    
    public static final int TOP_MODE_BUTTON_W = 32;
    
    public static final int TOP_ICON_BUTTON_W = 32;

    
    
    public static final int DEFAULT_BOTTOM_H = 110;
    
    public static final int MIN_BOTTOM_H = 72;
    
    public static final int MAX_BOTTOM_H = 320;
    
    public static final int BOTTOM_PANEL_PADDING = 8;
    
    public static final int BOTTOM_PANEL_HEADER_H = 18;
    
    public static final int MIN_STORAGE_GRID_ROWS = 2;
    
    public static final int GRID_BOTTOM_PADDING = 4;

    
    
    public static final int SLOT = 22;
    
    public static final int HOTBAR_SLOT = 18;
    
    public static final int HOTBAR_PITCH = 20;
    
    public static final int TOOL_HOTBAR_ITEM_SLOTS = 9;
    
    public static final int EMPTY_HAND_BUTTON_INDEX = TOOL_HOTBAR_ITEM_SLOTS;
    
    public static final int TOOL_AREA_H = HOTBAR_SLOT;

    
    
    public static final int SEARCH_CLEAR_SIZE = 12;
    
    public static final int SORT_BUTTON_SIZE = 16;

    
    
    public static final int CRAFT_PANEL_W = 126;
    
    public static final int CRAFT_PANEL_GAP = 6;
    
    public static final int CRAFT_PANEL_COLS = 4;
    
    public static final int CRAFT_PANEL_SLOT = 18;
    
    public static final int CRAFT_PANEL_PITCH = 20;
    
    public static final int CRAFT_PANEL_SEARCH_H = 12;
    
    public static final int CRAFT_PANEL_APPLY_W = 18;
    
    public static final int CRAFT_PANEL_TOGGLE_W = 38;
    
    public static final int CRAFT_DOCK_C_SIZE = 18;
    
    public static final int CRAFT_DOCK_SLOT_SIZE = 10;
    
    public static final int CRAFT_DOCK_GAP = 2;
    
    public static final int STORAGE_RECENT_GAP = 6;

    
    
    public static final int CATEGORY_W = 124;
    
    public static final int CATEGORY_ROW_H = 11;
    
    public static final float CATEGORY_TEXT_SCALE = 0.84F;

    
    
    public static final int QUICK_BUILD_PANEL_W = 188;
    
    public static final int QUICK_BUILD_PANEL_H = 216;
    
    public static final int QUICK_BUILD_PANEL_MIN_H = 156;
    
    public static final int QUICK_BUILD_SHAPE_SLOT = 32;
    
    public static final int QUICK_BUILD_SHAPE_GAP = 8;
    
    public static final int QUICK_BUILD_GEAR_MENU_W = 148;
    
    public static final int QUICK_BUILD_GEAR_ROW_H = 18;

    
    
    public static final int SHAPE_WHEEL_RADIUS = 52;
    
    public static final int SHAPE_WHEEL_SLOT = 22;
    
    public static final int SHAPE_MAX_DIMENSION = 32;
    
    public static final int SHAPE_MAX_OFFSET = SHAPE_MAX_DIMENSION - 1;
    
    public static final int SHAPE_MAX_RADIUS = 32;
    
    public static final int SHAPE_ROTATE_STEP_DEGREES = 15;

    
    
    public static final int SHAPE_CONTEXT_PANEL_W = 148;
    
    public static final int SHAPE_CONTEXT_PANEL_X_MARGIN = 10;
    
    public static final int SHAPE_CONTEXT_PANEL_Y = TOP_H + 10;
    
    public static final int SHAPE_CONTEXT_ROW_H = 14;

    
    
    public static final int GEAR_MENU_H = 337;
    
    public static final int GEAR_MENU_MIN_H = 249;
    
    public static final int GEAR_MENU_CONTENT_H = 724;

    

    
    public static final int TOPBAR_TEX_W = 1024;
    
    public static final int TOPBAR_TEX_H = 1536;
    
    public static final int TOPBAR_TEX_FRAME_H = 512;

    
    public static final int TOPBAR_V_INACTIVE = 512;
    
    public static final int TOPBAR_V_ACTIVE = 1024;
    
    public static final int TOPBAR_V_HOVER = 0;
    
    public static final ResourceLocation TOPBAR_INTERACT = topbarTexture("click_button");
    
    public static final ResourceLocation TOPBAR_LINK = topbarTexture("bind_button");
    
    public static final ResourceLocation TOPBAR_ROTATE = topbarTexture("direction_rotation_button");
    
    public static final ResourceLocation TOPBAR_QUICK_BUILD = topbarTexture("quick_construction_button");
    
    public static final ResourceLocation TOPBAR_QUEST_DETECT = topbarTexture("quest_detect_button");
    
    public static final ResourceLocation TOPBAR_CHUNK_VIEW = topbarTexture("block_display_button");
    
    public static final ResourceLocation TOPBAR_GEAR = topbarTexture("setting_button");

    
    
    public static final double DEFAULT_RTS_GUI_SCALE = 2.0D;
    
    public static final double MIN_RTS_GUI_SCALE = 1.0D;
    
    public static final double MAX_RTS_GUI_SCALE = 6.0D;
    
    public static final double RTS_GUI_SCALE_STEP = 0.5D;
    
    public static final int SEARCH_MAX_LENGTH = 128;
    
    public static final String CATEGORY_ALL = "all";
    
    public static final String CATEGORY_MOD_PREFIX = "mod|";
    
    public static final String CATEGORY_TAB_PREFIX = "tab|";

    
    private static ResourceLocation topbarTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/topbar/" + key + ".png");
        return id == null ? ResourceLocation.withDefaultNamespace("missingno") : id;
    }

}
