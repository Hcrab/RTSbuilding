package com.rtsbuilding.rtsbuilding.client.screen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * BuilderScreen 使用的布局常量定义。
 * <p>
 * 所有与屏幕布局、面板尺寸、间距相关的常量集中在此，
 * 便于统一调整 UI 布局参数。
 */
public final class BuilderScreenConstants {

    // ======================== 顶部栏 ========================
    /** 顶部栏高度 */
    public static final int TOP_H = 52;
    /** 顶部按钮间距 */
    public static final int TOP_BUTTON_GAP = 5;
    /** 顶部按钮高度 */
    public static final int TOP_BUTTON_H = 24;
    /** 顶部按钮最小宽度 */
    public static final int MIN_TOP_BUTTON_W = 28;
    /** 模式按钮宽度 */
    public static final int TOP_MODE_BUTTON_W = 32;
    /** 图标按钮宽度 */
    public static final int TOP_ICON_BUTTON_W = 32;

    // ======================== 底部面板 ========================
    /** 默认底部面板高度 */
    public static final int DEFAULT_BOTTOM_H = 110;
    /** 底部面板最小高度 */
    public static final int MIN_BOTTOM_H = 72;
    /** 底部面板最大高度 */
    public static final int MAX_BOTTOM_H = 320;
    /** 底部面板内边距 */
    public static final int BOTTOM_PANEL_PADDING = 8;
    /** 底部面板标题栏高度 */
    public static final int BOTTOM_PANEL_HEADER_H = 18;
    /** 存储网格最小行数 */
    public static final int MIN_STORAGE_GRID_ROWS = 2;
    /** 网格底部内边距 */
    public static final int GRID_BOTTOM_PADDING = 4;

    // ======================== 槽位/格子 ========================
    /** 存储网格单个格子大小 */
    public static final int SLOT = 22;
    /** 快捷栏单个格子大小 */
    public static final int HOTBAR_SLOT = 18;
    /** 快捷栏格子间距 */
    public static final int HOTBAR_PITCH = 20;
    /** 工具快捷栏物品槽位数 */
    public static final int TOOL_HOTBAR_ITEM_SLOTS = 8;
    /** 空手按钮索引（第9格） */
    public static final int EMPTY_HAND_BUTTON_INDEX = 8;
    /** 工具区域高度 */
    public static final int TOOL_AREA_H = HOTBAR_SLOT;

    // ======================== 搜索/排序 ========================
    /** 搜索清除按钮大小 */
    public static final int SEARCH_CLEAR_SIZE = 12;
    /** 排序按钮大小 */
    public static final int SORT_BUTTON_SIZE = 16;

    // ======================== 合成面板 ========================
    /** 合成面板宽度 */
    public static final int CRAFT_PANEL_W = 126;
    /** 合成面板与存储网格的间距 */
    public static final int CRAFT_PANEL_GAP = 6;
    /** 合成面板列数 */
    public static final int CRAFT_PANEL_COLS = 4;
    /** 合成面板槽位大小 */
    public static final int CRAFT_PANEL_SLOT = 18;
    /** 合成面板行间距 */
    public static final int CRAFT_PANEL_PITCH = 20;
    /** 合成搜索框高度 */
    public static final int CRAFT_PANEL_SEARCH_H = 12;
    /** 合成应用按钮宽度 */
    public static final int CRAFT_PANEL_APPLY_W = 18;
    /** 合成切换按钮宽度 */
    public static final int CRAFT_PANEL_TOGGLE_W = 38;
    /** 合成底座中央按钮大小 */
    public static final int CRAFT_DOCK_C_SIZE = 18;
    /** 合成底座槽位大小 */
    public static final int CRAFT_DOCK_SLOT_SIZE = 10;
    /** 合成底座间距 */
    public static final int CRAFT_DOCK_GAP = 2;
    /** 存储与最近物品间距 */
    public static final int STORAGE_RECENT_GAP = 6;

    // ======================== 分类面板 ========================
    /** 分类面板宽度 */
    public static final int CATEGORY_W = 124;
    /** 分类行高 */
    public static final int CATEGORY_ROW_H = 11;
    /** 分类文字缩放比例 */
    public static final float CATEGORY_TEXT_SCALE = 0.84F;

    // ======================== 交互轮盘 ========================
    /** 交互轮盘单页大小 */
    public static final int INTERACT_WHEEL_PAGE_SIZE = 10;
    /** 交互轮盘半径 */
    public static final int INTERACT_WHEEL_RADIUS = 68;
    /** 交互轮盘槽位大小 */
    public static final int INTERACT_WHEEL_SLOT = 18;
    /** 交互轮盘槽位半长 */
    public static final int INTERACT_WHEEL_SLOT_HALF = INTERACT_WHEEL_SLOT / 2;

