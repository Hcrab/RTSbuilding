package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.client.widget.WindowSlider;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 快速建造面板：形状选择 + 填充模式 + 旋转控制。
 * <p>
 * 继承 {@link RtsWindowPanel} 获得窗口能力。
 * 向后兼容 {@code isQuickBuildOpen() / setQuickBuildOpen() / toggleOpen()}。
 */
public final class QuickBuildPanel extends RtsWindowPanel {

    /** 右侧列（填充/旋转）相对于窗口左边缘的偏移 */
    private static final int RIGHT_COL_X = 88;

    /** 形状按钮行间距 */
    private static final int SHAPE_ROW_PITCH = QUICK_BUILD_SHAPE_SLOT + 6;
    private static final int MODE_TOGGLE_H = 18;
    private static final int MODE_TOGGLE_GAP = 4;
    private static final int MODE_ROW_TOP = 5;
    /** 2×2 布局后内容起始偏移（第 2 行标签下方 + 间距） */
    private static final int SECTION_TOP = 53;
    /** 连锁破坏滑条 */

    // ======================== 面板尺寸 ========================
    private static final int QUICK_BUILD_PANEL_W = 178;
    /** 基础面板高度（已包含 2×2 模式切换的额外高度） */
    private static final int QUICK_BUILD_PANEL_H = 244;
    private static final int QUICK_BUILD_DESTROY_PANEL_H = QUICK_BUILD_PANEL_H + SHAPE_ROW_PITCH;
    /** 智能放置模式面板高度（无形状选择，内容更少） */
    private static final int QUICK_BUILD_SMART_PLACE_PANEL_H = QUICK_BUILD_PANEL_H;
    private static final int QUICK_BUILD_PANEL_MIN_H = 222;

    /** 底部提示文字区域额外高度 */
    private static final int BOTTOM_INFO_H = 72;
    private static final int BOTTOM_TEXT_MAX_LINES = 3;

    /** 模式切换布局：2×2，第 2 行起始位置 */
    private static final int MODE_ROW_2_TOP = MODE_ROW_TOP + MODE_TOGGLE_H + MODE_TOGGLE_GAP;
    /** 2×2 模式切换增加的高度 */
    private static final int MODE_2X2_EXTRA_HEIGHT = MODE_TOGGLE_H + MODE_TOGGLE_GAP;

    /** 选择指示器贴图 */
    private static final ResourceLocation SELECTION_DOT_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/general/mode_button.png");

    // ======================== 精灵图参数 ========================
    private static final int SHAPE_SHEET_W = 450;
    private static final int SHAPE_SHEET_H = 900;
    private static final int SHAPE_STATE_H = 450;
    private static final int MODE_BUTTON_SHEET_W = 512;
    private static final int MODE_BUTTON_STATE_H = 512;

    /** 模式按钮贴图：512×1536，3 行状态，每行 512px */
    private static final int MODE_BUTTON_H = MODE_BUTTON_STATE_H * 3;

    // ======================== 形状定义 ========================
    private static final BuildShape[] BUILD_SHAPES = {
            BuildShape.BLOCK,
            BuildShape.LINE,
            BuildShape.SQUARE,
            BuildShape.WALL,
            BuildShape.CIRCLE,
            BuildShape.CYLINDER,
            BuildShape.BALL,
            BuildShape.BOX
    };

    private static final AreaMineShape[] DESTROY_SHAPES = {
            AreaMineShape.CHAIN,
            AreaMineShape.BLOCK,
            AreaMineShape.LINE,
            AreaMineShape.SQUARE,
            AreaMineShape.WALL,
            AreaMineShape.CIRCLE,
            AreaMineShape.CYLINDER,
            AreaMineShape.BALL,
            AreaMineShape.BOX
    };

    /** 各形状按钮对应的悬浮提示翻译键 */
    private static final String[] BUILD_SHAPE_TOOLTIP_KEYS = {
            "screen.rtsbuilding.tooltip.shape_block",
            "screen.rtsbuilding.tooltip.shape_line",
            "screen.rtsbuilding.tooltip.shape_square",
            "screen.rtsbuilding.tooltip.shape_wall",
            "screen.rtsbuilding.tooltip.shape_circle",
            "screen.rtsbuilding.tooltip.shape_cylinder",
            "screen.rtsbuilding.tooltip.shape_ball",
            "screen.rtsbuilding.tooltip.shape_box"
    };

    private static final String[] DESTROY_SHAPE_TOOLTIP_KEYS = {
            "screen.rtsbuilding.tooltip.shape_chain",
            "screen.rtsbuilding.tooltip.shape_block",
            "screen.rtsbuilding.tooltip.shape_line",
            "screen.rtsbuilding.tooltip.shape_square",
            "screen.rtsbuilding.tooltip.shape_wall",
            "screen.rtsbuilding.tooltip.shape_circle",
            "screen.rtsbuilding.tooltip.shape_cylinder",
            "screen.rtsbuilding.tooltip.shape_ball",
            "screen.rtsbuilding.tooltip.shape_box"
    };

    /** 各形状按钮对应的精灵图纹理 */
    private static final ResourceLocation[] BUILD_SHAPE_TEXTURES = {
            QUICK_BUILD_SINGLE_BLOCK,
            QUICK_BUILD_LINE_BLOCK,
            QUICK_BUILD_SQUARE_BLOCK,
            QUICK_BUILD_WALL_BLOCK,
            QUICK_BUILD_CIRCLE_BLOCK,
            QUICK_BUILD_CYLINDER_BLOCK,
            QUICK_BUILD_BALL_BLOCK,
            QUICK_BUILD_BOX_BLOCK
    };

    private static final ResourceLocation[] DESTROY_SHAPE_TEXTURES = {
            QUICK_BUILD_CHAIN_BLOCK,
            QUICK_BUILD_SINGLE_BLOCK,
            QUICK_BUILD_LINE_BLOCK,
            QUICK_BUILD_SQUARE_BLOCK,
            QUICK_BUILD_WALL_BLOCK,
            QUICK_BUILD_CIRCLE_BLOCK,
            QUICK_BUILD_CYLINDER_BLOCK,
            QUICK_BUILD_BALL_BLOCK,
            QUICK_BUILD_BOX_BLOCK
    };

    // ======================== 实例 ========================
    private WindowButton[] shapeButtons;
    private WindowButton[] fillModeButtons;
    private QuickBuildMode quickBuildMode = QuickBuildMode.BUILD;
    private BuildShape buildModeShape = BuildShape.BLOCK;
    private AreaMineShape rangeDestroyShape = AreaMineShape.CHAIN;
    private WindowSlider chainLimitSlider;
    private int chainDestroyLimit = 64;
    private boolean advancedRangeDestroySquare;
    private boolean advancedRangeDestroyWall;
    private boolean advancedRangeDestroyCircle;
    private boolean advancedRangeDestroyCylinder;
    private boolean advancedRangeDestroyBall;
    private boolean advancedRangeDestroyBox;
    private boolean circleVertical;
    private boolean cylinderVertical;

    /** 缓存的形状（BUILD），用于检测 fill mode 是否需要重建 */
    private BuildShape lastFillShape;
    /** 缓存的形状（DESTROY），解决 CHAIN↔BLOCK 映射到相同 BuildShape 的问题 */
    private AreaMineShape lastAreaMineShape;
    /** 直线连接模式按钮 */
    private WindowButton connectToggle;

    // ===== 智能放置 =====
    private final SmartPlaceHandler smartPlaceHandler = new SmartPlaceHandler();
    private WindowButton[] smartPlaceModeButtons;
    private WindowSlider fillCountSlider;
    private WindowSlider detectionDiameterSlider;

    // ===== 高级破坏 =====
    private AdvancedDestroySubMode advDestroySubMode = AdvancedDestroySubMode.RECTANGLE;
    private final AdvancedDestroyOptions advDestroyOptions = new AdvancedDestroyOptions();
    private final AdvancedDestroyHandler advDestroyHandler = new AdvancedDestroyHandler(advDestroyOptions);
    private WindowButton[] advDestroySubModeButtons;
    // 矩形滑条（6条，同轴配对一行）
    private WindowSlider rectSliderPlusX, rectSliderMinusX;
    private WindowSlider rectSliderPlusY, rectSliderMinusY;
    private WindowSlider rectSliderPlusZ, rectSliderMinusZ;
    private WindowButton[] rectFillModeButtons;
    // 矩形尺寸/区块切换按钮
    private WindowButton rectSizeModeBtn, rectChunkModeBtn;
    // 区块模式 Y 滑条（独立于尺寸模式 Y 滑条）
    private WindowSlider chunkRectSliderPlusY, chunkRectSliderMinusY;
    // 过滤控件
    private WindowButton filterSlotBtn;       // "挖掘方块"槽位
    private WindowButton filterPanelBtn;      // "过滤"按钮
    private WindowButton filterInverseBtn;    // "反选"开关
    private final AdvDestroyFilterPanel filterPanel = new AdvDestroyFilterPanel(r -> {
        advDestroyHandler.markDirty();
        if (screen != null) screen.persistUiState();
    });
    // 世界取块模式
    private boolean worldPickMode = false;
    // 圆柱滑条
    private WindowSlider cylinderRadiusSlider;
    private WindowSlider cylinderPlusHSlider, cylinderMinusHSlider;
    private WindowButton[] cylinderFillModeButtons;
    // 楼梯控件
    private WindowSlider stairsCountSlider;
    private WindowButton stairsRotateButton;
    private WindowButton stairsSymmetricButton;
    // 伐木控件
    private WindowSlider lumberLimitSlider;
    private WindowButton lumberStrongManToggle;
    private WindowButton lumberAllowPlayerBlocksToggle;

