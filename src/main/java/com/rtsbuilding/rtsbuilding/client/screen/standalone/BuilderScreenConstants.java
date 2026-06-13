package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Layout constants used by BuilderScreen.
 * <p>
 * All constants related to screen layout, panel sizes, and spacing are
 * centralised here to facilitate unified UI layout adjustments.
 */
public final class BuilderScreenConstants {

    // ======================== Top bar ========================
    /** Top bar height */
    public static final int TOP_H = 52;
    /** Top bar button gap */
    public static final int TOP_BUTTON_GAP = 5;
    /** Top bar button height */
    public static final int TOP_BUTTON_H = 24;
    /** Top button minimum width */
    public static final int MIN_TOP_BUTTON_W = 28;
    /** Mode button width */
    public static final int TOP_MODE_BUTTON_W = 32;
    /** Icon button width */
    public static final int TOP_ICON_BUTTON_W = 32;

    // ======================== Bottom panel ========================
    /** Default bottom panel height */
    public static final int DEFAULT_BOTTOM_H = 110;
    /** Bottom panel minimum height */
    public static final int MIN_BOTTOM_H = 72;
    /** Bottom panel maximum height */
    public static final int MAX_BOTTOM_H = 320;
    /** Bottom panel padding */
    public static final int BOTTOM_PANEL_PADDING = 8;
    /** Bottom panel header height */
    public static final int BOTTOM_PANEL_HEADER_H = 18;
    /** Minimum storage grid rows */
    public static final int MIN_STORAGE_GRID_ROWS = 2;
    /** Grid bottom padding */
    public static final int GRID_BOTTOM_PADDING = 4;

    // ======================== Slots / Grid ========================
    /** Storage grid single slot size */
    public static final int SLOT = 22;
    /** Hotbar single slot size */
    public static final int HOTBAR_SLOT = 18;
    /** Hotbar slot pitch */
    public static final int HOTBAR_PITCH = 20;
    /** Tool hotbar item slots count */
    public static final int TOOL_HOTBAR_ITEM_SLOTS = 9;
    /** Empty hand button index (after vanilla 9-slot hotbar) */
    public static final int EMPTY_HAND_BUTTON_INDEX = TOOL_HOTBAR_ITEM_SLOTS;
    /** Tool area height */
    public static final int TOOL_AREA_H = HOTBAR_SLOT;

    // ======================== Search / Sort ========================
    /** Search clear button size */
    public static final int SEARCH_CLEAR_SIZE = 12;
    /** Sort button size */
    public static final int SORT_BUTTON_SIZE = 16;

    // ======================== Crafting panel ========================
    /** Crafting panel width */
    public static final int CRAFT_PANEL_W = 126;
    /** Gap between crafting panel and storage grid */
    public static final int CRAFT_PANEL_GAP = 6;
    /** Crafting panel columns */
    public static final int CRAFT_PANEL_COLS = 4;
    /** Crafting panel slot size */
    public static final int CRAFT_PANEL_SLOT = 18;
    /** Crafting panel row pitch */
    public static final int CRAFT_PANEL_PITCH = 20;
    /** Crafting search box height */
    public static final int CRAFT_PANEL_SEARCH_H = 12;
    /** Crafting apply button width */
    public static final int CRAFT_PANEL_APPLY_W = 18;
    /** Crafting toggle button width */
    public static final int CRAFT_PANEL_TOGGLE_W = 38;
    /** Craft dock centre button size */
    public static final int CRAFT_DOCK_C_SIZE = 18;
    /** Craft dock slot size */
    public static final int CRAFT_DOCK_SLOT_SIZE = 10;
    /** Craft dock gap */
    public static final int CRAFT_DOCK_GAP = 2;
    /** Gap between storage and recent items */
    public static final int STORAGE_RECENT_GAP = 6;

    // ======================== Category panel ========================
    /** Category panel width */
    public static final int CATEGORY_W = 124;
    /** Category row height */
    public static final int CATEGORY_ROW_H = 11;
    /** Category text scale */
    public static final float CATEGORY_TEXT_SCALE = 0.84F;