    // ======================== 快速建造面板 ========================
    /** 快速建造面板宽度 */
    public static final int QUICK_BUILD_PANEL_W = 188;
    /** 快速建造面板高度 */
    public static final int QUICK_BUILD_PANEL_H = 216;
    /** 快速建造面板最小高度 */
    public static final int QUICK_BUILD_PANEL_MIN_H = 156;
    /** 快速建造形状槽位大小 */
    public static final int QUICK_BUILD_SHAPE_SLOT = 32;
    /** 快速建造形状间距 */
    public static final int QUICK_BUILD_SHAPE_GAP = 8;
    /** 快速建造齿轮菜单宽度 */
    public static final int QUICK_BUILD_GEAR_MENU_W = 148;
    /** 快速建造齿轮行高 */
    public static final int QUICK_BUILD_GEAR_ROW_H = 18;

    // ======================== 连锁挖掘面板 ========================
    /** 连锁挖掘面板宽度 */
    public static final int ULTIMINE_PANEL_W = 238;
    /** 连锁挖掘面板高度 */
    public static final int ULTIMINE_PANEL_H = 122;
    /** 连锁挖掘最小限制 */
    public static final int ULTIMINE_MIN_LIMIT = 1;
    /** 连锁挖掘最大限制 */
    public static final int ULTIMINE_MAX_LIMIT = 256;

    // ======================== 形状轮盘 ========================
    /** 形状轮盘半径 */
    public static final int SHAPE_WHEEL_RADIUS = 52;
    /** 形状轮盘槽位大小 */
    public static final int SHAPE_WHEEL_SLOT = 22;
    /** 形状最大尺寸 */
    public static final int SHAPE_MAX_DIMENSION = 32;
    /** 形状最大偏移 */
    public static final int SHAPE_MAX_OFFSET = SHAPE_MAX_DIMENSION - 1;
    /** 形状最大半径 */
    public static final int SHAPE_MAX_RADIUS = 32;
    /** 形状旋转步进角度 */
    public static final int SHAPE_ROTATE_STEP_DEGREES = 15;
    /** 形状历史记录上限 */
    public static final int SHAPE_HISTORY_LIMIT = 24;

    // ======================== 形状上下文面板 ========================
    /** 形状上下文面板宽度 */
    public static final int SHAPE_CONTEXT_PANEL_W = 148;
    /** 形状上下文面板 X 边距 */
    public static final int SHAPE_CONTEXT_PANEL_X_MARGIN = 10;
    /** 形状上下文面板 Y 坐标 */
    public static final int SHAPE_CONTEXT_PANEL_Y = TOP_H + 10;
    /** 形状上下文行高 */
    public static final int SHAPE_CONTEXT_ROW_H = 14;

    // ======================== 漏斗缓冲面板 ========================
    /** 漏斗缓冲面板宽度 */
    public static final int FUNNEL_BUFFER_PANEL_W = 132;
    /** 漏斗缓冲行高 */
    public static final int FUNNEL_BUFFER_ROW_H = 22;
    /** 漏斗缓冲切换按钮宽度 */
    public static final int FUNNEL_BUFFER_TOGGLE_W = 60;
    /** 漏斗缓冲切换按钮高度 */
    public static final int FUNNEL_BUFFER_TOGGLE_H = 16;

    // ======================== 齿轮菜单（设置） ========================
    /** 齿轮菜单高度 */
    public static final int GEAR_MENU_H = 284;
    /** 齿轮菜单最小高度 */
    public static final int GEAR_MENU_MIN_H = 168;
    /** 齿轮菜单内容高度 */
    public static final int GEAR_MENU_CONTENT_H = 508;

    // ======================== 任务检测弹窗 ========================
    /** 任务检测弹窗宽度 */
    public static final int QUEST_DETECT_POPUP_W = 178;
    /** 任务检测弹窗高度 */
    public static final int QUEST_DETECT_POPUP_H = 48;

    // ======================== 存储扫描弹窗 ========================
    /** 存储扫描弹窗宽度 */
    public static final int STORAGE_SCAN_POPUP_W = 150;
    /** 存储扫描弹窗高度 */
    public static final int STORAGE_SCAN_POPUP_H = 30;

    // ======================== 输入 / 渲染控制 ========================
    /** 中键拖拽阈值（像素） */
    public static final double MIDDLE_CLICK_DRAG_THRESHOLD = 1.5D;
    /** 默认 RTS UI 缩放 */
    public static final double DEFAULT_RTS_GUI_SCALE = 2.0D;
    /** 最小 RTS UI 缩放 */
    public static final double MIN_RTS_GUI_SCALE = 1.0D;
    /** 最大 RTS UI 缩放 */
    public static final double MAX_RTS_GUI_SCALE = 4.0D;
    /** RTS UI 缩放步进 */
    public static final double RTS_GUI_SCALE_STEP = 0.5D;
    /** 模态层 Z 轴向深度 */
    public static final float RTS_MODAL_LAYER_Z = 400.0F;
    /** 受伤闪光持续时间（毫秒） */
    public static final long DAMAGE_FLASH_DURATION_MS = 300L;