    // ======================== 持久化属性 ========================

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.boolField(
                    "quick_build_open",
                    state -> state.quickBuild.quickBuildOpen,
                    (state, v) -> state.quickBuild.quickBuildOpen = v,
                    this::isOpen,
                    v -> setOpen(v)),
            PersistableProperty.enumField(
                    "quick_build_mode",
                    state -> state.quickBuild.quickBuildMode,
                    (state, v) -> state.quickBuild.quickBuildMode = v,
                    () -> this.quickBuildMode,
                    v -> this.quickBuildMode = v,
                    QuickBuildMode.BUILD,
                    QuickBuildMode.class),
            PersistableProperty.intField(
                    "chain_destroy_limit",
                    state -> state.quickBuild.mining.ultimineLimit,
                    (state, v) -> state.quickBuild.mining.ultimineLimit = v,
                    () -> this.chainDestroyLimit,
                    v -> this.chainDestroyLimit = v),
            PersistableProperty.enumField(
                    "area_mine_shape",
                    state -> state.quickBuild.mining.areaMineShape,
                    (state, v) -> state.quickBuild.mining.areaMineShape = v,
                    this::getRangeDestroyShape,
                    v -> this.rangeDestroyShape = v,
                    AreaMineShape.CHAIN,
                    AreaMineShape.class),
            PersistableProperty.boolField(
                    "advanced_range_destroy_square",
                    state -> state.quickBuild.mining.advancedRangeDestroySquare,
                    (state, v) -> state.quickBuild.mining.advancedRangeDestroySquare = v,
                    () -> this.advancedRangeDestroySquare,
                    v -> this.advancedRangeDestroySquare = v),
            PersistableProperty.boolField(
                    "advanced_range_destroy_wall",
                    state -> state.quickBuild.mining.advancedRangeDestroyWall,
                    (state, v) -> state.quickBuild.mining.advancedRangeDestroyWall = v,
                    () -> this.advancedRangeDestroyWall,
                    v -> this.advancedRangeDestroyWall = v),
            PersistableProperty.boolField(
                    "advanced_range_destroy_circle",
                    state -> state.quickBuild.mining.advancedRangeDestroyCircle,
                    (state, v) -> state.quickBuild.mining.advancedRangeDestroyCircle = v,
                    () -> this.advancedRangeDestroyCircle,
                    v -> this.advancedRangeDestroyCircle = v),
            PersistableProperty.boolField(
                    "advanced_range_destroy_cylinder",
                    state -> state.quickBuild.mining.advancedRangeDestroyCylinder,
                    (state, v) -> state.quickBuild.mining.advancedRangeDestroyCylinder = v,
                    () -> this.advancedRangeDestroyCylinder,
                    v -> this.advancedRangeDestroyCylinder = v),
            PersistableProperty.boolField(
                    "round_shape_circle_vertical",
                    state -> state.quickBuild.mining.circleVertical,
                    (state, v) -> state.quickBuild.mining.circleVertical = v,
                    () -> this.circleVertical,
                    v -> this.circleVertical = v),
            PersistableProperty.boolField(
                    "round_shape_cylinder_vertical",
                    state -> state.quickBuild.mining.cylinderVertical,
                    (state, v) -> state.quickBuild.mining.cylinderVertical = v,
                    () -> this.cylinderVertical,
                    v -> this.cylinderVertical = v),
            PersistableProperty.boolField(
                    "advanced_range_destroy_ball",
                    state -> state.quickBuild.mining.advancedRangeDestroyBall,
                    (state, v) -> state.quickBuild.mining.advancedRangeDestroyBall = v,
                    () -> this.advancedRangeDestroyBall,
                    v -> this.advancedRangeDestroyBall = v),
            PersistableProperty.boolField(
                    "advanced_range_destroy_box",
                    state -> state.quickBuild.mining.advancedRangeDestroyBox,
                    (state, v) -> state.quickBuild.mining.advancedRangeDestroyBox = v,
                    () -> this.advancedRangeDestroyBox,
                    v -> this.advancedRangeDestroyBox = v),
            // 智能放置模式持久化属性
            PersistableProperty.enumField(
                    "smart_place_mode",
                    state -> state.quickBuild.smartPlace.smartPlaceMode,
                    (state, v) -> state.quickBuild.smartPlace.smartPlaceMode = v,
                    () -> this.smartPlaceHandler.getOptions().mode,
                    v -> this.smartPlaceHandler.getOptions().mode = v,
                    SmartPlaceMode.HOLE_FILL,
                    SmartPlaceMode.class),
            PersistableProperty.intField(
                    "smart_place_fill_count",
                    state -> state.quickBuild.smartPlace.fillCount,
                    (state, v) -> state.quickBuild.smartPlace.fillCount = v,
                    () -> this.smartPlaceHandler.getOptions().fillCount,
                    v -> this.smartPlaceHandler.getOptions().fillCount = v),
            PersistableProperty.intField(
                    "smart_place_detection_diameter",
                    state -> state.quickBuild.smartPlace.detectionDiameter,
                    (state, v) -> state.quickBuild.smartPlace.detectionDiameter = v,
                    () -> this.smartPlaceHandler.getOptions().detectionDiameter,
                    v -> this.smartPlaceHandler.getOptions().detectionDiameter = v),
            // 高级破坏持久化属性
            PersistableProperty.enumField(
                    "adv_destroy_sub_mode",
                    state -> state.quickBuild.advancedDestroy.subMode,
                    (state, v) -> state.quickBuild.advancedDestroy.subMode = v,
                    () -> this.advDestroySubMode,
                    v -> this.advDestroySubMode = v,
                    AdvancedDestroySubMode.RECTANGLE,
                    AdvancedDestroySubMode.class),
            PersistableProperty.intField("adv_rect_plus_x",
                    state -> state.quickBuild.advancedDestroy.rectPlusX,
                    (state, v) -> state.quickBuild.advancedDestroy.rectPlusX = v,
                    () -> this.advDestroyOptions.getRectPlusX(),
                    v -> this.advDestroyOptions.setRectPlusX(v)),
            PersistableProperty.intField("adv_rect_minus_x",
                    state -> state.quickBuild.advancedDestroy.rectMinusX,
                    (state, v) -> state.quickBuild.advancedDestroy.rectMinusX = v,
                    () -> this.advDestroyOptions.getRectMinusX(),
                    v -> this.advDestroyOptions.setRectMinusX(v)),
            PersistableProperty.intField("adv_rect_plus_y",
                    state -> state.quickBuild.advancedDestroy.rectPlusY,
                    (state, v) -> state.quickBuild.advancedDestroy.rectPlusY = v,
                    () -> this.advDestroyOptions.getRectPlusY(),
                    v -> this.advDestroyOptions.setRectPlusY(v)),
            PersistableProperty.intField("adv_rect_minus_y",
                    state -> state.quickBuild.advancedDestroy.rectMinusY,
                    (state, v) -> state.quickBuild.advancedDestroy.rectMinusY = v,
                    () -> this.advDestroyOptions.getRectMinusY(),
                    v -> this.advDestroyOptions.setRectMinusY(v)),
            PersistableProperty.intField("adv_rect_plus_z",
                    state -> state.quickBuild.advancedDestroy.rectPlusZ,
                    (state, v) -> state.quickBuild.advancedDestroy.rectPlusZ = v,
                    () -> this.advDestroyOptions.getRectPlusZ(),
                    v -> this.advDestroyOptions.setRectPlusZ(v)),
            PersistableProperty.intField("adv_rect_minus_z",
                    state -> state.quickBuild.advancedDestroy.rectMinusZ,
                    (state, v) -> state.quickBuild.advancedDestroy.rectMinusZ = v,
                    () -> this.advDestroyOptions.getRectMinusZ(),
                    v -> this.advDestroyOptions.setRectMinusZ(v)),
            PersistableProperty.enumField("adv_rect_fill_mode",
                    state -> state.quickBuild.advancedDestroy.rectFillMode,
                    (state, v) -> state.quickBuild.advancedDestroy.rectFillMode = v,
                    () -> this.advDestroyOptions.getRectFillMode(),
                    v -> this.advDestroyOptions.setRectFillMode(v),
                    ShapeFillMode.FILL,
                    ShapeFillMode.class),
            PersistableProperty.intField("adv_cylinder_radius",
                    state -> state.quickBuild.advancedDestroy.cylinderRadius,
                    (state, v) -> state.quickBuild.advancedDestroy.cylinderRadius = v,
                    () -> this.advDestroyOptions.getCylinderRadius(),
                    v -> this.advDestroyOptions.setCylinderRadius(v)),
            PersistableProperty.intField("adv_cylinder_plus_h",
                    state -> state.quickBuild.advancedDestroy.cylinderPlusH,
                    (state, v) -> state.quickBuild.advancedDestroy.cylinderPlusH = v,
                    () -> this.advDestroyOptions.getCylinderPlusH(),
                    v -> this.advDestroyOptions.setCylinderPlusH(v)),
            PersistableProperty.intField("adv_cylinder_minus_h",
                    state -> state.quickBuild.advancedDestroy.cylinderMinusH,
                    (state, v) -> state.quickBuild.advancedDestroy.cylinderMinusH = v,
                    () -> this.advDestroyOptions.getCylinderMinusH(),
                    v -> this.advDestroyOptions.setCylinderMinusH(v)),
            PersistableProperty.enumField("adv_cylinder_fill_mode",
                    state -> state.quickBuild.advancedDestroy.cylinderFillMode,
                    (state, v) -> state.quickBuild.advancedDestroy.cylinderFillMode = v,
                    () -> this.advDestroyOptions.getCylinderFillMode(),
                    v -> this.advDestroyOptions.setCylinderFillMode(v),
                    ShapeFillMode.FILL,
                    ShapeFillMode.class),
            PersistableProperty.intField("adv_stairs_count",
                    state -> state.quickBuild.advancedDestroy.stairsCount,
                    (state, v) -> state.quickBuild.advancedDestroy.stairsCount = v,
                    () -> this.advDestroyOptions.getStairsCount(),
                    v -> this.advDestroyOptions.setStairsCount(v)),
            PersistableProperty.intField("adv_stairs_rotation",
                    state -> state.quickBuild.advancedDestroy.stairsRotation,
                    (state, v) -> state.quickBuild.advancedDestroy.stairsRotation = v,
                    () -> this.advDestroyOptions.getStairsRotation(),
                    v -> this.advDestroyOptions.setStairsRotation(v)),
            PersistableProperty.boolField("adv_stairs_symmetric",
                    state -> state.quickBuild.advancedDestroy.stairsSymmetric,
                    (state, v) -> state.quickBuild.advancedDestroy.stairsSymmetric = v,
                    () -> this.advDestroyOptions.isStairsSymmetric(),
                    v -> this.advDestroyOptions.setStairsSymmetric(v)),
            // 伐木持久化
            PersistableProperty.intField("adv_lumber_limit",
                    state -> state.quickBuild.advancedDestroy.lumberLimit,
                    (state, v) -> state.quickBuild.advancedDestroy.lumberLimit = v,
                    () -> this.advDestroyOptions.getLumberLimit(),
                    v -> this.advDestroyOptions.setLumberLimit(v)),
            PersistableProperty.boolField("adv_lumber_strong_man",
                    state -> state.quickBuild.advancedDestroy.lumberStrongMan,
                    (state, v) -> state.quickBuild.advancedDestroy.lumberStrongMan = v,
                    () -> this.advDestroyOptions.isLumberStrongMan(),
                    v -> this.advDestroyOptions.setLumberStrongMan(v)),
            PersistableProperty.boolField("adv_lumber_allow_player_blocks",
                    state -> state.quickBuild.advancedDestroy.lumberAllowPlayerBlocks,
                    (state, v) -> state.quickBuild.advancedDestroy.lumberAllowPlayerBlocks = v,
                    () -> this.advDestroyOptions.isLumberAllowPlayerBlocks(),
                    v -> this.advDestroyOptions.setLumberAllowPlayerBlocks(v)),
            // 矩形区块模式 + 过滤持久化
            PersistableProperty.enumField("adv_rect_mode",
                    state -> state.quickBuild.advancedDestroy.rectMode,
                    (state, v) -> state.quickBuild.advancedDestroy.rectMode = v,
                    () -> this.advDestroyOptions.getRectMode(),
                    v -> this.advDestroyOptions.setRectMode(v),
                    AdvDestroyRectMode.SIZE,
                    AdvDestroyRectMode.class),
            PersistableProperty.intField("adv_chunk_rect_plus_y",
                    state -> state.quickBuild.advancedDestroy.chunkRectPlusY,
                    (state, v) -> state.quickBuild.advancedDestroy.chunkRectPlusY = v,
                    () -> this.advDestroyOptions.getChunkRectPlusY(),
                    v -> {}),
            PersistableProperty.intField("adv_chunk_rect_minus_y",
                    state -> state.quickBuild.advancedDestroy.chunkRectMinusY,
                    (state, v) -> state.quickBuild.advancedDestroy.chunkRectMinusY = v,
                    () -> this.advDestroyOptions.getChunkRectMinusY(),
                    v -> {}),
            PersistableProperty.boolField("adv_filter_inverse",
                    state -> state.quickBuild.advancedDestroy.filterInverse,
                    (state, v) -> state.quickBuild.advancedDestroy.filterInverse = v,
                    () -> this.advDestroyOptions.isFilterInverse(),
                    v -> this.advDestroyOptions.setFilterInverse(v)),
            PersistableProperty.stringField("adv_filters_json",
                    state -> state.quickBuild.advancedDestroy.filtersJson,
                    (state, v) -> state.quickBuild.advancedDestroy.filtersJson = v,
                    () -> this.advDestroyOptions.saveFiltersToJson(),
                    v -> this.advDestroyOptions.loadFiltersFromJson(v)),
            PersistableProperty.bounds("quick_build", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }

    // ======================== 初始化 ========================

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.open = true;
        this.resizable = false;
        this.buildModeShape = controller.getBuildShape();
        AreaMineShape storedDestroyShape = controller.getAreaMineShape();
        this.rangeDestroyShape = storedDestroyShape == null ? AreaMineShape.CHAIN : storedDestroyShape;
        ensureChainLimitSlider();
        ensureSmartPlaceSliders();
        createShapeButtons();
        createSmartPlaceModeButtons();
        createAdvDestroySubModeButtons();
        ensureAdvDestroySliders();
        this.lastFillShape = controller.getBuildShape();
        this.lastAreaMineShape = this.rangeDestroyShape;
    }

    private void createShapeButtons() {
        shapeButtons = new WindowButton[currentShapeCount()];
        for (int i = 0; i < shapeButtons.length; i++) {
            shapeButtons[i] = createShapeButton(i);
        }
    }

    /**
     * 创建指定索引的形状按钮，使用 WindowButton 内置纹理渲染。
     * 选中状态：始终显示下半（active）贴图；未选中：上半（inactive），悬停时切换至下半。
     */
    private WindowButton createShapeButton(int index) {
        ResourceLocation texture = currentShapeTexture(index);
        boolean selected = isCurrentShapeSelected(index);
        int normalV = selected ? SHAPE_STATE_H : 0;
        WindowButton button = new WindowButton(0, 0,
                QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT,
                Component.empty(),
                texture,
                0, normalV,
                SHAPE_SHEET_W, SHAPE_STATE_H,
                SHAPE_STATE_H, SHAPE_STATE_H,
                SHAPE_SHEET_W, SHAPE_SHEET_H,
                btn -> selectShape(index));
        if (isDestroyModeActive()) {
            button.active = canUseDestroyShape(DESTROY_SHAPES[index]);
        }
        return button;
    }

    /** 当形状切换时刷新所有按钮贴图（选中/未选中状态）。 */
    private void rebuildAllShapeButtons() {
        createShapeButtons();
    }

    private void rebuildFillModeButtons() {
        if (isRangeDestroyChainMode()) {
            this.lastFillShape = controller.getBuildShape();
            this.lastAreaMineShape = this.rangeDestroyShape;
            fillModeButtons = new WindowButton[0];
            this.connectToggle = null;
            return;
        }
        this.lastFillShape = controller.getBuildShape();
        this.lastAreaMineShape = this.rangeDestroyShape;
        List<ShapeFillMode> modes =
                ShapeGeometryUtil.availableFillModes(controller.getBuildShape());
        fillModeButtons = new WindowButton[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            int idx = i;
            fillModeButtons[i] = new WindowButton(0, 0, 84, 20,
                    Component.literal(screen.fillModeLabel(modes.get(i))), btn -> {
                // 直接读写模式对应的独立字段，避免经过 syncActiveToModeFields 中转
                if (isDestroyModeActive()) {
                    screen.getShapeController().setDestroyShapeFillMode(modes.get(idx));
                } else {
                    screen.getShapeController().setBuildShapeFillMode(modes.get(idx));
                }
                screen.persistUiState();
            });
        }
        // 连接模式按钮（LINE/WALL 形状时显示）
        if (controller.getBuildShape() == BuildShape.LINE || controller.getBuildShape() == BuildShape.WALL) {
            this.connectToggle = new WindowButton(0, 0, 84, 20,
                    Component.literal(screen.text("screen.rtsbuilding.quick_build.connect")), btn -> {
                // 直接读写模式对应的独立字段，避免经过 syncActiveToModeFields 中转
                if (isDestroyModeActive()) {
                    boolean next = !screen.getShapeController().isDestroyLineConnected();
                    screen.getShapeController().setDestroyLineConnected(next);
                } else {
                    boolean next = !screen.getShapeController().isBuildLineConnected();
                    screen.getShapeController().setBuildLineConnected(next);
                }
                screen.persistUiState();
            });
        } else {
            this.connectToggle = null;
        }
        BuildShape orientedShape = activeAdvancedShape();
        if (supportsVerticalToggle(orientedShape)) {
            WindowButton[] next = Arrays.copyOf(fillModeButtons, fillModeButtons.length + 1);
            int verticalIndex = fillModeButtons.length;
            next[verticalIndex] = new WindowButton(0, 0, 84, 20,
                    Component.translatable("screen.rtsbuilding.quick_build.vertical"), btn -> {
                BuildShape shape = activeAdvancedShape();
                setRoundShapeVertical(shape, !isRoundShapeVertical(shape));
                screen.clearShapeBuildSession();
                screen.persistUiState();
                rebuildFillModeButtons();
            });
            fillModeButtons = next;
        }
        BuildShape advancedShape = activeAdvancedShape();
        if (supportsAdvancedShape(advancedShape)) {
            WindowButton[] next = Arrays.copyOf(fillModeButtons, fillModeButtons.length + 1);
            int advancedIndex = fillModeButtons.length;
            next[advancedIndex] = new WindowButton(0, 0, 84, 20,
                    Component.translatable("screen.rtsbuilding.quick_build.advanced_box"), btn -> {
                BuildShape shape = activeAdvancedShape();
                setAdvancedShape(shape, !isAdvancedShape(shape));
                screen.clearShapeBuildSession();
                screen.persistUiState();
            });
            fillModeButtons = next;
        }
    }

    private int currentShapeCount() {
        return isDestroyModeActive() ? DESTROY_SHAPES.length : BUILD_SHAPES.length;
    }

    private ResourceLocation currentShapeTexture(int index) {
        return isDestroyModeActive() ? DESTROY_SHAPE_TEXTURES[index] : BUILD_SHAPE_TEXTURES[index];
    }

    private String currentShapeTooltipKey(int index) {
        return isDestroyModeActive() ? DESTROY_SHAPE_TOOLTIP_KEYS[index] : BUILD_SHAPE_TOOLTIP_KEYS[index];
    }

    private boolean isCurrentShapeSelected(int index) {
        return isDestroyModeActive()
                ? effectiveRangeDestroyShape() == DESTROY_SHAPES[index]
                : this.buildModeShape == BUILD_SHAPES[index];
    }

    private void selectShape(int index) {
        if (isDestroyModeActive()) {
            AreaMineShape shape = DESTROY_SHAPES[index];
            if (canUseDestroyShape(shape)) {
                setRangeDestroyShape(shape);
            }
            return;
        }
        setBuildModeShape(BUILD_SHAPES[index]);
    }

    public BuildShape getBuildModeShape() {
        return this.buildModeShape;
    }

    public AreaMineShape getRangeDestroyShape() {
        return effectiveRangeDestroyShape();
    }

    public void setBuildModeShape(BuildShape shape) {
        this.buildModeShape = shape == null ? BuildShape.BLOCK : shape;
        if (isOpen() && !isDestroyModeActive()) {
            this.controller.setBuildShape(this.buildModeShape);
            screen.ensureFillModeForShape(this.buildModeShape);
            screen.clearShapeBuildSession();
            this.controller.clearAreaMineSession();
        }
        screen.persistUiState();
        rebuildFillModeButtons();
        rebuildAllShapeButtons();
    }

    public void setRangeDestroyShape(AreaMineShape shape) {
        AreaMineShape next = shape == null ? AreaMineShape.CHAIN : shape;
        if (!canUseDestroyShape(next)) {
            return;
        }
        this.rangeDestroyShape = next;
        if (isOpen() && isDestroyModeActive()) {
            applyActiveShapeToController();
            screen.clearShapeBuildSession();
            this.controller.clearAreaMineSession();
        }
        screen.persistUiState();
        rebuildFillModeButtons();
        rebuildAllShapeButtons();
    }

    public void loadStoredShapes(BuildShape storedBuildShape, AreaMineShape storedDestroyShape) {
        this.buildModeShape = storedBuildShape == null ? BuildShape.BLOCK : storedBuildShape;
        // 注意：不覆盖 rangeDestroyShape——由 area_mine_shape PersistableProperty 统一管理
        if (isOpen()) {
            applyActiveShapeToController();
        }
        rebuildFillModeButtons();
        rebuildAllShapeButtons();
    }

    public int getChainDestroyLimit() {
        return this.chainDestroyLimit;
    }

    public void setChainDestroyLimit(int limit) {
        setChainDestroyLimit(limit, true);
    }

    public void loadChainDestroyLimit(int limit) {
        setChainDestroyLimit(limit, false);
    }

    private void setChainDestroyLimit(int limit, boolean persist) {
        int clamped = sanitizeChainLimit(limit);
        if (this.chainDestroyLimit == clamped) {
            syncSliderValue();
            return;
        }
        this.chainDestroyLimit = clamped;
        syncSliderValue();
        if (persist && screen != null) {
            screen.persistUiState();
        }
    }

    private void syncSliderValue() {
        if (this.chainLimitSlider != null) {
            this.chainLimitSlider.setValue(this.chainDestroyLimit);
        }
    }

    private void ensureChainLimitSlider() {
        if (this.chainLimitSlider != null) {
            return;
        }
        int sliderW = Math.max(50, windowWidth - RIGHT_COL_X - 40);
        this.chainLimitSlider = new WindowSlider(0, 0, sliderW, 18,
                ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT, this.chainDestroyLimit);
        this.chainLimitSlider.onChange(value -> {
            this.chainDestroyLimit = value;
            if (screen != null) {
                screen.persistUiState();
            }
        });
    }

    private static int sanitizeChainLimit(int value) {
        return Mth.clamp(value, ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT);
    }

    // ===== 智能放置子模式按钮和滑条 =====

    private void createSmartPlaceModeButtons() {
        smartPlaceModeButtons = new WindowButton[2];
        for (int i = 0; i < 2; i++) {
            SmartPlaceMode mode = SmartPlaceMode.values()[i];
            smartPlaceModeButtons[i] = new WindowButton(0, 0,
                    QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT,
                    Component.translatable("screen.rtsbuilding.quick_build.smart_place_mode_" + i),
                    btn -> selectSmartPlaceMode(mode));
        }
    }

    private void selectSmartPlaceMode(SmartPlaceMode mode) {
        smartPlaceHandler.setMode(mode);
        rebuildSmartPlaceModeButtons();
        screen.clearShapeBuildSession();
        screen.persistUiState();
    }

    private void rebuildSmartPlaceModeButtons() {
        // 文本按钮无需重绘，选中状态由按钮自身样式处理
    }

    private void ensureSmartPlaceSliders() {
        if (fillCountSlider != null && detectionDiameterSlider != null) {
            return;
        }
        SmartPlaceOptions opts = smartPlaceHandler.getOptions();
        int sliderW = Math.max(80, windowWidth - RIGHT_COL_X - 40);

        fillCountSlider = new WindowSlider(0, 0, sliderW, 18,
                SMART_PLACE_MIN_FILL_COUNT, SMART_PLACE_MAX_FILL_COUNT, opts.fillCount);
        fillCountSlider.onChange(value -> {
            smartPlaceHandler.setFillCount(value);
            if (screen != null) {
                screen.persistUiState();
            }
        });

        detectionDiameterSlider = new WindowSlider(0, 0, sliderW, 18,
                SMART_PLACE_MIN_DIAMETER, SMART_PLACE_MAX_DIAMETER, opts.detectionDiameter);
        detectionDiameterSlider.onChange(value -> {
            smartPlaceHandler.setDetectionDiameter(value);
            if (screen != null) {
                screen.persistUiState();
            }
        });
    }

    /** 外部获取智能放置处理器，供 BuilderScreen 读取预览数据 */
    public SmartPlaceHandler getSmartPlaceHandler() {
        return smartPlaceHandler;
    }

    /** 当前是否处于智能放置模式 */
    public boolean isSmartPlaceActive() {
        return effectiveMode() == QuickBuildMode.SMART_PLACE;
    }

    /** 当前是否处于高级破坏模式 */
    public boolean isAdvancedDestroyActive() {
        return effectiveMode() == QuickBuildMode.ADVANCED_DESTROY;
    }

    /** 外部获取高级破坏处理器，供 BuilderScreen 读取预览数据和锚点状态 */
    public AdvancedDestroyHandler getAdvDestroyHandler() {
        return advDestroyHandler;
    }

    public AdvancedDestroyOptions getAdvDestroyOptions() {
        return advDestroyOptions;
    }

    public boolean isWorldPickMode() { return worldPickMode; }

    public void enterWorldPickMode() {
        this.worldPickMode = true;
        // 光标切换为十字
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            long cursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
            GLFW.glfwSetCursor(window, cursor);
        } catch (Exception ignored) {}
    }

    public void exitWorldPickMode() {
        this.worldPickMode = false;
        // 恢复箭头光标
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            long cursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
            GLFW.glfwSetCursor(window, cursor);
        } catch (Exception ignored) {}
    }

    // ===== 高级破坏子模式按钮和滑条 =====

    private void createAdvDestroySubModeButtons() {
        advDestroySubModeButtons = new WindowButton[4];
        for (int i = 0; i < 4; i++) {
            AdvancedDestroySubMode mode = AdvancedDestroySubMode.values()[i];
            advDestroySubModeButtons[i] = new WindowButton(0, 0,
                    QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_sub_" + i),
                    btn -> selectAdvDestroySubMode(mode));
        }
    }

    private void selectAdvDestroySubMode(AdvancedDestroySubMode mode) {
        this.advDestroySubMode = mode;
        this.advDestroyOptions.setSubMode(mode);
        this.advDestroyHandler.clearAnchor();
        if (screen != null) {
            screen.clearShapeBuildSession();
            screen.persistUiState();
        }
    }

    private void ensureAdvDestroySliders() {
        int sliderW = Math.max(40, windowWidth - RIGHT_COL_X - 60);
        int halfSliderW = Math.max(25, (windowWidth - RIGHT_COL_X - 60) / 2);

        // 矩形滑条（仅首次创建）
        if (rectSliderPlusX == null) {
            rectSliderPlusX = new WindowSlider(0, 0, halfSliderW, 16,
                    ADV_DESTROY_RECT_MIN, ADV_DESTROY_RECT_MAX, advDestroyOptions.getRectPlusX());
            rectSliderPlusX.onChange(v -> { advDestroyOptions.setRectPlusX(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
            rectSliderMinusX = new WindowSlider(0, 0, halfSliderW, 16,
                    ADV_DESTROY_RECT_MIN, ADV_DESTROY_RECT_MAX, advDestroyOptions.getRectMinusX());
            rectSliderMinusX.onChange(v -> { advDestroyOptions.setRectMinusX(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
            rectSliderPlusY = new WindowSlider(0, 0, halfSliderW, 16,
                    ADV_DESTROY_RECT_MIN, ADV_DESTROY_RECT_MAX, advDestroyOptions.getRectPlusY());
            rectSliderPlusY.onChange(v -> { advDestroyOptions.setRectPlusY(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
            rectSliderMinusY = new WindowSlider(0, 0, halfSliderW, 16,
                    ADV_DESTROY_RECT_MIN, ADV_DESTROY_RECT_MAX, advDestroyOptions.getRectMinusY());
            rectSliderMinusY.onChange(v -> { advDestroyOptions.setRectMinusY(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
            rectSliderPlusZ = new WindowSlider(0, 0, halfSliderW, 16,
                    ADV_DESTROY_RECT_MIN, ADV_DESTROY_RECT_MAX, advDestroyOptions.getRectPlusZ());
            rectSliderPlusZ.onChange(v -> { advDestroyOptions.setRectPlusZ(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
            rectSliderMinusZ = new WindowSlider(0, 0, halfSliderW, 16,
                    ADV_DESTROY_RECT_MIN, ADV_DESTROY_RECT_MAX, advDestroyOptions.getRectMinusZ());
            rectSliderMinusZ.onChange(v -> { advDestroyOptions.setRectMinusZ(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
        }
        // 矩形填充模式按钮
        if (rectFillModeButtons == null) {
            rectFillModeButtons = new WindowButton[3];
            String[] rectFillLabels = {"screen.rtsbuilding.fill.fill", "screen.rtsbuilding.fill.hollow", "screen.rtsbuilding.fill.skeleton"};
            for (int i = 0; i < 3; i++) {
                ShapeFillMode fm = ShapeFillMode.values()[i];
                rectFillModeButtons[i] = new WindowButton(0, 0, 50, 16,
                        Component.translatable(rectFillLabels[i]), btn -> {
                    advDestroyOptions.setRectFillMode(fm);
                    advDestroyHandler.markDirty();
                    if (screen != null) screen.persistUiState();
                });
            }
        }
        // 圆柱滑条
        if (cylinderRadiusSlider == null) {
            cylinderRadiusSlider = new WindowSlider(0, 0, sliderW, 16,
                    ADV_DESTROY_CYLINDER_RADIUS_MIN, ADV_DESTROY_CYLINDER_RADIUS_MAX,
                    advDestroyOptions.getCylinderRadius());
            cylinderRadiusSlider.onChange(v -> { advDestroyOptions.setCylinderRadius(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
            cylinderPlusHSlider = new WindowSlider(0, 0, halfSliderW, 16,
                    ADV_DESTROY_CYLINDER_HEIGHT_MIN, ADV_DESTROY_CYLINDER_HEIGHT_MAX,
                    advDestroyOptions.getCylinderPlusH());
            cylinderPlusHSlider.onChange(v -> { advDestroyOptions.setCylinderPlusH(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
            cylinderMinusHSlider = new WindowSlider(0, 0, halfSliderW, 16,
                    ADV_DESTROY_CYLINDER_HEIGHT_MIN, ADV_DESTROY_CYLINDER_HEIGHT_MAX,
                    advDestroyOptions.getCylinderMinusH());
            cylinderMinusHSlider.onChange(v -> { advDestroyOptions.setCylinderMinusH(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
        }
        // 圆柱填充模式按钮
        if (cylinderFillModeButtons == null) {
            cylinderFillModeButtons = new WindowButton[2];
            String[] cylFillLabels = {"screen.rtsbuilding.fill.fill", "screen.rtsbuilding.fill.hollow"};
            for (int i = 0; i < 2; i++) {
                ShapeFillMode fm = ShapeFillMode.values()[i]; // FILL, HOLLOW
                cylinderFillModeButtons[i] = new WindowButton(0, 0, 50, 16,
                        Component.translatable(cylFillLabels[i]), btn -> {
                    advDestroyOptions.setCylinderFillMode(fm);
                    advDestroyHandler.markDirty();
                    if (screen != null) screen.persistUiState();
                });
            }
        }
        // 楼梯控件
        if (stairsCountSlider == null) {
            stairsCountSlider = new WindowSlider(0, 0, sliderW, 16,
                    ADV_DESTROY_STAIRS_COUNT_MIN, ADV_DESTROY_STAIRS_COUNT_MAX,
                    advDestroyOptions.getStairsCount());
            stairsCountSlider.onChange(v -> { advDestroyOptions.setStairsCount(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
        }
        if (stairsRotateButton == null) {
            stairsRotateButton = new WindowButton(0, 0, 50, 18,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_stairs_rotate"), btn -> {
                advDestroyOptions.setStairsRotation(advDestroyOptions.getStairsRotation() + 90);
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }
        if (stairsSymmetricButton == null) {
            stairsSymmetricButton = new WindowButton(0, 0, 50, 18,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_stairs_symmetric"), btn -> {
                advDestroyOptions.setStairsSymmetric(!advDestroyOptions.isStairsSymmetric());
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }
        // 伐木控件
        if (lumberLimitSlider == null) {
            lumberLimitSlider = new WindowSlider(0, 0, sliderW, 16,
                    ADV_DESTROY_LUMBER_LIMIT_MIN, ADV_DESTROY_LUMBER_LIMIT_MAX,
                    advDestroyOptions.getLumberLimit());
            lumberLimitSlider.onChange(v -> { advDestroyOptions.setLumberLimit(v); advDestroyHandler.markDirty(); if (screen != null) screen.persistUiState(); });
        }
        if (lumberStrongManToggle == null) {
            lumberStrongManToggle = new WindowButton(0, 0, 80, 18,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_lumber_strong_man"), btn -> {
                advDestroyOptions.setLumberStrongMan(!advDestroyOptions.isLumberStrongMan());
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }
        if (lumberAllowPlayerBlocksToggle == null) {
            lumberAllowPlayerBlocksToggle = new WindowButton(0, 0, 80, 18,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_lumber_allow_player"), btn -> {
                advDestroyOptions.setLumberAllowPlayerBlocks(!advDestroyOptions.isLumberAllowPlayerBlocks());
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }

        // 尺寸/区块切换按钮
        if (rectSizeModeBtn == null) {
            rectSizeModeBtn = new WindowButton(0, 0, 30, 14,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_rect_size"), btn -> {
                advDestroyOptions.setRectMode(AdvDestroyRectMode.SIZE);
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }
        if (rectChunkModeBtn == null) {
            rectChunkModeBtn = new WindowButton(0, 0, 30, 14,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_rect_chunk"), btn -> {
                advDestroyOptions.setRectMode(AdvDestroyRectMode.CHUNK);
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }

        // 区块模式 Y 滑条（动态范围，每次渲染时设置值）
        if (chunkRectSliderPlusY == null) {
            chunkRectSliderPlusY = new WindowSlider(0, 0, halfSliderW, 16,
                    0, 320, advDestroyOptions.getChunkRectPlusY());
            chunkRectSliderPlusY.onChange(v -> {
                advDestroyOptions.setChunkRectPlusY(v, 320);
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }
        if (chunkRectSliderMinusY == null) {
            chunkRectSliderMinusY = new WindowSlider(0, 0, halfSliderW, 16,
                    0, 320, advDestroyOptions.getChunkRectMinusY());
            chunkRectSliderMinusY.onChange(v -> {
                advDestroyOptions.setChunkRectMinusY(v, 320);
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }

        // 过滤控件
        if (filterSlotBtn == null) {
            filterSlotBtn = new WindowButton(0, 0, 20, 20,
                    Component.literal("▣"), btn -> {
                var filters = advDestroyOptions.getFilters();
                if (controller != null && controller.hasSelectedItem() && !controller.getSelectedItemId().isBlank()) {
                    // 从存储面板选中方块 → 设置/替换快速过滤
                    ResourceLocation itemId = ResourceLocation.tryParse(controller.getSelectedItemId());
                    if (itemId != null) {
                        filters.removeIf(f -> f.getType() == AdvDestroyFilter.FilterType.ITEM_STACK);
                        filters.addFirst(new AdvDestroyItemStackFilter(itemId));
                    }
                } else if (!filters.isEmpty()) {
                    // 有过滤且空手点击 → 清除过滤
                    filters.removeIf(f -> f.getType() == AdvDestroyFilter.FilterType.ITEM_STACK);
                } else {
                    // 无过滤且无选中方块 → 进入世界取块模式
                    enterWorldPickMode();
                    return;
                }
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }
        if (filterPanelBtn == null) {
            filterPanelBtn = new WindowButton(0, 0, 30, 14,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_filter"), btn -> {
                filterPanel.toggle(this.windowX, this.windowY, this.windowWidth, this.windowHeight);
            });
        }
        if (filterInverseBtn == null) {
            filterInverseBtn = new WindowButton(0, 0, 30, 14,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_filter_inverse"), btn -> {
                advDestroyOptions.setFilterInverse(!advDestroyOptions.isFilterInverse());
                advDestroyHandler.markDirty();
                if (screen != null) screen.persistUiState();
            });
        }
    }

    // ======================== 渲染 ========================

    /**
     * 动态调整窗口高度：底部信息显示时增加 {@value #BOTTOM_INFO_H}px。
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.windowHeight = currentBasePanelHeight() + BOTTOM_INFO_H;
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open || !canShowWindow()) return;
        renderShapeTooltip(g, mouseX, mouseY);
    }

    private void renderShapeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < shapeButtons.length; i++) {
            WindowButton btn = shapeButtons[i];
            if (mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth()
                    && mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                g.renderTooltip(screen.font(), Component.translatable(currentShapeTooltipKey(i)), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderModeToggles(GuiGraphics g, int mouseX, int mouseY) {
        int bodyY = contentY();
        int totalW = this.windowWidth - 16;
        int buttonW = (totalW - MODE_TOGGLE_GAP) / 2;
        int col1X = this.windowX + 8;
        int col2X = col1X + buttonW + MODE_TOGGLE_GAP;
        int row1Y = bodyY + MODE_ROW_TOP;
        int row2Y = bodyY + MODE_ROW_2_TOP;

        // 第 1 行：范围放置 | 范围破坏
        renderModeToggle(g, col1X, row1Y, buttonW, QuickBuildMode.BUILD,
                Component.translatable("screen.rtsbuilding.quick_build.mode_build"), mouseX, mouseY);
        renderModeToggle(g, col2X, row1Y, buttonW, QuickBuildMode.DESTROY,
                Component.translatable("screen.rtsbuilding.quick_build.mode_destroy"), mouseX, mouseY);

        // 第 2 行：智能放置 | 高级破坏
        renderModeToggle(g, col1X, row2Y, buttonW, QuickBuildMode.SMART_PLACE,
                Component.translatable("screen.rtsbuilding.quick_build.mode_smart_place"), mouseX, mouseY);
        renderModeToggle(g, col2X, row2Y, buttonW, QuickBuildMode.ADVANCED_DESTROY,
                Component.translatable("screen.rtsbuilding.quick_build.mode_advanced_destroy"), mouseX, mouseY);
    }

    private void renderModeToggle(GuiGraphics g, int x, int y, int w, QuickBuildMode mode,
            Component label, int mouseX, int mouseY) {
        boolean enabled = (mode != QuickBuildMode.DESTROY || canUseRangeDestroy())
                && (mode != QuickBuildMode.ADVANCED_DESTROY || canUseAdvancedDestroy());
        boolean active = this.quickBuildMode == mode && enabled;
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + MODE_TOGGLE_H;
        int border = !enabled ? 0xFF3A4652 : (active ? 0xFF5FE36C : (hovered ? 0xFF7B91A6 : 0xFF647B92));
        int bg = !enabled ? 0xFF111720 : (active ? 0xFF29583E : (hovered ? 0xFF223040 : 0xFF141C26));
        g.fill(x, y, x + w, y + MODE_TOGGLE_H, border);
        g.fill(x + 1, y + 1, x + w - 1, y + MODE_TOGGLE_H - 1, bg);
        int labelX = x + Math.max(2, (w - screen.font().width(label)) / 2);
        int labelY = y + (MODE_TOGGLE_H - screen.font().lineHeight) / 2;
        int color = !enabled ? 0xFF7B8794 : (active ? 0xFFD8FFE0 : 0xFFD8E3EE);
        g.drawString(screen.font(), label, labelX, labelY, color, false);
    }

    private void renderProgressStrip(GuiGraphics g, int x, int dividerY) {
        int barX = x + 8;
        int barY = dividerY + 4;
        int barW = this.windowWidth - 16;
        int barH = 4;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF0B1118);
        RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
        int processed = workflow != null ? workflow.completedBlocks() : -1;
        int total = workflow != null ? workflow.totalBlocks() : 0;
        if (processed >= 0 && total > 0) {
            int filled = Math.min(barW, Math.round(barW * (processed / (float) total)));
            g.fill(barX, barY, barX + filled, barY + barH, 0xFFFF8EAD);
        } else {
            g.fill(barX, barY, barX + 1, barY + barH, 0xFF5F6F7F);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.windowX;
        int y = this.windowY;
        int bodyY = contentY();
        renderModeToggles(g, mouseX, mouseY);

        // --- 智能放置模式专属内容 ---
        if (isSmartPlaceActive()) {
            renderSmartPlaceContent(g, mouseX, mouseY, partialTick);
            renderSmartPlaceBottomInfo(g, x, y, bodyY);
            return;
        }

        // --- 高级破坏模式专属内容 ---
        if (isAdvancedDestroyActive()) {
            renderAdvancedDestroyContent(g, mouseX, mouseY, partialTick);
            renderAdvancedDestroyBottomInfo(g, x, y, bodyY);
            return;
        }

        int shapeTitleY = bodyY + SECTION_TOP;

        // --- 形状模式 ---
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.shape"),
                x + 10, shapeTitleY, 0xD8E3EE, false);

        // --- 形状按钮 ---
        for (int i = 0; i < shapeButtons.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = x + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = bodyY + SECTION_TOP + 15 + (row * SHAPE_ROW_PITCH);
            shapeButtons[i].setX(slotX);
            shapeButtons[i].setY(slotY);
            shapeButtons[i].active = !isDestroyModeActive() || canUseDestroyShape(DESTROY_SHAPES[i]);
            if (isDestroyModeActive() && DESTROY_SHAPES[i] == AreaMineShape.CHAIN
                    && isRangeDestroyChainMode()) {
                g.fill(slotX, slotY, slotX + QUICK_BUILD_SHAPE_SLOT, slotY + QUICK_BUILD_SHAPE_SLOT, 0xFF78B28C);
                g.fill(slotX + 2, slotY + 2, slotX + QUICK_BUILD_SHAPE_SLOT - 2,
                        slotY + QUICK_BUILD_SHAPE_SLOT - 2, 0xFF163222);
            }
            shapeButtons[i].render(g, mouseX, mouseY, partialTick);
        }

        // --- 填充模式 ---
        int rightX = x + RIGHT_COL_X;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.fill"),
                rightX, shapeTitleY, 0xD8E3EE, false);

        if (isRangeDestroyChainMode()) {
            ensureChainLimitSlider();
            int labelY = bodyY + SECTION_TOP + 17;
            g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.chain_limit_label"),
                    rightX, labelY, 0xFFD8E3EE, false);
            int sliderW = Math.max(50, windowWidth - RIGHT_COL_X - 40);
            this.chainLimitSlider.setWidth(sliderW);
            this.chainLimitSlider.setX(rightX);
            this.chainLimitSlider.setY(labelY + 14);
            this.chainLimitSlider.render(g, mouseX, mouseY, partialTick);
            // 显示当前值
            String valueStr = Integer.toString(this.chainDestroyLimit);
            g.drawString(screen.font(), valueStr, rightX + sliderW + 6, labelY + 16, 0xFFEAF4FF, false);
        } else if (fillModeButtons == null || controller.getBuildShape() != lastFillShape
                || (isDestroyModeActive() && this.rangeDestroyShape != this.lastAreaMineShape)) {
            rebuildFillModeButtons();
        }
        List<ShapeFillMode> modes =
                ShapeGeometryUtil.availableFillModes(controller.getBuildShape());
        for (int i = 0; fillModeButtons != null && i < fillModeButtons.length; i++) {
            int rowY = bodyY + SECTION_TOP + 15 + (i * 38); // 垂直居中对齐对应行的形状按钮
            fillModeButtons[i].setX(rightX);
            fillModeButtons[i].setY(rowY);
            fillModeButtons[i].render(g, mouseX, mouseY, partialTick);

            BuildShape advancedShape = activeAdvancedShape();
            int verticalIndex = verticalButtonIndex(modes);
            int advancedIndex = advancedButtonIndex(modes);
            boolean verticalButton = i == verticalIndex;
            boolean advancedButton = supportsAdvancedShape(advancedShape)
                    && i == advancedIndex;
            boolean selected = verticalButton
                    ? isRoundShapeVertical(advancedShape)
                    : advancedButton
                    ? isAdvancedShape(advancedShape)
                    : i < modes.size() && (isDestroyModeActive()
                            ? screen.getShapeController().getDestroyShapeFillMode()
                            : screen.getShapeController().getBuildShapeFillMode()) == modes.get(i);
            boolean hovered = fillModeButtons[i].isHoveredOrFocused();
            int vOffset = selected ? MODE_BUTTON_STATE_H * 2 : (hovered ? MODE_BUTTON_STATE_H : 0);
            RtsTextureRenderer.drawTextureHighPrecision(
                    g, SELECTION_DOT_TEXTURE,
                    rightX + 2, rowY + 2, 16, 16,
                    0, vOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                    MODE_BUTTON_SHEET_W, MODE_BUTTON_H,
                    0, 0xFFFFFFFF
            );
        }

        // --- 连接模式按钮（LINE/WALL 形状时在填充模式下方显示） ---
        if (this.connectToggle != null) {
            int connectRowY = bodyY + SECTION_TOP + 15
                    + ((fillModeButtons == null ? modes.size() : fillModeButtons.length) * 38);
            this.connectToggle.setX(rightX);
            this.connectToggle.setY(connectRowY);
            this.connectToggle.render(g, mouseX, mouseY, partialTick);

            boolean connected = isDestroyModeActive()
                    ? screen.getShapeController().isDestroyLineConnected()
                    : screen.getShapeController().isBuildLineConnected();
            boolean hovered = this.connectToggle.isHoveredOrFocused();
            int vOffset = connected ? MODE_BUTTON_STATE_H * 2 : (hovered ? MODE_BUTTON_STATE_H : 0);
            RtsTextureRenderer.drawTextureHighPrecision(
                    g, SELECTION_DOT_TEXTURE,
                    rightX + 2, connectRowY + 2, 16, 16,
                    0, vOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                    MODE_BUTTON_SHEET_W, MODE_BUTTON_H,
                    0, 0xFFFFFFFF
            );
        }

        // --- 底部提示文字（仅在选中物品时显示，使用面板扩展区域） ---
        {
            // 分界线
            int dividerY = y + currentBasePanelHeight();
            g.fill(x + 6, dividerY - 1, x + windowWidth - 6, dividerY, 0xFF647B92);
            renderProgressStrip(g, x, dividerY);

            // 扩展区域中心线
            int textY = dividerY + 12;
            int itemY = textY - 4;

            if (effectiveMode() == QuickBuildMode.DESTROY) {
                RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
                if (workflow != null) {
                    String fullText = workflow.progressText() + "    "
                            + screen.text("screen.rtsbuilding.quick_build.destroy_remaining", workflow.remainingBlocks());
                    g.drawString(screen.font(), fullText, x + 8, textY, 0xFFB8FFB8, false);
                    renderDimensionInfo(g, x + 8, textY + screen.font().lineHeight + 4, this.windowWidth - 16);
                } else {
                    String hintKey = isRangeDestroyChainMode()
                            ? "screen.rtsbuilding.quick_build.chain_hint"
                            : isAdvancedShapeMode()
                                    ? "screen.rtsbuilding.quick_build.destroy_advanced_box_hint"
                                    : "screen.rtsbuilding.quick_build.destroy_hint";
                    int nextY = renderBottomInfoText(g, Component.translatable(hintKey, confirmKeyLabel(true)),
                            x + 8, textY, this.windowWidth - 16, 0xFFB8B8);
                    renderDimensionInfo(g, x + 8, nextY + 3, this.windowWidth - 16);
                }
                return;
            }

            String costText = "x " + screen.currentShapeCostText();
            int textWidth = screen.font().width(costText);
            g.drawString(screen.font(), costText, x + 8, textY, 0xB8FFB8, false);

            // 渲染所选方块的物品图标，同时记录右侧边界
            ItemStack preview = resolveShapeBuildItem();
            int rightEdge = x + 8 + textWidth;
            if (!preview.isEmpty()) {
                int itemX = x + 8 + textWidth + 4;
                g.renderItem(preview, itemX, itemY);
                // 立即 flush 物品渲染，确保在 scissor 仍生效时提交到帧缓冲区
                g.flush();
                rightEdge = itemX + 16;
            }

            // 仓库库存检查：缺少数量，紧靠右侧（创造模式下跳过）
            boolean isCreative = screen.getMinecraft().player != null && screen.getMinecraft().player.isCreative();
            if (!isCreative) {
                String selectedId = controller.getSelectedItemId();
                if (!selectedId.isBlank()) {
                    try {
                        long needed = Long.parseLong(screen.currentShapeCostText());
                        long available = controller.getStorageTotalCount(selectedId);
                        long missing = needed - available;
                        if (missing > 0) {
                            String missText = screen.text("screen.rtsbuilding.quick_build.missing_blocks", missing);
                            int missTextX = rightEdge + 8;
                            g.drawString(screen.font(), missText, missTextX, textY, 0xFFB8B8, false);

                            if (!preview.isEmpty()) {
                                int missIconX = missTextX + screen.font().width(missText) + 4;
                                g.renderItem(preview, missIconX, itemY);
                                g.flush();
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            int nextY = renderBottomInfoText(g,
                    Component.translatable("screen.rtsbuilding.quick_build.build_hint"),
                    x + 8,
                    textY + screen.font().lineHeight + 3,
                    this.windowWidth - 16,
                    0xFFD8E8FF);
            renderDimensionInfo(g, x + 8, nextY + 3, this.windowWidth - 16);
        }
    }

    private int renderBottomInfoText(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = screen.font().split(text, Math.max(1, maxWidth));
        int lineCount = Math.min(BOTTOM_TEXT_MAX_LINES, lines.size());
        for (int i = 0; i < lineCount; i++) {
            g.drawString(screen.font(), lines.get(i), x, y + i * screen.font().lineHeight, color, false);
        }
        return y + lineCount * screen.font().lineHeight;
    }

    private void renderDimensionInfo(GuiGraphics g, int x, int y, int maxWidth) {
        Component text = Component.translatable(
                "screen.rtsbuilding.quick_build.dimensions",
                screen.currentShapeSizeText());
        String trimmed = screen.font().plainSubstrByWidth(text.getString(), Math.max(1, maxWidth));
        g.drawString(screen.font(), trimmed, x, y, 0xFFC9D8E8, false);
    }

    private String confirmKeyLabel(boolean destroyMode) {
        return (destroyMode ? ClientKeyMappings.CONFIRM_BATCH_DESTROY : ClientKeyMappings.CONFIRM_BATCH_PLACE)
                .getTranslatedKeyMessage()
                .getString();
    }

    // ===== 智能放置渲染 =====

    private void renderSmartPlaceContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bodyY = contentY();
        int shapeTitleY = bodyY + SECTION_TOP;
        int rightX = this.windowX + RIGHT_COL_X;

        // 左侧：子模式按钮
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.smart_place_mode"),
                this.windowX + 10, shapeTitleY, 0xD8E3EE, false);
        for (int i = 0; i < 2; i++) {
            int col = i % 2;
            int slotX = this.windowX + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = bodyY + SECTION_TOP + 15 + (i / 2 * SHAPE_ROW_PITCH);
            smartPlaceModeButtons[i].setX(slotX);
            smartPlaceModeButtons[i].setY(slotY);
            smartPlaceModeButtons[i].render(g, mouseX, mouseY, partialTick);
        }

        // 右侧：滑条
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.smart_place_fill_count"),
                rightX, shapeTitleY, 0xD8E3EE, false);

        int sliderW = Math.max(80, windowWidth - RIGHT_COL_X - 40);
        fillCountSlider.setWidth(sliderW);
        fillCountSlider.setX(rightX);
        fillCountSlider.setY(shapeTitleY + 16);
        fillCountSlider.render(g, mouseX, mouseY, partialTick);
        String countStr = Integer.toString(smartPlaceHandler.getFillCount());
        g.drawString(screen.font(), countStr, rightX + sliderW + 6, shapeTitleY + 20, 0xFFEAF4FF, false);

        detectionDiameterSlider.setWidth(sliderW);
        detectionDiameterSlider.setX(rightX);
        detectionDiameterSlider.setY(shapeTitleY + 16 + 38);
        detectionDiameterSlider.render(g, mouseX, mouseY, partialTick);
        String diamStr = Integer.toString(smartPlaceHandler.getDetectionDiameter());
        g.drawString(screen.font(), diamStr, rightX + sliderW + 6, shapeTitleY + 58, 0xFFEAF4FF, false);
    }

    private void renderSmartPlaceBottomInfo(GuiGraphics g, int x, int y, int bodyY) {
        // 分界线
        int dividerY = y + currentBasePanelHeight();
        g.fill(x + 6, dividerY - 1, x + windowWidth - 6, dividerY, 0xFF647B92);

        int textY = dividerY + 12;

        // 更新鼠标指向位置，供追踪扫描使用
        var hit = screen.pickBlockHit();
        if (hit != null) {
            smartPlaceHandler.setCursorTarget(hit.getBlockPos());
        }
        smartPlaceHandler.tick(screen.getMinecraft());
        boolean hasResult = smartPlaceHandler.hasValidResult();
        boolean isAnchored = smartPlaceHandler.isAnchored();

        if (hasResult) {
            String scanText = smartPlaceHandler.reachedVolumeLimit()
                    ? "screen.rtsbuilding.quick_build.smart_place_scan_overflow"
                    : "screen.rtsbuilding.quick_build.smart_place_scanned";
            String formatted = screen.text(scanText, smartPlaceHandler.getScanCount(),
                    smartPlaceHandler.getFillCount());
            g.drawString(screen.font(), formatted, x + 8, textY, 0xFFB8FFB8, false);

            // 包围盒尺寸提示
            if (smartPlaceHandler.getBoundingBox() != null) {
                var box = smartPlaceHandler.getBoundingBox();
                String dimText = screen.text("screen.rtsbuilding.quick_build.smart_place_bounds",
                        (int)(box.maxX - box.minX), (int)(box.maxY - box.minY), (int)(box.maxZ - box.minZ));
                g.drawString(screen.font(), dimText, x + 8, textY + screen.font().lineHeight + 3, 0xFFC9D8E8, false);
            }

            // 锚定状态额外提示
            if (isAnchored) {
                g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.smart_place_anchored"),
                        x + 8, textY + screen.font().lineHeight * 2 + 6, 0xFFFFD700, false);
            }
        } else {
            g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.smart_place_hint"),
                    x + 8, textY, 0xFFB8B8, false);
        }
    }

    // ===== 高级破坏渲染 =====

    private static final int ADV_SLIDER_ROW_H = 24;

    private void renderAdvancedDestroyContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bodyY = contentY();
        int shapeTitleY = bodyY + SECTION_TOP;
        int rightX = this.windowX + RIGHT_COL_X;

        // 左侧：子模式按钮
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.adv_sub_mode_label"),
                this.windowX + 10, shapeTitleY, 0xD8E3EE, false);
        for (int i = 0; i < advDestroySubModeButtons.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = this.windowX + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = bodyY + SECTION_TOP + 15 + (row * SHAPE_ROW_PITCH);
            advDestroySubModeButtons[i].setX(slotX);
            advDestroySubModeButtons[i].setY(slotY);
            // 子模式选中高亮
            boolean selected = advDestroyOptions.getSubMode() == AdvancedDestroySubMode.values()[i];
            if (selected) {
                g.fill(slotX, slotY, slotX + QUICK_BUILD_SHAPE_SLOT, slotY + QUICK_BUILD_SHAPE_SLOT, 0xFF5FE36C);
                g.fill(slotX + 2, slotY + 2, slotX + QUICK_BUILD_SHAPE_SLOT - 2,
                        slotY + QUICK_BUILD_SHAPE_SLOT - 2, 0xFF29583E);
            }
            advDestroySubModeButtons[i].render(g, mouseX, mouseY, partialTick);
        }

        // 右侧：根据子模式渲染滑条
        int sliderW = Math.max(40, windowWidth - RIGHT_COL_X - 60);
        int halfSliderW = Math.max(25, (windowWidth - RIGHT_COL_X - 60) / 2);

        switch (advDestroyOptions.getSubMode()) {
            case RECTANGLE -> {
                boolean isChunk = advDestroyOptions.getRectMode() == AdvDestroyRectMode.CHUNK;

                // 尺寸/区块切换按钮
                int toggleY = shapeTitleY;
                rectSizeModeBtn.setX(rightX);
                rectSizeModeBtn.setY(toggleY);
                boolean sizeSel = !isChunk;
                if (sizeSel) {
                    g.fill(rightX, toggleY, rightX + 30, toggleY + 14, 0xFF5FE36C);
                }
                rectSizeModeBtn.render(g, mouseX, mouseY, partialTick);

                rectChunkModeBtn.setX(rightX + 32);
                rectChunkModeBtn.setY(toggleY);
                boolean chunkSel = isChunk;
                if (chunkSel) {
                    g.fill(rightX + 32, toggleY, rightX + 62, toggleY + 14, 0xFF5FE36C);
                }
                rectChunkModeBtn.render(g, mouseX, mouseY, partialTick);

                if (!isChunk) {
                    // ===== 尺寸模式：6条滑条 =====
                    String[][] axisLabels = {
                        {"screen.rtsbuilding.quick_build.adv_rect_px", "screen.rtsbuilding.quick_build.adv_rect_nx"},
                        {"screen.rtsbuilding.quick_build.adv_rect_py", "screen.rtsbuilding.quick_build.adv_rect_ny"},
                        {"screen.rtsbuilding.quick_build.adv_rect_pz", "screen.rtsbuilding.quick_build.adv_rect_nz"}
                    };
                    WindowSlider[][] rectPairs = {
                        {rectSliderPlusX, rectSliderMinusX},
                        {rectSliderPlusY, rectSliderMinusY},
                        {rectSliderPlusZ, rectSliderMinusZ}
                    };
                    for (int row = 0; row < 3; row++) {
                        int rowY = bodyY + SECTION_TOP + 18 + row * ADV_SLIDER_ROW_H;
                        g.drawString(screen.font(), Component.translatable(axisLabels[row][0]),
                                rightX, rowY + 2, 0xFFC9D8E8, false);
                        rectPairs[row][0].setWidth(halfSliderW);
                        rectPairs[row][0].setX(rightX + 18);
                        rectPairs[row][0].setY(rowY);
                        rectPairs[row][0].render(g, mouseX, mouseY, partialTick);

                        g.drawString(screen.font(), Component.translatable(axisLabels[row][1]),
                                rightX + 18 + halfSliderW + 4, rowY + 2, 0xFFC9D8E8, false);
                        rectPairs[row][1].setWidth(halfSliderW);
                        rectPairs[row][1].setX(rightX + 18 + halfSliderW + 16);
                        rectPairs[row][1].setY(rowY);
                        rectPairs[row][1].render(g, mouseX, mouseY, partialTick);
                    }
                } else {
                    // ===== 区块模式：XZ灰化 + Y独立滑条 =====
                    // 显示 X/Z 固化为 "区块" 文字
                    int infoY1 = bodyY + SECTION_TOP + 18;
                    g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.adv_chunk_xz_locked"),
                            rightX, infoY1 + 2, 0xFF6B7D8E, false);

                    // 动态更新 Y 滑条范围和默认值
                    var handler = advDestroyHandler;
                    BlockPos anchor = handler.isAnchored() ? handler.getAnchorPos() : handler.getCursorTarget();
                    int anchorY = anchor != null ? anchor.getY() : 0;
                    int[] yMax = advDestroyOptions.computeChunkYMax(anchorY);
                    chunkRectSliderPlusY.setRange(ADV_DESTROY_CHUNK_Y_MIN, yMax[0]);
                    chunkRectSliderMinusY.setRange(ADV_DESTROY_CHUNK_Y_MIN, yMax[1]);
                    // 首次进入区块模式时设默认值
                    if (isChunk && advDestroyOptions.getChunkRectPlusY() == 0 && yMax[0] > 0) {
                        advDestroyOptions.setChunkRectPlusY(yMax[0], yMax[0]);
                    }
                    if (isChunk && advDestroyOptions.getChunkRectMinusY() == 0 && yMax[1] > 0) {
                        advDestroyOptions.setChunkRectMinusY(yMax[1], yMax[1]);
                    }

                    int yRowY = bodyY + SECTION_TOP + 18 + ADV_SLIDER_ROW_H;
                    g.drawString(screen.font(), "+y", rightX, yRowY + 2, 0xFFC9D8E8, false);
                    chunkRectSliderPlusY.setWidth(halfSliderW);
                    chunkRectSliderPlusY.setX(rightX + 18);
                    chunkRectSliderPlusY.setY(yRowY);
                    chunkRectSliderPlusY.setValue(advDestroyOptions.getChunkRectPlusY());
                    chunkRectSliderPlusY.render(g, mouseX, mouseY, partialTick);
                    String pyStr = "+" + advDestroyOptions.getChunkRectPlusY();
                    g.drawString(screen.font(), pyStr, rightX + 18 + halfSliderW + 4, yRowY + 2, 0xFFEAF4FF, false);

                    g.drawString(screen.font(), "-y", rightX, yRowY + ADV_SLIDER_ROW_H - 2, 0xFFC9D8E8, false);
                    chunkRectSliderMinusY.setWidth(halfSliderW);
                    chunkRectSliderMinusY.setX(rightX + 18);
                    chunkRectSliderMinusY.setY(yRowY + ADV_SLIDER_ROW_H - 4);
                    chunkRectSliderMinusY.setValue(advDestroyOptions.getChunkRectMinusY());
                    chunkRectSliderMinusY.render(g, mouseX, mouseY, partialTick);
                    String nyStr = "-" + advDestroyOptions.getChunkRectMinusY();
                    g.drawString(screen.font(), nyStr, rightX + 18 + halfSliderW + 4, yRowY + ADV_SLIDER_ROW_H - 2, 0xFFEAF4FF, false);
                }

                // 矩形填充模式按钮（在滑条下方）
                int fillY = bodyY + SECTION_TOP + 18 + 3 * ADV_SLIDER_ROW_H + 8;
                ShapeFillMode activeRectFill = advDestroyOptions.getRectFillMode();
                for (int i = 0; i < rectFillModeButtons.length; i++) {
                    ShapeFillMode fm = ShapeFillMode.values()[i];
                    int bx = rightX + i * 54;
                    rectFillModeButtons[i].setX(bx);
                    rectFillModeButtons[i].setY(fillY);
                    boolean sel = activeRectFill == fm;
                    boolean hovered = rectFillModeButtons[i].isHoveredOrFocused();
                    int vOffset = sel ? MODE_BUTTON_STATE_H * 2 : (hovered ? MODE_BUTTON_STATE_H : 0);
                    RtsTextureRenderer.drawTextureHighPrecision(
                            g, SELECTION_DOT_TEXTURE, bx + 2, fillY + 2, 14, 14,
                            0, vOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                            MODE_BUTTON_SHEET_W, MODE_BUTTON_H, 0, 0xFFFFFFFF);
                    rectFillModeButtons[i].render(g, mouseX, mouseY, partialTick);
                }

                // ===== 过滤行（三行，在填充模式按钮下方） =====
                int filterBaseY = fillY + 20;

                // 行1："挖掘方块：[槽位]"
                g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.adv_filter_slot_label"),
                        rightX, filterBaseY + 3, 0xFFC9D8E8, false);
                filterSlotBtn.setX(rightX + 54);
                filterSlotBtn.setY(filterBaseY);
                filterSlotBtn.render(g, mouseX, mouseY, partialTick);
                // 渲染当前过滤方块图标
                var filters = advDestroyOptions.getFilters();
                if (!filters.isEmpty() && filters.getFirst().getType() == AdvDestroyFilter.FilterType.ITEM_STACK) {
                    var itemFilter = (AdvDestroyItemStackFilter) filters.getFirst();
                    var item = BuiltInRegistries.ITEM.getOptional(itemFilter.getItemId());
                    if (item.isPresent()) {
                        g.renderFakeItem(new ItemStack(item.get()), rightX + 56, filterBaseY + 2);
                    }
                }

                // 行2：过滤按钮
                filterPanelBtn.setX(rightX);
                filterPanelBtn.setY(filterBaseY + ADV_DESTROY_FILTER_ROW_H);
                filterPanelBtn.render(g, mouseX, mouseY, partialTick);

                // 行3：反选开关
                filterInverseBtn.setX(rightX);
                filterInverseBtn.setY(filterBaseY + 2 * ADV_DESTROY_FILTER_ROW_H);
                filterInverseBtn.render(g, mouseX, mouseY, partialTick);
                boolean inv = advDestroyOptions.isFilterInverse();
                boolean invHov = filterInverseBtn.isHoveredOrFocused();
                int invOff = inv ? MODE_BUTTON_STATE_H * 2 : (invHov ? MODE_BUTTON_STATE_H : 0);
                RtsTextureRenderer.drawTextureHighPrecision(
                        g, SELECTION_DOT_TEXTURE, rightX + 32, filterBaseY + 2 * ADV_DESTROY_FILTER_ROW_H + 1, 14, 14,
                        0, invOff, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                        MODE_BUTTON_SHEET_W, MODE_BUTTON_H, 0, 0xFFFFFFFF);

                // 渲染过滤面板（如果可见）
                filterPanel.render(g, mouseX, mouseY, partialTick, filters);
            }
            case CYLINDER -> {
                g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.adv_cyl_radius"),
                        rightX, shapeTitleY, 0xD8E3EE, false);
                cylinderRadiusSlider.setWidth(sliderW);
                cylinderRadiusSlider.setX(rightX);
                cylinderRadiusSlider.setY(shapeTitleY + 16);
                cylinderRadiusSlider.render(g, mouseX, mouseY, partialTick);
                String rStr = Integer.toString(advDestroyOptions.getCylinderRadius());
                g.drawString(screen.font(), rStr, rightX + sliderW + 6, shapeTitleY + 20, 0xFFEAF4FF, false);

                // 高度滑条对 +h/-h
                int hRowY = shapeTitleY + 16 + ADV_SLIDER_ROW_H;
                g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.adv_cyl_ph"),
                        rightX, hRowY + 2, 0xFFC9D8E8, false);
                cylinderPlusHSlider.setWidth(halfSliderW);
                cylinderPlusHSlider.setX(rightX + 18);
                cylinderPlusHSlider.setY(hRowY);
                cylinderPlusHSlider.render(g, mouseX, mouseY, partialTick);

                g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.adv_cyl_nh"),
                        rightX + 18 + halfSliderW + 4, hRowY + 2, 0xFFC9D8E8, false);
                cylinderMinusHSlider.setWidth(halfSliderW);
                cylinderMinusHSlider.setX(rightX + 18 + halfSliderW + 16);
                cylinderMinusHSlider.setY(hRowY);
                cylinderMinusHSlider.render(g, mouseX, mouseY, partialTick);

                // 圆柱填充模式按钮
                int cylFillY = hRowY + ADV_SLIDER_ROW_H + 2;
                ShapeFillMode activeCylFill = advDestroyOptions.getCylinderFillMode();
                cylinderFillModeButtons[0].setX(rightX);
                cylinderFillModeButtons[0].setY(cylFillY);
                cylinderFillModeButtons[1].setX(rightX + 54);
                cylinderFillModeButtons[1].setY(cylFillY);
                for (int i = 0; i < 2; i++) {
                    ShapeFillMode fm = ShapeFillMode.values()[i];
                    int bx = rightX + i * 54;
                    boolean sel = activeCylFill == fm;
                    boolean hovered = cylinderFillModeButtons[i].isHoveredOrFocused();
                    int vOffset = sel ? MODE_BUTTON_STATE_H * 2 : (hovered ? MODE_BUTTON_STATE_H : 0);
                    RtsTextureRenderer.drawTextureHighPrecision(
                            g, SELECTION_DOT_TEXTURE, bx + 2, cylFillY + 2, 14, 14,
                            0, vOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                            MODE_BUTTON_SHEET_W, MODE_BUTTON_H, 0, 0xFFFFFFFF);
                    cylinderFillModeButtons[i].render(g, mouseX, mouseY, partialTick);
                }
            }
            case STAIRS -> {
                g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.adv_stairs_count"),
                        rightX, shapeTitleY, 0xD8E3EE, false);
                stairsCountSlider.setWidth(sliderW);
                stairsCountSlider.setX(rightX);
                stairsCountSlider.setY(shapeTitleY + 16);
                stairsCountSlider.render(g, mouseX, mouseY, partialTick);
                String countStr = Integer.toString(advDestroyOptions.getStairsCount());
                g.drawString(screen.font(), countStr, rightX + sliderW + 6, shapeTitleY + 20, 0xFFEAF4FF, false);

                // 旋转按钮
                int btnRowY = shapeTitleY + 16 + ADV_SLIDER_ROW_H;
                stairsRotateButton.setX(rightX);
                stairsRotateButton.setY(btnRowY);
                stairsRotateButton.render(g, mouseX, mouseY, partialTick);
                String rotStr = Integer.toString(advDestroyOptions.getStairsRotation()) + "°";
                g.drawString(screen.font(), rotStr, rightX + 54, btnRowY + 2, 0xFFEAF4FF, false);

                // 对称按钮
                stairsSymmetricButton.setX(rightX);
                stairsSymmetricButton.setY(btnRowY + 22);
                stairsSymmetricButton.render(g, mouseX, mouseY, partialTick);

                boolean sym = advDestroyOptions.isStairsSymmetric();
                boolean symHovered = stairsSymmetricButton.isHoveredOrFocused();
                int symOffset = sym ? MODE_BUTTON_STATE_H * 2 : (symHovered ? MODE_BUTTON_STATE_H : 0);
                RtsTextureRenderer.drawTextureHighPrecision(
                        g, SELECTION_DOT_TEXTURE, rightX + 54, btnRowY + 23, 14, 14,
                        0, symOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                        MODE_BUTTON_SHEET_W, MODE_BUTTON_H, 0, 0xFFFFFFFF);
            }
            case LUMBER -> {
                g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.adv_stairs_count"),
                        rightX, shapeTitleY, 0xD8E3EE, false);
                lumberLimitSlider.setWidth(sliderW);
                lumberLimitSlider.setX(rightX);
                lumberLimitSlider.setY(shapeTitleY + 16);
                lumberLimitSlider.render(g, mouseX, mouseY, partialTick);
                String countStr = Integer.toString(advDestroyOptions.getLumberLimit());
                g.drawString(screen.font(), countStr, rightX + sliderW + 6, shapeTitleY + 20, 0xFFEAF4FF, false);

                // 光头强附体开关
                int toggleRowY = shapeTitleY + 16 + ADV_SLIDER_ROW_H;
                lumberStrongManToggle.setX(rightX);
                lumberStrongManToggle.setY(toggleRowY);
                lumberStrongManToggle.render(g, mouseX, mouseY, partialTick);
                boolean strongMan = advDestroyOptions.isLumberStrongMan();
                boolean smHovered = lumberStrongManToggle.isHoveredOrFocused();
                int smOffset = strongMan ? MODE_BUTTON_STATE_H * 2 : (smHovered ? MODE_BUTTON_STATE_H : 0);
                RtsTextureRenderer.drawTextureHighPrecision(
                        g, SELECTION_DOT_TEXTURE, rightX + 84, toggleRowY + 1, 16, 16,
                        0, smOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                        MODE_BUTTON_SHEET_W, MODE_BUTTON_H, 0, 0xFFFFFFFF);

                // 允许破坏玩家造物开关
                lumberAllowPlayerBlocksToggle.setX(rightX);
                lumberAllowPlayerBlocksToggle.setY(toggleRowY + 22);
                lumberAllowPlayerBlocksToggle.render(g, mouseX, mouseY, partialTick);
                boolean allowPlayer = advDestroyOptions.isLumberAllowPlayerBlocks();
                boolean apHovered = lumberAllowPlayerBlocksToggle.isHoveredOrFocused();
                int apOffset = allowPlayer ? MODE_BUTTON_STATE_H * 2 : (apHovered ? MODE_BUTTON_STATE_H : 0);
                RtsTextureRenderer.drawTextureHighPrecision(
                        g, SELECTION_DOT_TEXTURE, rightX + 84, toggleRowY + 23, 16, 16,
                        0, apOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                        MODE_BUTTON_SHEET_W, MODE_BUTTON_H, 0, 0xFFFFFFFF);
            }
        }
    }

    private void renderAdvancedDestroyBottomInfo(GuiGraphics g, int x, int y, int bodyY) {
        int dividerY = y + currentBasePanelHeight();
        g.fill(x + 6, dividerY - 1, x + windowWidth - 6, dividerY, 0xFF647B92);
        renderProgressStrip(g, x, dividerY);

        int textY = dividerY + 12;

        // 活跃工作流进度
        RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
        if (workflow != null) {
            String fullText = workflow.progressText() + "    "
                    + screen.text("screen.rtsbuilding.quick_build.destroy_remaining", workflow.remainingBlocks());
            g.drawString(screen.font(), fullText, x + 8, textY, 0xFFB8FFB8, false);
            return;
        }

        // 更新鼠标指向
        var hit = screen.pickBlockHit();
        if (hit != null) {
            advDestroyHandler.setCursorTarget(hit.getBlockPos());
            advDestroyHandler.setHitFace(hit.getDirection());
        }
        advDestroyHandler.tick();
        boolean isAnchored = advDestroyHandler.isAnchored();
        int blockCount = advDestroyHandler.getBlockCount();

        // 伐木子模式：显示扫描详情
        if (advDestroyOptions.getSubMode() == AdvancedDestroySubMode.LUMBER) {
            var result = advDestroyHandler.getLumberResult();
            if (result != null && !result.all().isEmpty()) {
                String treeText = screen.text("screen.rtsbuilding.quick_build.adv_lumber_detected",
                        result.logCount(), result.leafCount(), result.mushroomCount());
                g.drawString(screen.font(), treeText, x + 8, textY, 0xFFB8FFB8, false);
                int nextY = textY + screen.font().lineHeight + 3;

                if (result.hasPlayerBlocks() && !advDestroyOptions.isLumberAllowPlayerBlocks()) {
                    g.drawString(screen.font(),
                            Component.translatable("screen.rtsbuilding.quick_build.adv_lumber_player_blocks"),
                            x + 8, nextY, 0xFFFF8E8E, false);
                    return;
                }
                if (result.exceeded()) {
                    g.drawString(screen.font(),
                            Component.translatable("screen.rtsbuilding.quick_build.adv_lumber_exceeded"),
                            x + 8, nextY, 0xFFFFD700, false);
                    return;
                }
                if (isAnchored) {
                    g.drawString(screen.font(),
                            Component.translatable("screen.rtsbuilding.quick_build.adv_anchored_hint"),
                            x + 8, nextY, 0xFFFFD700, false);
                } else {
                    String hintKey = com.rtsbuilding.rtsbuilding.Config.isKeyboardBatchConfirmEnabled()
                            ? "screen.rtsbuilding.quick_build.adv_hint_keyboard"
                            : "screen.rtsbuilding.quick_build.adv_hint_mouse";
                    g.drawString(screen.font(), Component.translatable(hintKey), x + 8, nextY, 0xFFB8B8, false);
                }
            } else {
                g.drawString(screen.font(),
                        Component.translatable("screen.rtsbuilding.quick_build.adv_lumber_no_tree"),
                        x + 8, textY, 0xFFB8B8, false);
            }
            return;
        }

        if (blockCount > 0) {
            String countText = screen.text("screen.rtsbuilding.quick_build.adv_block_count", blockCount);
            g.drawString(screen.font(), countText, x + 8, textY, 0xFFB8FFB8, false);

            if (isAnchored) {
                g.drawString(screen.font(),
                        Component.translatable("screen.rtsbuilding.quick_build.adv_anchored_hint"),
                        x + 8, textY + screen.font().lineHeight + 3, 0xFFFFD700, false);
            } else {
                String hintKey = com.rtsbuilding.rtsbuilding.Config.isKeyboardBatchConfirmEnabled()
                        ? "screen.rtsbuilding.quick_build.adv_hint_keyboard"
                        : "screen.rtsbuilding.quick_build.adv_hint_mouse";
                g.drawString(screen.font(), Component.translatable(hintKey),
                        x + 8, textY + screen.font().lineHeight + 3, 0xFFB8B8, false);
            }
        } else {
            g.drawString(screen.font(),
                    Component.translatable("screen.rtsbuilding.quick_build.adv_no_target"),
                    x + 8, textY, 0xFFB8B8, false);
        }
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        // 过滤面板事件（优先处理，防止透传）
        if (filterPanel.isVisible() && filterPanel.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
        if (this.chainLimitSlider != null && isRangeDestroyChainMode()) {
            if (this.chainLimitSlider.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
        // 智能放置滑条
        if (isSmartPlaceActive()) {
            if (fillCountSlider != null && fillCountSlider.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
            if (detectionDiameterSlider != null && detectionDiameterSlider.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
        // 高级破坏控件
        if (isAdvancedDestroyActive() && handleAdvDestroyClick(mouseX, mouseY, button)) {
            return;
        }
        if (handleModeToggleClick(mouseX, mouseY)) {
            return;
        }
        // 智能放置子模式按钮
        if (isSmartPlaceActive() && smartPlaceModeButtons != null) {
            for (WindowButton btn : smartPlaceModeButtons) {
                if (btn.mouseClicked(mouseX, mouseY, button)) {
                    return;
                }
            }
            return; // 智能放置模式下不处理形状按钮和填充按钮
        }
        // 委托给按钮处理
        for (WindowButton btn : shapeButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
        if (fillModeButtons != null) {
            for (WindowButton btn : fillModeButtons) {
                if (btn.mouseClicked(mouseX, mouseY, button)) {
                    return;
                }
            }
        }
        if (this.connectToggle != null && this.connectToggle.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (filterPanel.isVisible() && filterPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (this.chainLimitSlider != null && isRangeDestroyChainMode()) {
            if (this.chainLimitSlider.mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (isSmartPlaceActive()) {
            if (fillCountSlider != null && fillCountSlider.mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
            if (detectionDiameterSlider != null && detectionDiameterSlider.mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (isAdvancedDestroyActive() && handleAdvDestroyDrag(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.chainLimitSlider != null) {
            this.chainLimitSlider.mouseReleased(mouseX, mouseY, button);
        }
        if (isSmartPlaceActive()) {
            if (fillCountSlider != null) {
                fillCountSlider.mouseReleased(mouseX, mouseY, button);
            }
            if (detectionDiameterSlider != null) {
                detectionDiameterSlider.mouseReleased(mouseX, mouseY, button);
            }
        }
        if (isAdvancedDestroyActive()) {
            handleAdvDestroyRelease(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (filterPanel.isVisible() && filterPanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (filterPanel.isVisible() && filterPanel.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    private boolean handleAdvDestroyClick(double mouseX, double mouseY, int button) {
        // 子模式按钮
        if (advDestroySubModeButtons != null) {
            for (WindowButton btn : advDestroySubModeButtons) {
                if (btn.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        // 根据子模式处理滑条和按钮
        switch (advDestroyOptions.getSubMode()) {
            case RECTANGLE -> {
                if (sliderClicked(rectSliderPlusX, mouseX, mouseY, button)) return true;
                if (sliderClicked(rectSliderMinusX, mouseX, mouseY, button)) return true;
                if (sliderClicked(rectSliderPlusY, mouseX, mouseY, button)) return true;
                if (sliderClicked(rectSliderMinusY, mouseX, mouseY, button)) return true;
                if (sliderClicked(rectSliderPlusZ, mouseX, mouseY, button)) return true;
                if (sliderClicked(rectSliderMinusZ, mouseX, mouseY, button)) return true;
                if (chunkRectSliderPlusY != null && sliderClicked(chunkRectSliderPlusY, mouseX, mouseY, button)) return true;
                if (chunkRectSliderMinusY != null && sliderClicked(chunkRectSliderMinusY, mouseX, mouseY, button)) return true;
                if (rectFillModeButtons != null) {
                    for (WindowButton btn : rectFillModeButtons) {
                        if (btn.mouseClicked(mouseX, mouseY, button)) return true;
                    }
                }
                if (rectSizeModeBtn != null && rectSizeModeBtn.mouseClicked(mouseX, mouseY, button)) return true;
                if (rectChunkModeBtn != null && rectChunkModeBtn.mouseClicked(mouseX, mouseY, button)) return true;
                if (filterSlotBtn != null && filterSlotBtn.mouseClicked(mouseX, mouseY, button)) return true;
                if (filterPanelBtn != null && filterPanelBtn.mouseClicked(mouseX, mouseY, button)) return true;
                if (filterInverseBtn != null && filterInverseBtn.mouseClicked(mouseX, mouseY, button)) return true;
            }
            case CYLINDER -> {
                if (sliderClicked(cylinderRadiusSlider, mouseX, mouseY, button)) return true;
                if (sliderClicked(cylinderPlusHSlider, mouseX, mouseY, button)) return true;
                if (sliderClicked(cylinderMinusHSlider, mouseX, mouseY, button)) return true;
                if (cylinderFillModeButtons != null) {
                    for (WindowButton btn : cylinderFillModeButtons) {
                        if (btn.mouseClicked(mouseX, mouseY, button)) return true;
                    }
                }
            }
            case STAIRS -> {
                if (sliderClicked(stairsCountSlider, mouseX, mouseY, button)) return true;
                if (stairsRotateButton != null && stairsRotateButton.mouseClicked(mouseX, mouseY, button)) return true;
                if (stairsSymmetricButton != null && stairsSymmetricButton.mouseClicked(mouseX, mouseY, button)) return true;
            }
            case LUMBER -> {
                if (sliderClicked(lumberLimitSlider, mouseX, mouseY, button)) return true;
                if (lumberStrongManToggle != null && lumberStrongManToggle.mouseClicked(mouseX, mouseY, button)) return true;
                if (lumberAllowPlayerBlocksToggle != null && lumberAllowPlayerBlocksToggle.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        return false;
    }

    private static boolean sliderClicked(WindowSlider slider, double mouseX, double mouseY, int button) {
        return slider != null && slider.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleAdvDestroyDrag(double mouseX, double mouseY, int button) {
        switch (advDestroyOptions.getSubMode()) {
            case RECTANGLE -> {
                if (sliderDragged(rectSliderPlusX, mouseX, mouseY, button)) return true;
                if (sliderDragged(rectSliderMinusX, mouseX, mouseY, button)) return true;
                if (sliderDragged(rectSliderPlusY, mouseX, mouseY, button)) return true;
                if (sliderDragged(rectSliderMinusY, mouseX, mouseY, button)) return true;
                if (sliderDragged(rectSliderPlusZ, mouseX, mouseY, button)) return true;
                if (sliderDragged(rectSliderMinusZ, mouseX, mouseY, button)) return true;
                if (chunkRectSliderPlusY != null && sliderDragged(chunkRectSliderPlusY, mouseX, mouseY, button)) return true;
                if (chunkRectSliderMinusY != null && sliderDragged(chunkRectSliderMinusY, mouseX, mouseY, button)) return true;
            }
            case CYLINDER -> {
                if (sliderDragged(cylinderRadiusSlider, mouseX, mouseY, button)) return true;
                if (sliderDragged(cylinderPlusHSlider, mouseX, mouseY, button)) return true;
                if (sliderDragged(cylinderMinusHSlider, mouseX, mouseY, button)) return true;
            }
            case STAIRS -> {
                if (sliderDragged(stairsCountSlider, mouseX, mouseY, button)) return true;
            }
            case LUMBER -> {
                if (sliderDragged(lumberLimitSlider, mouseX, mouseY, button)) return true;
            }
        }
        return false;
    }

    private static boolean sliderDragged(WindowSlider slider, double mouseX, double mouseY, int button) {
        return slider != null && slider.mouseDragged(mouseX, mouseY, button);
    }

    private void handleAdvDestroyRelease(double mouseX, double mouseY, int button) {
        switch (advDestroyOptions.getSubMode()) {
            case RECTANGLE -> {
                sliderReleased(rectSliderPlusX, mouseX, mouseY, button);
                sliderReleased(rectSliderMinusX, mouseX, mouseY, button);
                sliderReleased(rectSliderPlusY, mouseX, mouseY, button);
                sliderReleased(rectSliderMinusY, mouseX, mouseY, button);
                sliderReleased(rectSliderPlusZ, mouseX, mouseY, button);
                sliderReleased(rectSliderMinusZ, mouseX, mouseY, button);
                if (chunkRectSliderPlusY != null) sliderReleased(chunkRectSliderPlusY, mouseX, mouseY, button);
                if (chunkRectSliderMinusY != null) sliderReleased(chunkRectSliderMinusY, mouseX, mouseY, button);
            }
            case CYLINDER -> {
                sliderReleased(cylinderRadiusSlider, mouseX, mouseY, button);
                sliderReleased(cylinderPlusHSlider, mouseX, mouseY, button);
                sliderReleased(cylinderMinusHSlider, mouseX, mouseY, button);
            }
            case STAIRS -> {
                sliderReleased(stairsCountSlider, mouseX, mouseY, button);
            }
            case LUMBER -> {
                sliderReleased(lumberLimitSlider, mouseX, mouseY, button);
            }
        }
    }

    private static void sliderReleased(WindowSlider slider, double mouseX, double mouseY, int button) {
        if (slider != null) slider.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleModeToggleClick(double mouseX, double mouseY) {
        int bodyY = contentY();
        int totalW = this.windowWidth - 16;
        int buttonW = (totalW - MODE_TOGGLE_GAP) / 2;
        int col1X = this.windowX + 8;
        int col2X = col1X + buttonW + MODE_TOGGLE_GAP;
        int row1Y = bodyY + MODE_ROW_TOP;
        int row2Y = bodyY + MODE_ROW_2_TOP;

        boolean inCol1 = mouseX >= col1X && mouseX < col1X + buttonW;
        boolean inCol2 = mouseX >= col2X && mouseX < col2X + buttonW;

        // 第 1 行
        if (mouseY >= row1Y && mouseY < row1Y + MODE_TOGGLE_H) {
            if (inCol1) {
                setMode(QuickBuildMode.BUILD);
                return true;
            }
            if (inCol2) {
                if (!canUseRangeDestroy()) {
                    return true;
                }
                setMode(QuickBuildMode.DESTROY);
                return true;
            }
        }
        // 第 2 行：智能放置（col1） / 高级破坏（col2）
        if (mouseY >= row2Y && mouseY < row2Y + MODE_TOGGLE_H) {
            if (inCol1) {
                setMode(QuickBuildMode.SMART_PLACE);
                return true;
            }
            if (inCol2) {
                if (!canUseAdvancedDestroy()) {
                    return true;
                }
                setMode(QuickBuildMode.ADVANCED_DESTROY);
                return true;
            }
        }
        return false;
    }

    // ======================== 抽象方法实现 ========================

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.quick_build.title");
    }

    @Override
    protected int getDefaultWidth() {
        return QUICK_BUILD_PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return QUICK_BUILD_PANEL_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return QUICK_BUILD_PANEL_W; // 固定宽度，不允许横向缩放
    }

    @Override
    protected int getMinWindowHeight() {
        return QUICK_BUILD_PANEL_MIN_H;
    }

    @Override
    protected void computeDefaultPosition() {
        int y = TOP_H + 40;
        int availableH = screen.getFloatingPanelAvailableHeight(y);
        if (availableH >= QUICK_BUILD_PANEL_MIN_H) {
            this.windowHeight = QUICK_BUILD_PANEL_H;
        }
        this.windowX = screen.width - QUICK_BUILD_PANEL_W - 4;
        this.windowY = y;
    }

    @Override
    protected boolean canShowWindow() {
        return super.canShowWindow() && screen != null && screen.canUseQuickBuild();
    }

    // ======================== 抽象方法实现 & API ========================

    @Override
    protected void onClose() {
        smartPlaceHandler.clear();
        advDestroyHandler.clear();
        restoreSingleBlockCursor();
        if (screen != null) {
            screen.persistUiState();
        }
    }

    public QuickBuildMode getMode() {
        return this.quickBuildMode;
    }

    public void setMode(QuickBuildMode mode) {
        QuickBuildMode next = mode == null ? QuickBuildMode.BUILD : mode;
        if (next == QuickBuildMode.DESTROY && !canUseRangeDestroy()) {
            next = QuickBuildMode.BUILD;
        } else if (next == QuickBuildMode.DESTROY) {
            this.rangeDestroyShape = effectiveRangeDestroyShape();
        }
        if (next == QuickBuildMode.ADVANCED_DESTROY && !canUseAdvancedDestroy()) {
            next = QuickBuildMode.BUILD;
        }
        if (this.quickBuildMode == next) {
            if (isOpen()) {
                if (isSmartPlaceActive()) {
                    // 已处于智能放置模式，无需切换控制器状态
                } else if (isAdvancedDestroyActive()) {
                    // 已处于高级破坏模式
                } else {
                    applyActiveShapeToController();
                }
            } else {
                restoreSingleBlockCursor();
            }
            return;
        }
        // 离开旧模式时的清理
        if (isSmartPlaceActive()) {
            smartPlaceHandler.clear();
            screen.getShapeController().exitSmartPlace();
        }
        if (isAdvancedDestroyActive()) {
            advDestroyHandler.clear();
        }
        this.quickBuildMode = next;
        if (isOpen()) {
            if (next == QuickBuildMode.SMART_PLACE) {
                screen.getShapeController().switchToSmartPlace();
                screen.clearShapeBuildSession();
                this.controller.clearAreaMineSession();
            } else if (next == QuickBuildMode.ADVANCED_DESTROY) {
                screen.getShapeController().switchToDestroy();
                screen.clearShapeBuildSession();
                this.controller.clearAreaMineSession();
            } else {
                if (isDestroyModeActive()) {
                    screen.getShapeController().switchToDestroy();
                } else {
                    screen.getShapeController().switchToBuild();
                }
                applyActiveShapeToController();
                screen.clearShapeBuildSession();
                this.controller.clearAreaMineSession();
            }
        } else {
            restoreSingleBlockCursor();
        }
        screen.persistUiState();
        rebuildFillModeButtons();
        rebuildAllShapeButtons();
    }

    public boolean isRangeDestroyMode() {
        return effectiveMode() == QuickBuildMode.DESTROY;
    }

    public boolean isRangeDestroyChainMode() {
        return isRangeDestroyMode() && effectiveRangeDestroyShape() == AreaMineShape.CHAIN;
    }

    public boolean isAdvancedRangeDestroyBoxMode() {
        return isAdvancedShapeMode();
    }

    public boolean isAdvancedRangeDestroyShapeMode() {
        return isRangeDestroyMode() && isAdvancedShapeMode();
    }

    public boolean isAdvancedShapeMode() {
        BuildShape shape = activeAdvancedShape();
        return supportsAdvancedShape(shape) && isAdvancedShape(shape);
    }

    private BuildShape activeAdvancedShape() {
        return isDestroyModeActive() ? toBuildShape(effectiveRangeDestroyShape()) : this.buildModeShape;
    }

    private static boolean supportsAdvancedShape(BuildShape shape) {
        return switch (shape == null ? BuildShape.BLOCK : shape) {
            case SQUARE, WALL, CIRCLE, CYLINDER, BALL, BOX -> true;
            case BLOCK, LINE -> false;
        };
    }

    private static boolean supportsVerticalToggle(BuildShape shape) {
        return shape == BuildShape.CIRCLE || shape == BuildShape.CYLINDER;
    }

    private int verticalButtonIndex(List<ShapeFillMode> modes) {
        return supportsVerticalToggle(activeAdvancedShape()) ? modes.size() : -1;
    }

    private int advancedButtonIndex(List<ShapeFillMode> modes) {
        if (!supportsAdvancedShape(activeAdvancedShape())) {
            return -1;
        }
        return modes.size() + (supportsVerticalToggle(activeAdvancedShape()) ? 1 : 0);
    }

    private boolean isAdvancedShape(BuildShape shape) {
        return switch (shape == null ? BuildShape.BLOCK : shape) {
            case SQUARE -> this.advancedRangeDestroySquare;
            case WALL -> this.advancedRangeDestroyWall;
            case CIRCLE -> this.advancedRangeDestroyCircle;
            case CYLINDER -> this.advancedRangeDestroyCylinder;
            case BALL -> this.advancedRangeDestroyBall;
            case BOX -> this.advancedRangeDestroyBox;
            case BLOCK, LINE -> false;
        };
    }

    private void setAdvancedShape(BuildShape shape, boolean value) {
        switch (shape == null ? BuildShape.BLOCK : shape) {
            case SQUARE -> this.advancedRangeDestroySquare = value;
            case WALL -> this.advancedRangeDestroyWall = value;
            case CIRCLE -> this.advancedRangeDestroyCircle = value;
            case CYLINDER -> this.advancedRangeDestroyCylinder = value;
            case BALL -> this.advancedRangeDestroyBall = value;
            case BOX -> this.advancedRangeDestroyBox = value;
            case BLOCK, LINE -> {}
        }
    }

    public boolean isRoundShapeVertical(BuildShape shape) {
        return switch (shape == null ? BuildShape.BLOCK : shape) {
            case CIRCLE -> this.circleVertical;
            case CYLINDER -> this.cylinderVertical;
            default -> false;
        };
    }

    private void setRoundShapeVertical(BuildShape shape, boolean value) {
        switch (shape == null ? BuildShape.BLOCK : shape) {
            case CIRCLE -> this.circleVertical = value;
            case CYLINDER -> this.cylinderVertical = value;
            default -> {}
        }
    }

    public static AreaMineShape toAreaMineShape(BuildShape shape) {
        return switch (shape == null ? BuildShape.BLOCK : shape) {
            case LINE -> AreaMineShape.LINE;
            case SQUARE -> AreaMineShape.SQUARE;
            case WALL -> AreaMineShape.WALL;
            case CIRCLE -> AreaMineShape.CIRCLE;
            case CYLINDER -> AreaMineShape.CYLINDER;
            case BALL -> AreaMineShape.BALL;
            case BOX -> AreaMineShape.BOX;
            case BLOCK -> AreaMineShape.BLOCK;
        };
    }

    private static BuildShape toBuildShape(AreaMineShape shape) {
        return switch (shape == null ? AreaMineShape.BLOCK : shape) {
            case LINE -> BuildShape.LINE;
            case SQUARE -> BuildShape.SQUARE;
            case WALL -> BuildShape.WALL;
            case CIRCLE -> BuildShape.CIRCLE;
            case CYLINDER -> BuildShape.CYLINDER;
            case BALL -> BuildShape.BALL;
            case BOX -> BuildShape.BOX;
            case BLOCK, CHAIN -> BuildShape.BLOCK;
        };
    }

    @Override
    public void setOpen(boolean open) {
        boolean wasOpen = isOpen();
        super.setOpen(open);
        if (open && !wasOpen) {
            applyActiveShapeToController();
            rebuildFillModeButtons();
            rebuildAllShapeButtons();
            if (screen != null) {
                screen.persistUiState();
            }
        }
    }

    /** 返回当前布局信息，供其他面板计算相对位置。 */
    public PanelLayouts.QuickBuildPanelLayout resolveLayout() {
        if (!isOpen() || !canShowWindow()) {
            return null;
        }
        return new PanelLayouts.QuickBuildPanelLayout(
                windowX, windowY, windowWidth, windowHeight);
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 是否显示底部提示文字。
     * 仅在玩家选中了可放置的方块物品时扩展面板并显示。
     */
    private int currentBasePanelHeight() {
        if (isSmartPlaceActive()) return QUICK_BUILD_SMART_PLACE_PANEL_H;
        if (isAdvancedDestroyActive()) return QUICK_BUILD_PANEL_H;
        return isDestroyModeActive() ? QUICK_BUILD_DESTROY_PANEL_H : QUICK_BUILD_PANEL_H;
    }

    private QuickBuildMode effectiveMode() {
        if (this.quickBuildMode == QuickBuildMode.DESTROY && !canUseRangeDestroy()) {
            return QuickBuildMode.BUILD;
        }
        if (this.quickBuildMode == QuickBuildMode.ADVANCED_DESTROY && !canUseAdvancedDestroy()) {
            return QuickBuildMode.BUILD;
        }
        return this.quickBuildMode;
    }

    private boolean isDestroyModeActive() {
        return effectiveMode() == QuickBuildMode.DESTROY;
    }

    private boolean canUseRangeDestroy() {
        return QuickBuildUnlockPolicy.canUseAnyDestroyShape(
                this.controller.isProgressionEnabled(),
                hasPlugin(BuiltInRtsPluginCatalog.CHAIN_BREAK_PLUGIN),
                hasPlugin(BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN));
    }

    private boolean canUseAdvancedDestroy() {
        return QuickBuildUnlockPolicy.canUseAdvancedDestroy(
                this.controller.isProgressionEnabled(),
                hasPlugin(BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN));
    }

    private boolean canUseDestroyShape(AreaMineShape shape) {
        return QuickBuildUnlockPolicy.canUseDestroyShape(
                this.controller.isProgressionEnabled(),
                hasPlugin(BuiltInRtsPluginCatalog.CHAIN_BREAK_PLUGIN),
                hasPlugin(BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN),
                shape);
    }

    private AreaMineShape effectiveRangeDestroyShape() {
        AreaMineShape current = this.rangeDestroyShape == null ? AreaMineShape.CHAIN : this.rangeDestroyShape;
        if (canUseDestroyShape(current)) {
            return current;
        }
        AreaMineShape fallback = QuickBuildUnlockPolicy.firstAvailableDestroyShape(
                this.controller.isProgressionEnabled(),
                hasPlugin(BuiltInRtsPluginCatalog.CHAIN_BREAK_PLUGIN),
                hasPlugin(BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN));
        if (fallback == null) {
            return current;
        }
        this.rangeDestroyShape = fallback;
        if (isOpen() && this.quickBuildMode == QuickBuildMode.DESTROY && this.controller != null) {
            this.controller.setAreaMineShape(fallback);
            this.controller.setBuildShape(toBuildShape(fallback));
            if (fallback != AreaMineShape.CHAIN && this.screen != null) {
                this.screen.ensureFillModeForShape(this.controller.getBuildShape());
            }
        }
        return fallback;
    }

    private boolean hasPlugin(ResourceLocation pluginId) {
        return pluginId != null && this.controller.hasInstalledPlugin(pluginId.toString());
    }

    private void applyActiveShapeToController() {
        if (isDestroyModeActive()) {
            AreaMineShape shape = effectiveRangeDestroyShape();
            this.rangeDestroyShape = shape;
            this.controller.setAreaMineShape(shape);
            this.controller.setBuildShape(toBuildShape(shape));
            if (shape != AreaMineShape.CHAIN) {
                screen.ensureFillModeForShape(this.controller.getBuildShape());
            }
            return;
        }
        this.controller.setBuildShape(this.buildModeShape);
        screen.ensureFillModeForShape(this.buildModeShape);
    }

    private void restoreSingleBlockCursor() {
        this.controller.setBuildShape(BuildShape.BLOCK);
        this.controller.clearAreaMineSession();
        if (screen != null) {
            screen.clearShapeBuildSession();
        }
    }

    /**
     * 解析当前用于形状建造的物品栈：
     * 优先返回 RTS 存储中选中的物品，其次返回玩家手持工具槽位的物品。
     */
    private ItemStack resolveShapeBuildItem() {
        ItemStack selected = controller.getSelectedItemPreview();
        if (!selected.isEmpty()) {
            return selected;
        }
        var mc = screen.getMinecraft();
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        return mc.player.getInventory().getItem(mc.player.getInventory().selected);
    }
}