    // ======================== Quick-build panel ========================
    /** Quick-build panel width */
    public static final int QUICK_BUILD_PANEL_W = 188;
    /** Quick-build panel height */
    public static final int QUICK_BUILD_PANEL_H = 216;
    /** Quick-build panel minimum height */
    public static final int QUICK_BUILD_PANEL_MIN_H = 156;
    /** Quick-build shape slot size */
    public static final int QUICK_BUILD_SHAPE_SLOT = 32;
    /** Quick-build shape gap */
    public static final int QUICK_BUILD_SHAPE_GAP = 8;
    /** Quick-build gear menu width */
    public static final int QUICK_BUILD_GEAR_MENU_W = 148;
    /** Quick-build gear row height */
    public static final int QUICK_BUILD_GEAR_ROW_H = 18;

    // ======================== Chain Destroy Limits ========================
    public static final int ULTIMINE_MIN_LIMIT = 1;
    public static final int ULTIMINE_MAX_LIMIT = 256;

    // ======================== Shape wheel ========================
    /** Shape wheel radius */
    public static final int SHAPE_WHEEL_RADIUS = 52;
    /** Shape wheel slot size */
    public static final int SHAPE_WHEEL_SLOT = 22;
    /** Shape maximum dimension */
    public static final int SHAPE_MAX_DIMENSION = 32;
    /** Shape maximum offset */
    public static final int SHAPE_MAX_OFFSET = SHAPE_MAX_DIMENSION - 1;
    /** Shape maximum radius */
    public static final int SHAPE_MAX_RADIUS = 32;
    /** Shape rotation step degrees */
    public static final int SHAPE_ROTATE_STEP_DEGREES = 15;
    /** Shape history limit */
    public static final int SHAPE_HISTORY_LIMIT = RtsHistoryConstants.SHAPE_HISTORY_LIMIT;

    // ======================== Shape context panel ========================
    /** Shape context panel width */
    public static final int SHAPE_CONTEXT_PANEL_W = 148;
    /** Shape context panel X margin */
    public static final int SHAPE_CONTEXT_PANEL_X_MARGIN = 10;
    /** Shape context panel Y coordinate */
    public static final int SHAPE_CONTEXT_PANEL_Y = TOP_H + 10;
    /** Shape context row height */
    public static final int SHAPE_CONTEXT_ROW_H = 14;

    // ======================== Funnel buffer panel ========================
    /** Funnel buffer panel width */
    public static final int FUNNEL_BUFFER_PANEL_W = 132;
    /** Funnel buffer row height */
    public static final int FUNNEL_BUFFER_ROW_H = 22;
    /** Funnel buffer toggle width */
    public static final int FUNNEL_BUFFER_TOGGLE_W = 60;
    /** Funnel buffer toggle height */
    public static final int FUNNEL_BUFFER_TOGGLE_H = 16;

    // ======================== Gear menu (settings) ========================
    /** Gear menu height */
    public static final int GEAR_MENU_H = 520;
    /** Gear menu minimum height */
    public static final int GEAR_MENU_MIN_H = 220;
    /** Gear menu content height */
    public static final int GEAR_MENU_CONTENT_H = 724;

    // ======================== Quest detect popup ========================
    /** Quest detect popup width */
    public static final int QUEST_DETECT_POPUP_W = 178;
    /** Quest detect popup height */
    public static final int QUEST_DETECT_POPUP_H = 48;

    // ======================== Storage scan popup ========================
    /** Storage scan popup width */
    public static final int STORAGE_SCAN_POPUP_W = 150;
    /** Storage scan popup height */
    public static final int STORAGE_SCAN_POPUP_H = 30;

    // ======================== Input / Render control ========================
    /** Middle-click drag threshold (pixels) */
    public static final double MIDDLE_CLICK_DRAG_THRESHOLD = 1.5D;
    /** Default RTS GUI scale */
    public static final double DEFAULT_RTS_GUI_SCALE = 2.0D;
    /** Minimum RTS GUI scale */
    public static final double MIN_RTS_GUI_SCALE = 1.0D;
    /** Maximum RTS GUI scale */
    public static final double MAX_RTS_GUI_SCALE = 4.0D;
    /** RTS GUI scale step */
    public static final double RTS_GUI_SCALE_STEP = 0.5D;
    /** Modal layer Z depth */
    public static final float RTS_MODAL_LAYER_Z = 400.0F;
    /** Damage flash duration (ms) */
    public static final long DAMAGE_FLASH_DURATION_MS = 300L;

