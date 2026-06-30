package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import net.minecraft.resources.ResourceLocation;

/**
 * BuilderScreen 常量——包含 GUI 面板布局所需的全部尺寸和间距常量。
 *
 * <p>从旧 {@code client_old.screen.standalone.BuilderScreenConstants} 移植而来，
 * 保持值与旧版一致以确保面板渲染不受影响。</p>
 */
public final class BuilderScreenConstants {

    private BuilderScreenConstants() {}

    // ======================== Top bar ========================
    /** Top bar height in virtual pixels */
    public static final int TOP_H = 60;
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

    // ======================== Shape context panel ========================
    /** Shape context panel width */
    public static final int SHAPE_CONTEXT_PANEL_W = 148;
    /** Shape context panel X margin */
    public static final int SHAPE_CONTEXT_PANEL_X_MARGIN = 10;
    /** Shape context panel Y coordinate */
    public static final int SHAPE_CONTEXT_PANEL_Y = TOP_H + 10;
    /** Shape context row height */
    public static final int SHAPE_CONTEXT_ROW_H = 14;

    // ======================== Gear menu (settings) ========================
    /** Gear menu default height */
    public static final int GEAR_MENU_H = 520;
    /** Gear menu minimum height */
    public static final int GEAR_MENU_MIN_H = 220;
    /** Gear menu content height */
    public static final int GEAR_MENU_CONTENT_H = 724;

    // ======================== Funnel buffer panel ========================
    /** Funnel buffer panel width */
    public static final int FUNNEL_BUFFER_PANEL_W = 132;
    /** Funnel buffer row height */
    public static final int FUNNEL_BUFFER_ROW_H = 22;
    /** Funnel buffer toggle width */
    public static final int FUNNEL_BUFFER_TOGGLE_W = 60;
    /** Funnel buffer toggle height */
    public static final int FUNNEL_BUFFER_TOGGLE_H = 16;

    // ======================== Top-bar mode textures ========================

    /** 顶部栏贴图文件总宽度（像素，双主题翻倍） */
    public static final int TOPBAR_TEX_W = 1024;
    /** 顶部栏贴图文件总高度（像素） */
    public static final int TOPBAR_TEX_H = 1536;
    /** 单个状态帧高度（像素）—— 4 帧竖排：inactive / hover / active / pressed */
    public static final int TOPBAR_TEX_FRAME_H = 512;

    /** 精灵图 v 偏移：未激活（默认态） */
    public static final int TOPBAR_V_INACTIVE = 512;
    /** 精灵图 v 偏移：激活态（默认态） */
    public static final int TOPBAR_V_ACTIVE = 1024;
    /** 精灵图 v 偏移：未按下+悬停 */
    public static final int TOPBAR_V_HOVER = 0;
    /** 交互模式按钮贴图 */
    public static final ResourceLocation TOPBAR_INTERACT = topbarTexture("click_button");
    /** 链接存储按钮贴图 */
    public static final ResourceLocation TOPBAR_LINK = topbarTexture("bind_button");
    /** 漏斗模式按钮贴图 */
    public static final ResourceLocation TOPBAR_FUNNEL = topbarTexture("item_pickup_button");
    /** 旋转模式按钮贴图 */
    public static final ResourceLocation TOPBAR_ROTATE = topbarTexture("direction_rotation_button");
    /** 快速建造按钮贴图 */
    public static final ResourceLocation TOPBAR_QUICK_BUILD = topbarTexture("quick_construction_button");
    /** 任务探测按钮贴图 */
    public static final ResourceLocation TOPBAR_QUEST_DETECT = topbarTexture("quest_detect_button");
    /** 区块视图按钮贴图 */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW = topbarTexture("block_display_button");
    /** 齿轮设置按钮贴图 */
    public static final ResourceLocation TOPBAR_GEAR = topbarTexture("setting_button");

    // ======================== Miscellaneous ========================
    /** RTS GUI scale default */
    public static final double DEFAULT_RTS_GUI_SCALE = 2.0D;
    /** RTS GUI scale minimum */
    public static final double MIN_RTS_GUI_SCALE = 1.0D;
    /** RTS GUI scale maximum */
    public static final double MAX_RTS_GUI_SCALE = 6.0D;
    /** RTS GUI scale step */
    public static final double RTS_GUI_SCALE_STEP = 0.5D;
    /** Search box character limit */
    public static final int SEARCH_MAX_LENGTH = 128;
    /** "All" category token */
    public static final String CATEGORY_ALL = "all";
    /** Mod category prefix */
    public static final String CATEGORY_MOD_PREFIX = "mod|";
    /** Tab category prefix */
    public static final String CATEGORY_TAB_PREFIX = "tab|";

    /** 构建顶部栏纹理路径 */
    private static ResourceLocation topbarTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/topbar/" + key + ".png");
        return id == null ? ResourceLocation.withDefaultNamespace("missingno") : id;
    }

}