    // ======================== 杂项 ========================
    /** 漏斗光标使用的物品图标（漏斗） */
    public static final ItemStack FUNNEL_CURSOR_STACK = new ItemStack(net.minecraft.world.item.Items.HOPPER);
    /** "全部"分类标记 */
    public static final String CATEGORY_ALL = "all";
    /** Mod 分类前缀 */
    public static final String CATEGORY_MOD_PREFIX = "mod|";
    /** Tab 分类前缀 */
    public static final String CATEGORY_TAB_PREFIX = "tab|";

    // ======================== 纹理资源 ========================
    /** 形状纹理：方块（非活跃） */
    public static final ResourceLocation SHAPE_BLOCK_INACTIVE = quickBuildTexture("shape_block_inactive");
    /** 形状纹理：方块（悬停） */
    public static final ResourceLocation SHAPE_BLOCK_HOVER = quickBuildTexture("shape_block_hover");
    /** 形状纹理：方块（活跃） */
    public static final ResourceLocation SHAPE_BLOCK_ACTIVE = quickBuildTexture("shape_block_active");
    /** 形状纹理：线条（非活跃） */
    public static final ResourceLocation SHAPE_LINE_INACTIVE = quickBuildTexture("shape_line_inactive");
    /** 形状纹理：线条（悬停） */
    public static final ResourceLocation SHAPE_LINE_HOVER = quickBuildTexture("shape_line_hover");
    /** 形状纹理：线条（活跃） */
    public static final ResourceLocation SHAPE_LINE_ACTIVE = quickBuildTexture("shape_line_active");
    /** 形状纹理：正方形（非活跃） */
    public static final ResourceLocation SHAPE_SQUARE_INACTIVE = quickBuildTexture("shape_square_inactive");
    /** 形状纹理：正方形（悬停） */
    public static final ResourceLocation SHAPE_SQUARE_HOVER = quickBuildTexture("shape_square_hover");
    /** 形状纹理：正方形（活跃） */
    public static final ResourceLocation SHAPE_SQUARE_ACTIVE = quickBuildTexture("shape_square_active");
    /** 形状纹理：墙壁（非活跃） */
    public static final ResourceLocation SHAPE_WALL_INACTIVE = quickBuildTexture("shape_wall_inactive");
    /** 形状纹理：墙壁（悬停） */
    public static final ResourceLocation SHAPE_WALL_HOVER = quickBuildTexture("shape_wall_hover");
    /** 形状纹理：墙壁（活跃） */
    public static final ResourceLocation SHAPE_WALL_ACTIVE = quickBuildTexture("shape_wall_active");
    /** 形状纹理：圆（非活跃） */
    public static final ResourceLocation SHAPE_CIRCLE_INACTIVE = quickBuildTexture("shape_circle_inactive");
    /** 形状纹理：圆（悬停） */
    public static final ResourceLocation SHAPE_CIRCLE_HOVER = quickBuildTexture("shape_circle_hover");
    /** 形状纹理：圆（活跃） */
    public static final ResourceLocation SHAPE_CIRCLE_ACTIVE = quickBuildTexture("shape_circle_active");
    /** 形状纹理：立方体（非活跃） */
    public static final ResourceLocation SHAPE_BOX_INACTIVE = quickBuildTexture("shape_box_inactive");
    /** 形状纹理：立方体（悬停） */
    public static final ResourceLocation SHAPE_BOX_HOVER = quickBuildTexture("shape_box_hover");
    /** 形状纹理：立方体（活跃） */
    public static final ResourceLocation SHAPE_BOX_ACTIVE = quickBuildTexture("shape_box_active");

    // ======================== 顶部栏模式纹理 ========================
    /** 交互模式（非活跃） */
    public static final ResourceLocation TOPBAR_INTERACT_INACTIVE = topbarTexture("mode_interact_inactive");
    /** 交互模式（悬停） */
    public static final ResourceLocation TOPBAR_INTERACT_HOVER = topbarTexture("mode_interact_hover");
    /** 交互模式（活跃） */
    public static final ResourceLocation TOPBAR_INTERACT_ACTIVE = topbarTexture("mode_interact_active");
    /** 交互模式（按下） */
    public static final ResourceLocation TOPBAR_INTERACT_PRESSED = topbarTexture("mode_interact_pressed");