    // ======================== Miscellaneous ========================
    /** Funnel cursor item stack (hopper) */
    public static final ItemStack FUNNEL_CURSOR_STACK = new ItemStack(net.minecraft.world.item.Items.HOPPER);
    /** Left tooltip X offset */
    public static final int LEFT_TOOLTIP_X_OFFSET = 8;
    /** Left tooltip Y offset */
    public static final int LEFT_TOOLTIP_Y_OFFSET = 24;
    /** Left tooltip detail Y offset */
    public static final int LEFT_TOOLTIP_DETAIL_Y_OFFSET = 18;
    /** Storage link detail action button height */
    public static final int STORAGE_LINK_DETAIL_ACTION_H = 16;
    /** "All" category token */
    public static final String CATEGORY_ALL = "all";
    /** Mod category prefix */
    public static final String CATEGORY_MOD_PREFIX = "mod|";
    /** Tab category prefix */
    public static final String CATEGORY_TAB_PREFIX = "tab|";

    // ======================== Texture assets ========================
    /** PR #71 QuickBuild shape sheet: single block. */
    public static final ResourceLocation QUICK_BUILD_SINGLE_BLOCK = quickBuildTexture("single_block");
    /** PR #71 QuickBuild shape sheet: line. */
    public static final ResourceLocation QUICK_BUILD_LINE_BLOCK = quickBuildTexture("line_block");
    /** PR #71 QuickBuild shape sheet: square placeholder until final art lands. */
    public static final ResourceLocation QUICK_BUILD_SQUARE_BLOCK = quickBuildTexture("square_block");
    /** PR #71 QuickBuild shape sheet: wall placeholder until final art lands. */
    public static final ResourceLocation QUICK_BUILD_WALL_BLOCK = quickBuildTexture("wall_block");
    /** PR #71 QuickBuild shape sheet: circle placeholder until final art lands. */
    public static final ResourceLocation QUICK_BUILD_CIRCLE_BLOCK = quickBuildTexture("circle_block");
    /** PR #71 QuickBuild shape sheet: box placeholder until final art lands. */
    public static final ResourceLocation QUICK_BUILD_BOX_BLOCK = quickBuildTexture("box_block");

    public static final ResourceLocation QUICK_BUILD_CHAIN_BLOCK = quickBuildTexture("chain_block");

    // ======================== Top-bar mode textures ========================
    /** Interact mode (inactive) */
    public static final ResourceLocation TOPBAR_INTERACT_INACTIVE = topbarTexture("mode_interact_inactive");
    /** Interact mode (hover) */
    public static final ResourceLocation TOPBAR_INTERACT_HOVER = topbarTexture("mode_interact_hover");
    /** Interact mode (active) */
    public static final ResourceLocation TOPBAR_INTERACT_ACTIVE = topbarTexture("mode_interact_active");
    /** Interact mode (pressed) */
    public static final ResourceLocation TOPBAR_INTERACT_PRESSED = topbarTexture("mode_interact_pressed");

    /** Link mode (inactive) */
    public static final ResourceLocation TOPBAR_LINK_INACTIVE = topbarTexture("mode_link_inactive");
    /** Link mode (hover) */
    public static final ResourceLocation TOPBAR_LINK_HOVER = topbarTexture("mode_link_hover");
    /** Link mode (active) */
    public static final ResourceLocation TOPBAR_LINK_ACTIVE = topbarTexture("mode_link_active");
    /** Link mode (pressed) */
    public static final ResourceLocation TOPBAR_LINK_PRESSED = topbarTexture("mode_link_pressed");