    /** 链接模式（非活跃） */
    public static final ResourceLocation TOPBAR_LINK_INACTIVE = topbarTexture("mode_link_inactive");
    /** 链接模式（悬停） */
    public static final ResourceLocation TOPBAR_LINK_HOVER = topbarTexture("mode_link_hover");
    /** 链接模式（活跃） */
    public static final ResourceLocation TOPBAR_LINK_ACTIVE = topbarTexture("mode_link_active");
    /** 链接模式（按下） */
    public static final ResourceLocation TOPBAR_LINK_PRESSED = topbarTexture("mode_link_pressed");

    /** 漏斗模式（非活跃） */
    public static final ResourceLocation TOPBAR_FUNNEL_INACTIVE = topbarTexture("mode_funnel_inactive");
    /** 漏斗模式（悬停） */
    public static final ResourceLocation TOPBAR_FUNNEL_HOVER = topbarTexture("mode_funnel_hover");
    /** 漏斗模式（活跃） */
    public static final ResourceLocation TOPBAR_FUNNEL_ACTIVE = topbarTexture("mode_funnel_active");
    /** 漏斗模式（按下） */
    public static final ResourceLocation TOPBAR_FUNNEL_PRESSED = topbarTexture("mode_funnel_pressed");

    /** 旋转模式（非活跃） */
    public static final ResourceLocation TOPBAR_ROTATE_INACTIVE = topbarTexture("mode_rotate_inactive");
    /** 旋转模式（悬停） */
    public static final ResourceLocation TOPBAR_ROTATE_HOVER = topbarTexture("mode_rotate_hover");
    /** 旋转模式（活跃） */
    public static final ResourceLocation TOPBAR_ROTATE_ACTIVE = topbarTexture("mode_rotate_active");
    /** 旋转模式（按下） */
    public static final ResourceLocation TOPBAR_ROTATE_PRESSED = topbarTexture("mode_rotate_pressed");

    /** 快速建造（非活跃） */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_INACTIVE = topbarTexture("quick_build_inactive");
    /** 快速建造（悬停） */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_HOVER = topbarTexture("quick_build_hover");
    /** 快速建造（活跃） */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_ACTIVE = topbarTexture("quick_build_active");
    /** 快速建造（按下） */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_PRESSED = topbarTexture("quick_build_pressed");

    /** 连锁挖掘（非活跃） */
    public static final ResourceLocation TOPBAR_ULTIMINE_INACTIVE = topbarTexture("ultimine_inactive");
    /** 连锁挖掘（悬停） */
    public static final ResourceLocation TOPBAR_ULTIMINE_HOVER = topbarTexture("ultimine_hover");
    /** 连锁挖掘（活跃） */
    public static final ResourceLocation TOPBAR_ULTIMINE_ACTIVE = topbarTexture("ultimine_active");
    /** 连锁挖掘（按下） */
    public static final ResourceLocation TOPBAR_ULTIMINE_PRESSED = topbarTexture("ultimine_pressed");

    /** 区块视图（非活跃） */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_INACTIVE = topbarTexture("chunk_view_inactive");
    /** 区块视图（悬停） */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_HOVER = topbarTexture("chunk_view_hover");
    /** 区块视图（活跃） */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_ACTIVE = topbarTexture("chunk_view_active");
    /** 区块视图（按下） */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_PRESSED = topbarTexture("chunk_view_pressed");

    /** 设置齿轮（非活跃） */
    public static final ResourceLocation TOPBAR_GEAR_INACTIVE = topbarTexture("settings_gear_inactive");
    /** 设置齿轮（悬停） */
    public static final ResourceLocation TOPBAR_GEAR_HOVER = topbarTexture("settings_gear_hover");
    /** 设置齿轮（活跃） */
    public static final ResourceLocation TOPBAR_GEAR_ACTIVE = topbarTexture("settings_gear_active");
    /** 设置齿轮（按下） */
    public static final ResourceLocation TOPBAR_GEAR_PRESSED = topbarTexture("settings_gear_pressed");

    /** 任务检测（非活跃） */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_INACTIVE = topbarTexture("quest_detect_inactive");
    /** 任务检测（悬停） */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_HOVER = topbarTexture("quest_detect_hover");
    /** 任务检测（活跃） */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_ACTIVE = topbarTexture("quest_detect_active");
    /** 任务检测（按下） */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_PRESSED = topbarTexture("quest_detect_pressed");

    // ======================== 辅助方法 ========================

    /** 构建快速建造纹理路径 */
    private static ResourceLocation quickBuildTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/quickbuild/" + key + ".png");
        return id == null ? ResourceLocation.withDefaultNamespace("missingno") : id;
    }

    /** 构建顶部栏纹理路径 */
    private static ResourceLocation topbarTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/topbar/" + key + ".png");
        return id == null ? ResourceLocation.withDefaultNamespace("missingno") : id;
    }

    private BuilderScreenConstants() {
        // 工具类，禁止实例化
    }
}