    /** Funnel mode (inactive) */
    public static final ResourceLocation TOPBAR_FUNNEL_INACTIVE = topbarTexture("mode_funnel_inactive");
    /** Funnel mode (hover) */
    public static final ResourceLocation TOPBAR_FUNNEL_HOVER = topbarTexture("mode_funnel_hover");
    /** Funnel mode (active) */
    public static final ResourceLocation TOPBAR_FUNNEL_ACTIVE = topbarTexture("mode_funnel_active");
    /** Funnel mode (pressed) */
    public static final ResourceLocation TOPBAR_FUNNEL_PRESSED = topbarTexture("mode_funnel_pressed");

    /** Rotate mode (inactive) */
    public static final ResourceLocation TOPBAR_ROTATE_INACTIVE = topbarTexture("mode_rotate_inactive");
    /** Rotate mode (hover) */
    public static final ResourceLocation TOPBAR_ROTATE_HOVER = topbarTexture("mode_rotate_hover");
    /** Rotate mode (active) */
    public static final ResourceLocation TOPBAR_ROTATE_ACTIVE = topbarTexture("mode_rotate_active");
    /** Rotate mode (pressed) */
    public static final ResourceLocation TOPBAR_ROTATE_PRESSED = topbarTexture("mode_rotate_pressed");

    /** Quick build (inactive) */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_INACTIVE = topbarTexture("quick_build_inactive");
    /** Quick build (hover) */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_HOVER = topbarTexture("quick_build_hover");
    /** Quick build (active) */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_ACTIVE = topbarTexture("quick_build_active");
    /** Quick build (pressed) */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_PRESSED = topbarTexture("quick_build_pressed");

    /** Chain mine (inactive) */
    public static final ResourceLocation TOPBAR_ULTIMINE_INACTIVE = topbarTexture("ultimine_inactive");
    /** Chain mine (hover) */
    public static final ResourceLocation TOPBAR_ULTIMINE_HOVER = topbarTexture("ultimine_hover");
    /** Chain mine (active) */
    public static final ResourceLocation TOPBAR_ULTIMINE_ACTIVE = topbarTexture("ultimine_active");
    /** Chain mine (pressed) */
    public static final ResourceLocation TOPBAR_ULTIMINE_PRESSED = topbarTexture("ultimine_pressed");

    /** Chunk view (inactive) */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_INACTIVE = topbarTexture("chunk_view_inactive");
    /** Chunk view (hover) */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_HOVER = topbarTexture("chunk_view_hover");
    /** Chunk view (active) */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_ACTIVE = topbarTexture("chunk_view_active");
    /** Chunk view (pressed) */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_PRESSED = topbarTexture("chunk_view_pressed");

    /** Settings gear (inactive) */
    public static final ResourceLocation TOPBAR_GEAR_INACTIVE = topbarTexture("settings_gear_inactive");
    /** Settings gear (hover) */
    public static final ResourceLocation TOPBAR_GEAR_HOVER = topbarTexture("settings_gear_hover");
    /** Settings gear (active) */
    public static final ResourceLocation TOPBAR_GEAR_ACTIVE = topbarTexture("settings_gear_active");
    /** Settings gear (pressed) */
    public static final ResourceLocation TOPBAR_GEAR_PRESSED = topbarTexture("settings_gear_pressed");

    /** Quest detect (inactive) */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_INACTIVE = topbarTexture("quest_detect_inactive");
    /** Quest detect (hover) */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_HOVER = topbarTexture("quest_detect_hover");
    /** Quest detect (active) */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_ACTIVE = topbarTexture("quest_detect_active");
    /** Quest detect (pressed) */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_PRESSED = topbarTexture("quest_detect_pressed");

    // ======================== Utility methods ========================

    /** Builds QuickBuild texture paths */
    private static ResourceLocation quickBuildTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/quickbuild/" + key + ".png");
        return id == null ? ResourceLocation.withDefaultNamespace("missingno") : id;
    }

    /** Builds top-bar texture paths */
    private static ResourceLocation topbarTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/topbar/" + key + ".png");
        return id == null ? ResourceLocation.withDefaultNamespace("missingno") : id;
    }

    private BuilderScreenConstants() {
        // Utility class, prevents instantiation
    }
}
