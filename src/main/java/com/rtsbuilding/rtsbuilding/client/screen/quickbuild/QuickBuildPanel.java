package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.client.service.destruction.RtsDestroyPreviewPlanner;
import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.client.widget.WindowSlider;
import com.rtsbuilding.rtsbuilding.client.widget.RtsControlRole;
import com.rtsbuilding.rtsbuilding.client.widget.RtsControlState;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlan;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiCatalogPage;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiTransition;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.canvas.QuickBuildChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.EnumMap;
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
    private static final int RIGHT_COL_X = QuickBuildWindowLayout.RIGHT_COL_X;

    /** 形状按钮行间距 */
    private static final int SHAPE_ROW_PITCH = QuickBuildWindowLayout.SHAPE_ROW_PITCH;
    private static final int MODE_TOGGLE_H = QuickBuildWindowLayout.MODE_H;
    /** 连锁破坏滑条 */

    // ======================== 面板尺寸 ========================
    private static final int QUICK_BUILD_PANEL_W = QuickBuildWindowLayout.WINDOW_W;
    private static final int QUICK_BUILD_PANEL_H = QuickBuildWindowLayout.windowHeight(false);
    private static final int QUICK_BUILD_DESTROY_PANEL_H = QuickBuildWindowLayout.windowHeight(true);
    private static final int QUICK_BUILD_PANEL_MIN_H = QuickBuildWindowLayout.windowHeight(false);

    /** 底部提示文字区域额外高度 */
    private static final int BOTTOM_INFO_H = QuickBuildWindowLayout.BOTTOM_INFO_H;
    private static final int BOTTOM_TEXT_MAX_LINES = 3;

    /** 选择指示器贴图 */
    private static final Identifier SELECTION_DOT_TEXTURE =
            Identifier.tryParse("rtsbuilding:textures/gui/general/mode_button.png");
    private static final Identifier[] CONVENIENCE_TOOL_TEXTURES = {
            Identifier.tryParse("rtsbuilding:textures/gui/new_2nd_icons/cube.png"),
            Identifier.tryParse("rtsbuilding:textures/gui/new_2nd_icons/smart_break/stair.png"),
            Identifier.tryParse("rtsbuilding:textures/gui/new_2nd_icons/smart_break/tree.png")
    };
    private static final Identifier SMART_FILL_TOOL_TEXTURE =
            Identifier.tryParse("rtsbuilding:textures/gui/new_2nd_icons/fill_water/cave.png");

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
    private static final Identifier[] BUILD_SHAPE_TEXTURES = {
            QUICK_BUILD_SINGLE_BLOCK,
            QUICK_BUILD_LINE_BLOCK,
            QUICK_BUILD_SQUARE_BLOCK,
            QUICK_BUILD_WALL_BLOCK,
            QUICK_BUILD_CIRCLE_BLOCK,
            QUICK_BUILD_CYLINDER_BLOCK,
            QUICK_BUILD_BALL_BLOCK,
            QUICK_BUILD_BOX_BLOCK
    };

    private static final Identifier[] DESTROY_SHAPE_TEXTURES = {
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
    /** 破坏页的二级目录与工具参数属于玩家偏好，不是第二份服务端状态。 */
    private QuickBuildUiCatalogPage catalogPage = QuickBuildUiCatalogPage.SHAPES;
    private QuickBuildUiConvenienceTool convenienceTool = QuickBuildUiConvenienceTool.REPEAT_BOX;
    private QuickBuildUiConvenienceSettings convenienceSettings = QuickBuildUiConvenienceSettings.DEFAULT;
    private final RtsDestroyPreviewPlanner conveniencePreviewPlanner = new RtsDestroyPreviewPlanner();
    private final WindowButton[] catalogButtons = new WindowButton[2];
    private final WindowButton[] convenienceToolButtons = new WindowButton[3];
    private final EnumMap<QuickBuildUiConvenienceParameter, WindowSlider> convenienceSliders =
            new EnumMap<>(QuickBuildUiConvenienceParameter.class);
    private boolean syncingConvenienceSliders;
    /** 智能填坑只保留本地两次确认锚点，服务端仍会重新规划。 */
    private final SmartFillClientSession smartFill = new SmartFillClientSession();
    private WindowButton smartFillToolButton;
    private WindowSlider smartFillMaxBlocksSlider;
    private WindowSlider smartFillDiameterSlider;
    private boolean syncingSmartFillSliders;
    private boolean advancedRangeDestroySquare;
    private boolean advancedRangeDestroyWall;
    private boolean advancedRangeDestroyCircle;
    private boolean advancedRangeDestroyCylinder;
    private boolean advancedRangeDestroyBall;
    private boolean advancedRangeDestroyBox;
    private boolean circleVertical;
    private boolean cylinderVertical;
    /** 模式按钮使用固定 ID，避免每帧创建视觉状态，也不影响即时业务切换。 */
    private final UiControlAnimationRegistry<String> contentAnimations =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 24);

    /** 缓存的形状（BUILD），用于检测 fill mode 是否需要重建 */
    private BuildShape lastFillShape;
    /** 缓存的形状（DESTROY），解决 CHAIN↔BLOCK 映射到相同 BuildShape 的问题 */
    private AreaMineShape lastAreaMineShape;
    /** 直线连接模式按钮 */
    private WindowButton connectToggle;

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
            PersistableProperty.intField(
                    "quick_build_smart_fill_max_blocks",
                    state -> state.quickBuild.smartFillMaxBlocks,
                    (state, v) -> state.quickBuild.smartFillMaxBlocks = v,
                    this::getSmartFillMaxBlocks,
                    this::setSmartFillMaxBlocks),
            PersistableProperty.intField(
                    "quick_build_smart_fill_diameter",
                    state -> state.quickBuild.smartFillDiameter,
                    (state, v) -> state.quickBuild.smartFillDiameter = v,
                    this::getSmartFillDiameter,
                    this::setSmartFillDiameter),
            PersistableProperty.enumField(
                    "quick_build_catalog_page",
                    state -> state.quickBuild.mining.catalogPage,
                    (state, v) -> state.quickBuild.mining.catalogPage = v,
                    () -> this.catalogPage,
                    v -> this.catalogPage = v,
                    QuickBuildUiCatalogPage.SHAPES,
                    QuickBuildUiCatalogPage.class),
            PersistableProperty.enumField(
                    "quick_build_convenience_tool",
                    state -> state.quickBuild.mining.convenienceTool,
                    (state, v) -> state.quickBuild.mining.convenienceTool = v,
                    () -> this.convenienceTool,
                    v -> this.convenienceTool = v,
                    QuickBuildUiConvenienceTool.REPEAT_BOX,
                    QuickBuildUiConvenienceTool.class),
            PersistableProperty.intField(
                    "quick_build_repeat_size_x",
                    state -> state.quickBuild.mining.repeatSizeX,
                    (state, v) -> state.quickBuild.mining.repeatSizeX = v,
                    () -> this.convenienceSettings.sizeX(),
                    v -> setConvenienceSettingFromPersistence(QuickBuildUiConvenienceParameter.SIZE_X, v)),
            PersistableProperty.intField(
                    "quick_build_repeat_size_y",
                    state -> state.quickBuild.mining.repeatSizeY,
                    (state, v) -> state.quickBuild.mining.repeatSizeY = v,
                    () -> this.convenienceSettings.sizeY(),
                    v -> setConvenienceSettingFromPersistence(QuickBuildUiConvenienceParameter.SIZE_Y, v)),
            PersistableProperty.intField(
                    "quick_build_repeat_size_z",
                    state -> state.quickBuild.mining.repeatSizeZ,
                    (state, v) -> state.quickBuild.mining.repeatSizeZ = v,
                    () -> this.convenienceSettings.sizeZ(),
                    v -> setConvenienceSettingFromPersistence(QuickBuildUiConvenienceParameter.SIZE_Z, v)),
            PersistableProperty.intField(
                    "quick_build_chunk_up",
                    state -> state.quickBuild.mining.chunkUp,
                    (state, v) -> state.quickBuild.mining.chunkUp = v,
                    () -> this.convenienceSettings.chunkUp(),
                    v -> setConvenienceSettingFromPersistence(QuickBuildUiConvenienceParameter.CHUNK_UP, v)),
            PersistableProperty.intField(
                    "quick_build_chunk_down",
                    state -> state.quickBuild.mining.chunkDown,
                    (state, v) -> state.quickBuild.mining.chunkDown = v,
                    () -> this.convenienceSettings.chunkDown(),
                    v -> setConvenienceSettingFromPersistence(QuickBuildUiConvenienceParameter.CHUNK_DOWN, v)),
            PersistableProperty.intField(
                    "quick_build_tree_max_blocks",
                    state -> state.quickBuild.mining.treeMaxBlocks,
                    (state, v) -> state.quickBuild.mining.treeMaxBlocks = v,
                    () -> this.convenienceSettings.treeMaxBlocks(),
                    v -> setConvenienceSettingFromPersistence(
                            QuickBuildUiConvenienceParameter.TREE_MAX_BLOCKS, v)),
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
        createConvenienceControls();
        createSmartFillControls();
        createShapeButtons();
        this.lastFillShape = controller.getBuildShape();
        this.lastAreaMineShape = this.rangeDestroyShape;
    }

    private void createConvenienceControls() {
        for (int i = 0; i < this.catalogButtons.length; i++) {
            QuickBuildUiCatalogPage page = QuickBuildUiCatalogPage.values()[i];
            this.catalogButtons[i] = new WindowButton(
                    0, 0, 60, QuickBuildWindowLayout.CATALOG_H,
                    Component.translatable(page == QuickBuildUiCatalogPage.SHAPES
                            ? "screen.rtsbuilding.quick_build.catalog_shapes"
                            : "screen.rtsbuilding.quick_build.catalog_tools"),
                    ignored -> dispatchCore(QuickBuildUiAction.catalog(page)));
        }
        for (int i = 0; i < this.convenienceToolButtons.length; i++) {
            QuickBuildUiConvenienceTool tool = QuickBuildUiConvenienceTool.values()[i];
            this.convenienceToolButtons[i] = new WindowButton(
                    0, 0,
                    QuickBuildWindowLayout.CONVENIENCE_TOOL_W,
                    QuickBuildWindowLayout.CONVENIENCE_TOOL_H,
                    Component.empty(),
                    ignored -> dispatchCore(QuickBuildUiAction.convenienceTool(tool)));
        }
        for (QuickBuildUiConvenienceParameter parameter : QuickBuildUiConvenienceParameter.values()) {
            int[] range = convenienceParameterRange(parameter);
            WindowSlider slider = new WindowSlider(
                    0, 0,
                    QuickBuildWindowLayout.chainSliderWidth(QuickBuildWindowLayout.WINDOW_W),
                    QuickBuildWindowLayout.CHAIN_SLIDER_H,
                    range[0], range[1], this.convenienceSettings.value(parameter));
            slider.onChange(value -> {
                if (!this.syncingConvenienceSliders) {
                    dispatchCore(QuickBuildUiAction.convenienceParameter(parameter, value));
                }
            });
            this.convenienceSliders.put(parameter, slider);
        }
    }

    private void createSmartFillControls() {
        this.smartFillToolButton = new WindowButton(
                0, 0,
                QuickBuildWindowLayout.CONVENIENCE_TOOL_W,
                QuickBuildWindowLayout.CONVENIENCE_TOOL_H,
                Component.empty(),
                ignored -> dispatchCore(QuickBuildUiAction.catalog(
                        QuickBuildUiCatalogPage.CONVENIENCE_TOOLS)));
        int sliderWidth = QuickBuildWindowLayout.chainSliderWidth(QuickBuildWindowLayout.WINDOW_W);
        this.smartFillMaxBlocksSlider = new WindowSlider(
                0, 0, sliderWidth, QuickBuildWindowLayout.CHAIN_SLIDER_H,
                SmartFillLimits.MIN_BLOCKS, SmartFillLimits.MAX_BLOCKS,
                SmartFillLimits.DEFAULT_BLOCKS);
        this.smartFillMaxBlocksSlider.onChange(value -> {
            if (!this.syncingSmartFillSliders) {
                dispatchCore(QuickBuildUiAction.smartFillMaxBlocks(value));
            }
        });
        this.smartFillDiameterSlider = new WindowSlider(
                0, 0, sliderWidth, QuickBuildWindowLayout.CHAIN_SLIDER_H,
                SmartFillLimits.MIN_DIAMETER, SmartFillLimits.MAX_DIAMETER,
                SmartFillLimits.DEFAULT_DIAMETER);
        this.smartFillDiameterSlider.onChange(value -> {
            if (!this.syncingSmartFillSliders) {
                dispatchCore(QuickBuildUiAction.smartFillDiameter(value));
            }
        });
        syncSmartFillSettings();
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
        Identifier texture = currentShapeTexture(index);
        boolean selected = isCurrentShapeSelected(index);
        int normalV = selected ? SHAPE_STATE_H : 0;
        WindowButton button = new WindowButton(0, 0,
                QuickBuildWindowLayout.SHAPE_SLOT, QuickBuildWindowLayout.SHAPE_SLOT,
                Component.empty(),
                texture,
                0, normalV,
                SHAPE_SHEET_W, SHAPE_STATE_H,
                SHAPE_STATE_H, SHAPE_STATE_H,
                SHAPE_SHEET_W, SHAPE_SHEET_H,
                btn -> selectShape(index));
        button.setTextureTint(QuickBuildStyle.ICON_TINT);
        if (isDestroyModeActive()) {
            button.active = canUseDestroyShape(DESTROY_SHAPES[index]);
        }
        return button;
    }

    /** 当形状切换时刷新所有按钮贴图（选中/未选中状态）。 */
    private void rebuildAllShapeButtons() {
        createShapeButtons();
    }

    void rebuildFillModeButtons() {
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
                dispatchCore(QuickBuildUiAction.control(
                        QuickBuildUiControl.Id.valueOf(modes.get(idx).name())));
            });
        }
        // 连接模式按钮（LINE/WALL 形状时显示）
        if (controller.getBuildShape() == BuildShape.LINE || controller.getBuildShape() == BuildShape.WALL) {
            this.connectToggle = new WindowButton(0, 0, 84, 20,
                    Component.literal(screen.text("screen.rtsbuilding.quick_build.connect")), btn -> {
                // 直接读写模式对应的独立字段，避免经过 syncActiveToModeFields 中转
                dispatchCore(QuickBuildUiAction.control(QuickBuildUiControl.Id.CONNECT));
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
                dispatchCore(QuickBuildUiAction.control(QuickBuildUiControl.Id.VERTICAL));
            });
            fillModeButtons = next;
        }
        BuildShape advancedShape = activeAdvancedShape();
        if (supportsAdvancedShape(advancedShape)) {
            WindowButton[] next = Arrays.copyOf(fillModeButtons, fillModeButtons.length + 1);
            int advancedIndex = fillModeButtons.length;
            next[advancedIndex] = new WindowButton(0, 0, 84, 20,
                    Component.translatable("screen.rtsbuilding.quick_build.advanced_box"), btn -> {
                dispatchCore(QuickBuildUiAction.control(QuickBuildUiControl.Id.ADVANCED));
            });
            fillModeButtons = next;
        }
    }

    private int currentShapeCount() {
        return isDestroyModeActive() ? DESTROY_SHAPES.length : BUILD_SHAPES.length;
    }

    private Identifier currentShapeTexture(int index) {
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
            dispatchCore(QuickBuildUiAction.shape(
                    com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape.valueOf(
                            DESTROY_SHAPES[index].name())));
            return;
        }
        dispatchCore(QuickBuildUiAction.shape(
                com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape.valueOf(
                        BUILD_SHAPES[index].name())));
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

    QuickBuildUiCatalogPage getCatalogPage() {
        return this.catalogPage;
    }

    QuickBuildUiConvenienceTool getConvenienceTool() {
        return this.convenienceTool;
    }

    /** 顶部状态行显示当前实际会执行的便捷工具，而不是泛化的“工具”分类名。 */
    public String getConvenienceToolLabel() {
        String key = switch (this.convenienceTool) {
            case REPEAT_BOX -> "screen.rtsbuilding.quick_build.tool.repeat_box";
            case CHUNK_QUARRY -> "screen.rtsbuilding.quick_build.tool.chunk_quarry";
            case TREE_FELL -> "screen.rtsbuilding.quick_build.tool.tree_fell";
        };
        return this.screen == null ? "" : this.screen.text(key);
    }

    QuickBuildUiConvenienceSettings getConvenienceSettings() {
        return this.convenienceSettings;
    }

    void setCatalogPage(QuickBuildUiCatalogPage page) {
        this.catalogPage = page == null ? QuickBuildUiCatalogPage.SHAPES : page;
        this.conveniencePreviewPlanner.invalidate();
        if (isOpen()) {
            applyActiveShapeToController();
            screen.clearShapeBuildSession();
            controller.clearAreaMineSession();
        }
        screen.persistUiState();
    }

    void setConvenienceTool(QuickBuildUiConvenienceTool tool) {
        this.convenienceTool = tool == null ? QuickBuildUiConvenienceTool.REPEAT_BOX : tool;
        this.catalogPage = QuickBuildUiCatalogPage.CONVENIENCE_TOOLS;
        this.conveniencePreviewPlanner.invalidate();
        if (isOpen()) {
            applyActiveShapeToController();
            screen.clearShapeBuildSession();
            controller.clearAreaMineSession();
        }
        screen.persistUiState();
    }

    void setConvenienceParameter(QuickBuildUiConvenienceParameter parameter, int value) {
        if (parameter == null) {
            return;
        }
        this.convenienceSettings = this.convenienceSettings.with(parameter, value);
        syncConvenienceSliders();
        this.conveniencePreviewPlanner.invalidate();
        screen.persistUiState();
    }

    private void setConvenienceSettingFromPersistence(
            QuickBuildUiConvenienceParameter parameter, int value) {
        if (parameter != null) {
            this.convenienceSettings = this.convenienceSettings.with(parameter, value);
            this.conveniencePreviewPlanner.invalidate();
        }
    }

    public boolean isConvenienceDestroyMode() {
        return isDestroyModeActive()
                && this.catalogPage == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS;
    }

    public RtsConvenienceDestroyPlanner.Plan convenienceDestroyPreview() {
        if (!isConvenienceDestroyMode() || screen == null) {
            return invalidConveniencePlan();
        }
        return this.conveniencePreviewPlanner.preview(
                screen.getMinecraft(),
                commonConvenienceMode(),
                screen.pickBlockHit(),
                commonConvenienceSettings());
    }

    public com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords.GhostPreview
            convenienceGhostPreview() {
        RtsConvenienceDestroyPlanner.Plan plan = convenienceDestroyPreview();
        return plan.targets().isEmpty()
                ? com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords.GhostPreview.EMPTY
                : new com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords.GhostPreview(
                        plan.targets(), true, true, List.of());
    }

    public boolean submitConvenienceDestroy(BlockHitResult hit) {
        if (!isConvenienceDestroyMode() || hit == null || controller == null) {
            return false;
        }
        RtsConvenienceDestroyPlanner.Plan preview = this.conveniencePreviewPlanner.preview(
                screen.getMinecraft(), commonConvenienceMode(), hit, commonConvenienceSettings());
        if (preview.ready()) {
            controller.confirmConvenienceDestroy(
                    commonConvenienceMode(), hit, commonConvenienceSettings(), screen.getSelectedToolSlot());
        }
        this.conveniencePreviewPlanner.invalidate();
        return true;
    }

    public boolean submitConvenienceDestroy(BlockHitResult hit, RtsTraceInputKind inputKind) {
        return submitConvenienceDestroy(hit);
    }

    public boolean isCreativeOverwriteEnabled() {
        return isDestroyModeActive() && controller != null && controller.isLocalPlayerCreative();
    }

    String convenienceDimensionLabel() {
        return switch (this.convenienceTool) {
            case REPEAT_BOX -> this.convenienceSettings.sizeX() + "×"
                    + this.convenienceSettings.sizeY() + "×" + this.convenienceSettings.sizeZ();
            case CHUNK_QUARRY -> "16×" + (this.convenienceSettings.chunkUp()
                    + this.convenienceSettings.chunkDown() + 1) + "×16";
            case TREE_FELL -> "≤" + this.convenienceSettings.treeMaxBlocks();
        };
    }

    String convenienceHintKey() {
        RtsConvenienceDestroyPlanner.ResultCode code = convenienceDestroyPreview().code();
        if (code == RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT) {
            return "screen.rtsbuilding.quick_build.convenience.over_limit";
        }
        if (code == RtsConvenienceDestroyPlanner.ResultCode.UNLOADED_CHUNK) {
            return "screen.rtsbuilding.quick_build.convenience.unloaded";
        }
        if (code == RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET
                && this.convenienceTool == QuickBuildUiConvenienceTool.TREE_FELL) {
            return "screen.rtsbuilding.quick_build.convenience.tree_invalid";
        }
        return "screen.rtsbuilding.quick_build.convenience.hint";
    }

    private RtsConvenienceDestroyMode commonConvenienceMode() {
        return RtsConvenienceDestroyMode.valueOf(this.convenienceTool.name());
    }

    private RtsConvenienceDestroySettings commonConvenienceSettings() {
        return new RtsConvenienceDestroySettings(
                this.convenienceSettings.sizeX(),
                this.convenienceSettings.sizeY(),
                this.convenienceSettings.sizeZ(),
                this.convenienceSettings.chunkUp(),
                this.convenienceSettings.chunkDown(),
                this.convenienceSettings.treeMaxBlocks());
    }

    private static RtsConvenienceDestroyPlanner.Plan invalidConveniencePlan() {
        return new RtsConvenienceDestroyPlanner.Plan(
                RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET, List.of(), 0);
    }

    /** 是否正在使用先预览、再确认的智能填坑交互。 */
    public boolean isSmartFillMode() {
        return isOpen() && effectiveMode() == QuickBuildMode.SMART_FILL;
    }

    int getSmartFillMaxBlocks() {
        return this.smartFill.maxBlocks();
    }

    int getSmartFillDiameter() {
        return this.smartFill.diameter();
    }

    void setSmartFillMaxBlocks(int value) {
        this.smartFill.maxBlocks(Mth.clamp(
                value, SmartFillLimits.MIN_BLOCKS, SmartFillLimits.MAX_BLOCKS));
        syncSmartFillSettings();
        if (this.screen != null) {
            this.screen.persistUiState();
        }
    }

    void setSmartFillDiameter(int value) {
        this.smartFill.diameter(Mth.clamp(
                value, SmartFillLimits.MIN_DIAMETER, SmartFillLimits.MAX_DIAMETER));
        syncSmartFillSettings();
        if (this.screen != null) {
            this.screen.persistUiState();
        }
    }

    /** 返回与世界光标共享 planner 的即时幽灵预览。 */
    public com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords.GhostPreview
            smartFillGhostPreview() {
        if (!isSmartFillMode() || this.screen == null) {
            return com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords.GhostPreview.EMPTY;
        }
        return this.smartFill.preview(this.screen.getMinecraft(), this.screen.pickBlockHit());
    }

    SmartFillPlan smartFillPlan() {
        return this.smartFill.plan(
                this.screen == null ? null : this.screen.getMinecraft(),
                this.screen == null ? null : this.screen.pickBlockHit());
    }

    boolean isSmartFillAnchored() {
        return this.smartFill.anchored();
    }

    /** 首次点击锁定预览，第二次点击才提交声明式填坑意图。 */
    public boolean submitOrAnchorSmartFill(
            BlockHitResult hit,
            Vec3 rayOrigin,
            Vec3 rayDirection) {
        if (!isSmartFillMode() || this.controller == null || this.screen == null) {
            return false;
        }
        return this.smartFill.submitOrAnchor(
                this.screen.getMinecraft(),
                hit,
                rayOrigin,
                rayDirection,
                this.controller::confirmSmartFill);
    }

    /** Esc 只撤销本地第一次确认，不会取消已经提交的服务端任务。 */
    public boolean cancelSmartFillAnchor() {
        return isSmartFillMode() && this.smartFill.cancelAnchor();
    }

    private void syncSmartFillSettings() {
        if (this.smartFillMaxBlocksSlider == null || this.smartFillDiameterSlider == null) {
            return;
        }
        this.syncingSmartFillSliders = true;
        try {
            this.smartFillMaxBlocksSlider.setValue(this.smartFill.maxBlocks());
            this.smartFillDiameterSlider.setValue(this.smartFill.diameter());
        } finally {
            this.syncingSmartFillSliders = false;
        }
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
        int sliderW = QuickBuildWindowLayout.chainSliderWidth(QuickBuildWindowLayout.WINDOW_W);
        this.chainLimitSlider = new WindowSlider(0, 0, sliderW,
                QuickBuildWindowLayout.CHAIN_SLIDER_H,
                ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT, this.chainDestroyLimit);
        this.chainLimitSlider.onChange(value ->
                dispatchCore(QuickBuildUiAction.limit(value)));
    }

    private static int sanitizeChainLimit(int value) {
        return Mth.clamp(value, ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT);
    }

    // ======================== 渲染 ========================

    /** 固定使用 144×288 的 Quick Build Kit；不同模式只切换内容，不再重排窗口高度。 */
    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        this.windowWidth = QuickBuildWindowLayout.WINDOW_W;
        this.windowHeight = QuickBuildWindowLayout.windowHeight(core.mode);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderOverlays(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!this.open || !canShowWindow() || areChildControlsSuppressed()) return;
        renderShapeTooltip(g, mouseX, mouseY);
    }

    private void renderShapeTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        QuickBuildWindowLayout.Geometry layout = QuickBuildWindowLayout.geometry(
                this.windowX, this.windowY, QuickBuildUiAdapter.snapshot(this).mode);
        for (int i = 0; i < this.catalogButtons.length; i++) {
            UiRect bounds = new UiRect(layout.catalogX(i), layout.catalogY,
                    layout.catalogW, QuickBuildWindowLayout.CATALOG_H);
            if (bounds.contains(mouseX, mouseY)) {
                String key = i == 0 ? "screen.rtsbuilding.quick_build.catalog_shapes"
                        : "screen.rtsbuilding.quick_build.catalog_tools";
                g.setTooltipForNextFrame(screen.font(), Component.translatable(key),
                        screen.toNativeGuiCoordinate(mouseX), screen.toNativeGuiCoordinate(mouseY));
                return;
            }
        }
        if (isSmartFillMode()) {
            UiRect bounds = new UiRect(layout.convenienceToolX(0), layout.convenienceToolY(0),
                    QuickBuildWindowLayout.CONVENIENCE_TOOL_W,
                    QuickBuildWindowLayout.CONVENIENCE_TOOL_H);
            if (bounds.contains(mouseX, mouseY)) {
                g.setTooltipForNextFrame(screen.font(), Component.translatable(
                                "screen.rtsbuilding.quick_build.smart_fill.detail"),
                        screen.toNativeGuiCoordinate(mouseX), screen.toNativeGuiCoordinate(mouseY));
            }
            return;
        }
        if (isConvenienceDestroyMode()) {
            for (int i = 0; i < this.convenienceToolButtons.length; i++) {
                if (convenienceToolBounds(layout, i).contains(mouseX, mouseY)) {
                    g.setTooltipForNextFrame(screen.font(), Component.translatable(
                                    convenienceToolDetailKey(QuickBuildUiConvenienceTool.values()[i])),
                            screen.toNativeGuiCoordinate(mouseX), screen.toNativeGuiCoordinate(mouseY));
                    return;
                }
            }
            return;
        }
        for (int i = 0; i < shapeButtons.length; i++) {
            WindowButton btn = shapeButtons[i];
            if (mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth()
                    && mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                g .setTooltipForNextFrame(screen.font(), Component.translatable(currentShapeTooltipKey(i)),
                        screen.toNativeGuiCoordinate(mouseX), screen.toNativeGuiCoordinate(mouseY));
                break;
            }
        }
    }

    private void renderModeToggles(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        UiRect buildBounds = modeBounds(QuickBuildMode.BUILD);
        UiRect destroyBounds = modeBounds(QuickBuildMode.DESTROY);
        renderModeToggle(g, buildBounds, QuickBuildMode.BUILD,
                Component.translatable("screen.rtsbuilding.quick_build.mode_build"), mouseX, mouseY);
        renderModeToggle(g, destroyBounds, QuickBuildMode.DESTROY,
                Component.translatable("screen.rtsbuilding.quick_build.mode_destroy"), mouseX, mouseY);
    }

    /** 同一矩形同时提供给绘制和点击，避免高 RTS 缩放下看得见却点不到。 */
    private UiRect modeBounds(QuickBuildMode mode) {
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        QuickBuildWindowLayout.Geometry layout = QuickBuildWindowLayout.geometry(
                this.windowX, this.windowY, core.mode);
        return layout.modeArea(mode == QuickBuildMode.DESTROY
                ? QuickBuildUiMode.DESTROY : QuickBuildUiMode.BUILD);
    }

    private void renderModeToggle(GuiGraphicsExtractor g, UiRect bounds, QuickBuildMode mode,
            Component label, int mouseX, int mouseY) {
        boolean enabled = mode != QuickBuildMode.DESTROY || canUseRangeDestroy();
        boolean active = enabled && (this.quickBuildMode == mode
                || (mode == QuickBuildMode.BUILD
                && this.quickBuildMode == QuickBuildMode.SMART_FILL));
        boolean hovered = !areChildControlsSuppressed() && bounds.contains(mouseX, mouseY);
        UiControlAnimationState.Snapshot animation = animateContentControl(
                "quick-build-mode-" + mode.name(), enabled, hovered, active);
        QuickBuildStyle.ModeVisual visual = QuickBuildStyle.animatedMode(animation);
        QuickBuildChromeRenderer.renderMode(
                new MinecraftUiCanvas(g, screen.font(), screen, visualOpacity()),
                bounds, visual, animation.selection());
        int x = (int) bounds.getX();
        int y = (int) bounds.getY();
        int w = (int) bounds.getWidth();
        int labelX = x + Math.max(2, (w - screen.font().width(label)) / 2);
        int labelY = y + (MODE_TOGGLE_H - screen.font().lineHeight) / 2;
        g.text(screen.font(), label, labelX, labelY,
                withVisualOpacity(visual.text.toArgb()), false);
    }

    /**
     * 只保存 Quick Build 固定控件的视觉过渡，业务模式仍在点击帧同步写入控制器。
     * 这使渲染和命中共用 modeBounds，却不会把动画变成输入或远程操作的前置条件。
     */
    @Override
    protected UiControlAnimationState.Snapshot animateContentControl(
            String id, boolean enabled, boolean hovered, boolean selected) {
        return this.contentAnimations.update(id,
                new UiControlState(true, enabled, hovered, false, false, selected,
                        false, false, enabled ? "" : "disabled"),
                Config.isUiAnimationsEnabled());
    }

    /**
     * Legacy Direct 保留资源包给出的原像素；Palette 只叠加由统一语义色计算的轻量渐变，
     * 不新建图集、不改变 UV，也不参与点击判定。
     */
    private void renderPaletteIconGradient(
            GuiGraphicsExtractor g, UiRect bounds, String animationId,
            boolean enabled, boolean hovered, boolean selected) {
        if (UiThemeRuntime.manager().active().renderMode() == UiThemeRenderMode.LEGACY_DIRECT) {
            return;
        }
        UiControlAnimationState.Snapshot animation = animateContentControl(
                animationId, enabled, hovered, selected);
        QuickBuildChromeRenderer.renderIconGradientOverlay(
                new MinecraftUiCanvas(g, screen.font(), screen, visualOpacity()),
                bounds, QuickBuildStyle.animatedControlIndicator(animation));
    }

    /** 父窗口淡入、淡出或被覆盖时，装饰图标也必须和真实按钮一样停止悬停反馈。 */
    private boolean contentHovered(WindowButton button) {
        return button != null && !areChildControlsSuppressed() && button.isHoveredOrFocused();
    }

    private void renderProgressStrip(
            GuiGraphicsExtractor g,
            QuickBuildWindowLayout.Geometry layout) {
        int barX = (int) layout.progress.getX();
        int barY = (int) layout.progress.getY();
        int barW = (int) layout.progress.getWidth();
        int barH = (int) layout.progress.getHeight();
        g.fill(barX, barY, barX + barW, barY + barH,
                withVisualOpacity(QuickBuildStyle.PROGRESS_TRACK.toArgb()));
        RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
        int processed = workflow != null ? workflow.completedBlocks() : -1;
        int total = workflow != null ? workflow.totalBlocks() : 0;
        if (processed >= 0 && total > 0) {
            int filled = Math.min(barW, Math.round(barW * (processed / (float) total)));
            g.fill(barX, barY, barX + filled, barY + barH,
                    withVisualOpacity(QuickBuildStyle.PROGRESS_FILL.toArgb()));
        } else {
            g.fill(barX, barY, barX + 1, barY + barH,
                    withVisualOpacity(QuickBuildStyle.PROGRESS_IDLE_TICK.toArgb()));
        }
    }

    /** 渲染破坏页二级目录；同一套边界也由鼠标命中处理使用。 */
    private void renderCatalogControls(GuiGraphicsExtractor g,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < this.catalogButtons.length; i++) {
            QuickBuildUiCatalogPage page = QuickBuildUiCatalogPage.values()[i];
            int buttonX = layout.catalogX(i);
            int buttonY = layout.catalogY;
            this.catalogButtons[i].setX(buttonX);
            this.catalogButtons[i].setY(buttonY);
            this.catalogButtons[i].setWidth(layout.catalogW);
            boolean selected = (isSmartFillMode()
                    && page == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS)
                    || (!isSmartFillMode() && (isDestroyModeActive()
                    ? this.catalogPage == page
                    : page == QuickBuildUiCatalogPage.SHAPES));
            if (selected) {
                g.fill(buttonX, buttonY, buttonX + layout.catalogW,
                        buttonY + QuickBuildWindowLayout.CATALOG_H,
                        withVisualOpacity(QuickBuildStyle.CHAIN_SELECTED_BACKGROUND.toArgb()));
            }
            this.catalogButtons[i].render(g, mouseX, mouseY, partialTick);
        }
    }

    /** 渲染可实际提交的 Repeat、Chunk 和 Tree 工具及其当前工具的参数。 */
    private void renderConvenienceControls(GuiGraphicsExtractor g,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < this.convenienceToolButtons.length; i++) {
            QuickBuildUiConvenienceTool tool = QuickBuildUiConvenienceTool.values()[i];
            UiRect bounds = convenienceToolBounds(layout, i);
            int toolX = (int) bounds.getX();
            int toolY = (int) bounds.getY();
            this.convenienceToolButtons[i].setX(toolX);
            this.convenienceToolButtons[i].setY(toolY);
            boolean selected = this.convenienceTool == tool;
            this.convenienceToolButtons[i].applyControlState(
                    RtsControlState.enabled(RtsControlRole.TOGGLE).withSelected(selected));
            this.convenienceToolButtons[i].render(g, mouseX, mouseY, partialTick);
            renderPaletteIconGradient(g, bounds, "quick-build-tool-" + tool.name(),
                    true, contentHovered(this.convenienceToolButtons[i]), selected);
            RtsTextureRenderer.drawTextureHighPrecision(g, CONVENIENCE_TOOL_TEXTURES[i],
                    toolX + 1, toolY + 1,
                    QuickBuildWindowLayout.CONVENIENCE_TOOL_ICON_SIZE,
                    QuickBuildWindowLayout.CONVENIENCE_TOOL_ICON_SIZE,
                    0, 0, 24, 24, 24, 24, 0,
                    withVisualOpacity(RtsTextureRenderer.NO_TINT));
        }

        int sliderWidth = QuickBuildWindowLayout.chainSliderWidth(this.windowWidth);
        List<QuickBuildUiConvenienceParameter> parameters = activeConvenienceParameters(this.convenienceTool);
        syncConvenienceSliders();
        for (int i = 0; i < parameters.size(); i++) {
            QuickBuildUiConvenienceParameter parameter = parameters.get(i);
            int labelY = layout.convenienceParameterLabelY(i);
            String label = screen.text(convenienceParameterKey(parameter))
                    + ": " + this.convenienceSettings.value(parameter);
            String trimmed = screen.font().plainSubstrByWidth(
                    label, Math.max(1, layout.rightX - layout.contentX - 3));
            g.text(screen.font(), trimmed, layout.contentX, labelY,
                    withVisualOpacity(QuickBuildStyle.SECTION_TEXT.toArgb()), false);
            WindowSlider slider = this.convenienceSliders.get(parameter);
            slider.setVisible(true);
            slider.setWidth(sliderWidth);
            slider.setX(layout.rightX);
            slider.setY(layout.convenienceParameterSliderY(i));
            slider.render(g, mouseX, mouseY, partialTick);
        }
        for (QuickBuildUiConvenienceParameter parameter : QuickBuildUiConvenienceParameter.values()) {
            if (!parameters.contains(parameter)) {
                this.convenienceSliders.get(parameter).setVisible(false);
            }
        }
    }

    private void renderConvenienceBottomInfo(GuiGraphicsExtractor g,
            QuickBuildWindowLayout.Geometry layout) {
        g.fill((int) layout.divider.getX(), (int) layout.divider.getY(),
                (int) layout.divider.right(), (int) layout.divider.bottom(),
                withVisualOpacity(QuickBuildStyle.DIVIDER.toArgb()));
        renderProgressStrip(g, layout);
        int textY = layout.statusTextY;
        RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
        if (workflow != null) {
            String fullText = workflow.progressText() + "    "
                    + screen.text("screen.rtsbuilding.quick_build.destroy_remaining", workflow.remainingBlocks());
            g.text(screen.font(), fullText, layout.contentX, textY,
                    withVisualOpacity(QuickBuildStyle.SUCCESS_TEXT.toArgb()), false);
        } else {
            int nextY = renderBottomInfoText(g,
                    Component.translatable(convenienceHintKey()),
                    layout.contentX, textY, layout.contentW,
                    QuickBuildStyle.HINT_TEXT.toArgb());
            textY = nextY + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP;
        }
        Component dimensions = Component.translatable(
                "screen.rtsbuilding.quick_build.dimensions", convenienceDimensionLabel());
        String trimmed = screen.font().plainSubstrByWidth(dimensions.getString(), Math.max(1, layout.contentW));
        g.text(screen.font(), trimmed, layout.contentX,
                workflow == null ? textY : textY + screen.font().lineHeight
                        + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP,
                withVisualOpacity(QuickBuildStyle.DIMENSION_TEXT.toArgb()), false);
    }

    /** 渲染智能填坑的入口、两个范围参数和当前两次确认状态。 */
    private void renderSmartFillControls(
            GuiGraphicsExtractor g,
            QuickBuildWindowLayout.Geometry layout,
            QuickBuildUiState core,
            int mouseX, int mouseY, float partialTick) {
        int toolX = layout.convenienceToolX(0);
        int toolY = layout.convenienceToolY(0);
        this.smartFillToolButton.setX(toolX);
        this.smartFillToolButton.setY(toolY);
        this.smartFillToolButton.applyControlState(
                RtsControlState.enabled(RtsControlRole.TOGGLE).withSelected(true));
        this.smartFillToolButton.render(g, mouseX, mouseY, partialTick);
        UiRect smartFillBounds = new UiRect(toolX, toolY,
                QuickBuildWindowLayout.CONVENIENCE_TOOL_W,
                QuickBuildWindowLayout.CONVENIENCE_TOOL_H);
        renderPaletteIconGradient(g, smartFillBounds, "quick-build-tool-smart-fill", true,
                contentHovered(this.smartFillToolButton), true);
        RtsTextureRenderer.drawTextureHighPrecision(g, SMART_FILL_TOOL_TEXTURE,
                toolX + 1, toolY + 1,
                QuickBuildWindowLayout.CONVENIENCE_TOOL_ICON_SIZE,
                QuickBuildWindowLayout.CONVENIENCE_TOOL_ICON_SIZE,
                0, 0, 24, 24, 24, 24, 0,
                withVisualOpacity(RtsTextureRenderer.NO_TINT));

        int sliderWidth = QuickBuildWindowLayout.chainSliderWidth(this.windowWidth);
        syncSmartFillSettings();
        this.smartFillMaxBlocksSlider.setVisible(true);
        this.smartFillMaxBlocksSlider.setWidth(sliderWidth);
        this.smartFillMaxBlocksSlider.setX(layout.rightX);
        this.smartFillMaxBlocksSlider.setY(layout.smartFillParameterSliderY(0));
        g.text(screen.font(), Component.translatable(
                        "screen.rtsbuilding.quick_build.smart_fill.max_blocks"),
                layout.rightX, layout.smartFillParameterLabelY(0),
                withVisualOpacity(QuickBuildStyle.SECTION_TEXT.toArgb()), false);
        this.smartFillMaxBlocksSlider.render(g, mouseX, mouseY, partialTick);
        g.text(screen.font(), Integer.toString(core.smartFillMaxBlocks),
                layout.chainValueX(sliderWidth),
                layout.smartFillParameterSliderY(0) + QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET,
                withVisualOpacity(QuickBuildStyle.VALUE_TEXT.toArgb()), false);

        this.smartFillDiameterSlider.setVisible(true);
        this.smartFillDiameterSlider.setWidth(sliderWidth);
        this.smartFillDiameterSlider.setX(layout.rightX);
        this.smartFillDiameterSlider.setY(layout.smartFillParameterSliderY(1));
        g.text(screen.font(), Component.translatable(
                        "screen.rtsbuilding.quick_build.smart_fill.diameter"),
                layout.rightX, layout.smartFillParameterLabelY(1),
                withVisualOpacity(QuickBuildStyle.SECTION_TEXT.toArgb()), false);
        this.smartFillDiameterSlider.render(g, mouseX, mouseY, partialTick);
        g.text(screen.font(), Integer.toString(core.smartFillDiameter),
                layout.chainValueX(sliderWidth),
                layout.smartFillParameterSliderY(1) + QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET,
                withVisualOpacity(QuickBuildStyle.VALUE_TEXT.toArgb()), false);
    }

    private void renderSmartFillBottomInfo(
            GuiGraphicsExtractor g,
            QuickBuildWindowLayout.Geometry layout,
            QuickBuildUiState core) {
        g.fill((int) layout.divider.getX(), (int) layout.divider.getY(),
                (int) layout.divider.right(), (int) layout.divider.bottom(),
                withVisualOpacity(QuickBuildStyle.DIVIDER.toArgb()));
        int nextY = renderBottomInfoText(g, Component.translatable(core.hintKey, core.confirmKeyLabel),
                layout.contentX, layout.statusTextY, layout.contentW,
                core.smartFillAnchored
                        ? QuickBuildStyle.SUCCESS_TEXT.toArgb()
                        : QuickBuildStyle.HINT_TEXT.toArgb());
        String diameter = screen.text(
                "screen.rtsbuilding.quick_build.smart_fill.diameter_status",
                core.smartFillDiameter);
        String detail = core.smartFillTargetCount > 0
                ? diameter + " / " + core.smartFillTargetCount
                : diameter;
        g.text(screen.font(), screen.font().plainSubstrByWidth(detail, layout.contentW),
                layout.contentX, nextY + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP,
                withVisualOpacity(QuickBuildStyle.DIMENSION_TEXT.toArgb()), false);
    }

    private UiRect convenienceToolBounds(QuickBuildWindowLayout.Geometry layout, int index) {
        return new UiRect(layout.convenienceToolX(index), layout.convenienceToolY(index),
                QuickBuildWindowLayout.CONVENIENCE_TOOL_W,
                QuickBuildWindowLayout.CONVENIENCE_TOOL_H);
    }

    private static List<QuickBuildUiConvenienceParameter> activeConvenienceParameters(
            QuickBuildUiConvenienceTool tool) {
        return switch (tool == null ? QuickBuildUiConvenienceTool.REPEAT_BOX : tool) {
            case REPEAT_BOX -> List.of(
                    QuickBuildUiConvenienceParameter.SIZE_X,
                    QuickBuildUiConvenienceParameter.SIZE_Y,
                    QuickBuildUiConvenienceParameter.SIZE_Z);
            case CHUNK_QUARRY -> List.of(
                    QuickBuildUiConvenienceParameter.CHUNK_UP,
                    QuickBuildUiConvenienceParameter.CHUNK_DOWN);
            case TREE_FELL -> List.of(QuickBuildUiConvenienceParameter.TREE_MAX_BLOCKS);
        };
    }

    private static int[] convenienceParameterRange(QuickBuildUiConvenienceParameter parameter) {
        return switch (parameter) {
            case SIZE_X, SIZE_Z -> new int[] {
                    QuickBuildUiConvenienceSettings.BOX_MIN,
                    QuickBuildUiConvenienceSettings.BOX_MAX };
            case SIZE_Y -> new int[] {
                    QuickBuildUiConvenienceSettings.BOX_MIN,
                    QuickBuildUiConvenienceSettings.HEIGHT_MAX };
            case CHUNK_UP, CHUNK_DOWN -> new int[] {0,
                    QuickBuildUiConvenienceSettings.HEIGHT_MAX};
            case TREE_MAX_BLOCKS -> new int[] {
                    QuickBuildUiConvenienceSettings.TREE_MIN,
                    QuickBuildUiConvenienceSettings.TREE_MAX };
        };
    }

    private void syncConvenienceSliders() {
        this.syncingConvenienceSliders = true;
        try {
            for (QuickBuildUiConvenienceParameter parameter : QuickBuildUiConvenienceParameter.values()) {
                WindowSlider slider = this.convenienceSliders.get(parameter);
                if (slider != null) {
                    slider.setValue(this.convenienceSettings.value(parameter));
                }
            }
        } finally {
            this.syncingConvenienceSliders = false;
        }
    }

    private static String convenienceParameterKey(QuickBuildUiConvenienceParameter parameter) {
        return switch (parameter) {
            case SIZE_X -> "screen.rtsbuilding.quick_build.parameter.size_x";
            case SIZE_Y -> "screen.rtsbuilding.quick_build.parameter.size_y";
            case SIZE_Z -> "screen.rtsbuilding.quick_build.parameter.size_z";
            case CHUNK_UP -> "screen.rtsbuilding.quick_build.parameter.chunk_up";
            case CHUNK_DOWN -> "screen.rtsbuilding.quick_build.parameter.chunk_down";
            case TREE_MAX_BLOCKS -> "screen.rtsbuilding.quick_build.parameter.tree_max_blocks";
        };
    }

    private static String convenienceToolDetailKey(QuickBuildUiConvenienceTool tool) {
        return switch (tool) {
            case REPEAT_BOX -> "screen.rtsbuilding.quick_build.tool.repeat_box.detail";
            case CHUNK_QUARRY -> "screen.rtsbuilding.quick_build.tool.chunk_quarry.detail";
            case TREE_FELL -> "screen.rtsbuilding.quick_build.tool.tree_fell.detail";
        };
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        QuickBuildWindowLayout.Geometry layout = QuickBuildWindowLayout.geometry(
                this.windowX, this.windowY, core.mode);
        int x = this.windowX;
        int y = this.windowY;
        renderModeToggles(g, mouseX, mouseY);
        renderCatalogControls(g, layout, mouseX, mouseY, partialTick);
        if (isSmartFillMode()) {
            renderSmartFillControls(g, layout, core, mouseX, mouseY, partialTick);
            renderSmartFillBottomInfo(g, layout, core);
            return;
        }
        if (isConvenienceDestroyMode()) {
            renderConvenienceControls(g, layout, mouseX, mouseY, partialTick);
            renderConvenienceBottomInfo(g, layout);
            return;
        }
        int shapeTitleY = layout.sectionTitleY;

        // --- 形状模式 ---

        // --- 形状按钮 ---
        for (int i = 0; i < shapeButtons.length; i++) {
            int slotX = layout.shapeX(i);
            int slotY = layout.shapeY(i);
            shapeButtons[i].setX(slotX);
            shapeButtons[i].setY(slotY);
            shapeButtons[i].active = !isDestroyModeActive() || canUseDestroyShape(DESTROY_SHAPES[i]);
            boolean selected = isCurrentShapeSelected(i);
            shapeButtons[i].render(g, mouseX, mouseY, partialTick);
            renderPaletteIconGradient(g, new UiRect(slotX, slotY,
                    QuickBuildWindowLayout.SHAPE_SLOT, QuickBuildWindowLayout.SHAPE_SLOT),
                    "quick-build-shape-" + i, shapeButtons[i].active,
                    contentHovered(shapeButtons[i]), selected);
        }

        // --- 填充模式 ---
        int rightX = layout.rightX;

        if (isRangeDestroyChainMode()) {
            ensureChainLimitSlider();
            int labelY = layout.chainLabelY;
            g .text(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.chain_limit_label"),
                    rightX, labelY, withVisualOpacity(QuickBuildStyle.SECTION_TEXT.toArgb()), false);
            int sliderW = QuickBuildWindowLayout.chainSliderWidth(this.windowWidth);
            this.chainLimitSlider.setWidth(sliderW);
            this.chainLimitSlider.setX(rightX);
            this.chainLimitSlider.setY(layout.chainSliderY);
            this.chainLimitSlider.render(g, mouseX, mouseY, partialTick);
            // 显示当前值
            String valueStr = Integer.toString(this.chainDestroyLimit);
            g.text(screen.font(), valueStr, layout.chainValueX(sliderW),
                    layout.chainSliderY + QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET,
                    withVisualOpacity(QuickBuildStyle.VALUE_TEXT.toArgb()), false);
        } else if (fillModeButtons == null || controller.getBuildShape() != lastFillShape
                || (isDestroyModeActive() && this.rangeDestroyShape != this.lastAreaMineShape)) {
            rebuildFillModeButtons();
        }
        List<ShapeFillMode> modes =
                ShapeGeometryUtil.availableFillModes(controller.getBuildShape());
        for (int i = 0; fillModeButtons != null && i < fillModeButtons.length; i++) {
            int rowY = layout.controlY(i);
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
            boolean hovered = contentHovered(fillModeButtons[i]);
            int vOffset = selected ? MODE_BUTTON_STATE_H * 2 : (hovered ? MODE_BUTTON_STATE_H : 0);
            RtsTextureRenderer.drawTextureHighPrecision(
                    g, SELECTION_DOT_TEXTURE,
                    rightX + 2, rowY + 2, 16, 16,
                    0, vOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                    MODE_BUTTON_SHEET_W, MODE_BUTTON_H,
                    0, withVisualOpacity(RtsTextureRenderer.NO_TINT)
            );
        }

        // --- 连接模式按钮（LINE/WALL 形状时在填充模式下方显示） ---
        if (this.connectToggle != null) {
            int connectRowY = layout.controlY(
                    fillModeButtons == null ? modes.size() : fillModeButtons.length);
            this.connectToggle.setX(rightX);
            this.connectToggle.setY(connectRowY);
            this.connectToggle.render(g, mouseX, mouseY, partialTick);

            boolean connected = isDestroyModeActive()
                    ? screen.getShapeController().isDestroyLineConnected()
                    : screen.getShapeController().isBuildLineConnected();
            boolean hovered = contentHovered(this.connectToggle);
            int vOffset = connected ? MODE_BUTTON_STATE_H * 2 : (hovered ? MODE_BUTTON_STATE_H : 0);
            RtsTextureRenderer.drawTextureHighPrecision(
                    g, SELECTION_DOT_TEXTURE,
                    rightX + 2, connectRowY + 2, 16, 16,
                    0, vOffset, MODE_BUTTON_SHEET_W, MODE_BUTTON_STATE_H,
                    MODE_BUTTON_SHEET_W, MODE_BUTTON_H,
                    0, withVisualOpacity(RtsTextureRenderer.NO_TINT)
            );
        }

        // --- 底部提示文字（仅在选中物品时显示，使用面板扩展区域） ---
        {
            // 分界线
            int dividerY = layout.dividerY;
            g.fill((int) layout.divider.getX(), (int) layout.divider.getY(),
                    (int) layout.divider.right(), (int) layout.divider.bottom(),
                    withVisualOpacity(QuickBuildStyle.DIVIDER.toArgb()));
            renderProgressStrip(g, layout);

            // 扩展区域中心线
            int textY = layout.statusTextY;
            int itemY = layout.statusItemY;

            if (effectiveMode() == QuickBuildMode.DESTROY) {
                RtsWorkflowStatus workflow = this.controller.findActiveDestroyWorkflow();
                if (workflow != null) {
                    String fullText = workflow.progressText() + "    "
                            + screen.text("screen.rtsbuilding.quick_build.destroy_remaining", workflow.remainingBlocks());
                    g.text(screen.font(), fullText, layout.contentX, textY,
                            withVisualOpacity(QuickBuildStyle.SUCCESS_TEXT.toArgb()), false);
                    renderDimensionInfo(g, layout.contentX,
                            textY + screen.font().lineHeight + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP,
                            layout.contentW);
                } else {
                    int nextY = renderBottomInfoText(g,
                            Component.translatable(core.hintKey, core.confirmKeyLabel),
                            layout.contentX, textY, layout.contentW,
                            QuickBuildStyle.ERROR_TEXT.toArgb());
                    renderDimensionInfo(g, layout.contentX,
                            nextY + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP, layout.contentW);
                }
                return;
            }

            String costText = "x " + screen.currentShapeCostText();
            int textWidth = screen.font().width(costText);
            g.text(screen.font(), costText, layout.contentX, textY,
                    withVisualOpacity(QuickBuildStyle.SUCCESS_TEXT.toArgb()), false);

            // 渲染所选方块的物品图标，同时记录右侧边界
            ItemStack preview = resolveShapeBuildItem();
            int rightEdge = layout.contentX + textWidth;
            if (!preview.isEmpty()) {
                int itemX = layout.contentX + textWidth + QuickBuildWindowLayout.ITEM_GAP;
                g .item(preview, itemX, itemY);
                // 立即 flush 物品渲染，确保在 scissor 仍生效时提交到帧缓冲区
                g.nextStratum();
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
                            int missTextX = layout.missingTextX(rightEdge);
                            g.text(screen.font(), missText, missTextX, textY,
                                    withVisualOpacity(QuickBuildStyle.ERROR_TEXT.toArgb()), false);

                            if (!preview.isEmpty()) {
                                int missIconX = layout.missingIconX(
                                        missTextX, screen.font().width(missText));
                                g .item(preview, missIconX, itemY);
                                g.nextStratum();
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            int nextY = renderBottomInfoText(g,
                    Component.translatable(core.hintKey, core.confirmKeyLabel),
                    layout.contentX,
                    textY + screen.font().lineHeight + 3,
                    layout.contentW,
                    QuickBuildStyle.HINT_TEXT.toArgb());
            renderDimensionInfo(g, layout.contentX,
                    nextY + QuickBuildWindowLayout.INFO_FOLLOWUP_GAP, layout.contentW);
        }
    }

    private int renderBottomInfoText(GuiGraphicsExtractor g, Component text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = screen.font().split(text, Math.max(1, maxWidth));
        int lineCount = Math.min(BOTTOM_TEXT_MAX_LINES, lines.size());
        for (int i = 0; i < lineCount; i++) {
            g .text(screen.font(), lines.get(i), x, y + i * screen.font().lineHeight,
                    withVisualOpacity(color), false);
        }
        return y + lineCount * screen.font().lineHeight;
    }

    private void renderDimensionInfo(GuiGraphicsExtractor g, int x, int y, int maxWidth) {
        Component text = Component.translatable(
                "screen.rtsbuilding.quick_build.dimensions",
                screen.currentShapeSizeText());
        String trimmed = screen.font().plainSubstrByWidth(text.getString(), Math.max(1, maxWidth));
        g.text(screen.font(), trimmed, x, y,
                withVisualOpacity(QuickBuildStyle.DIMENSION_TEXT.toArgb()), false);
    }

    String confirmKeyLabel(boolean destroyMode) {
        return (destroyMode ? ClientKeyMappings.CONFIRM_BATCH_DESTROY : ClientKeyMappings.CONFIRM_BATCH_PLACE)
                .getTranslatedKeyMessage()
                .getString();
    }

    // ======================== 输入处理 ========================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (handleModeToggleClick(mouseX, mouseY)) {
            return;
        }
        if (handleConvenienceContentClick(mouseX, mouseY, button)) {
            return;
        }
        if (this.chainLimitSlider != null && isRangeDestroyChainMode()) {
            if (this.chainLimitSlider.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
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

    private boolean handleConvenienceContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        QuickBuildWindowLayout.Geometry layout = QuickBuildWindowLayout.geometry(
                this.windowX, this.windowY, QuickBuildUiAdapter.snapshot(this).mode);
        for (int i = 0; i < this.catalogButtons.length; i++) {
            UiRect bounds = new UiRect(layout.catalogX(i), layout.catalogY,
                    layout.catalogW, QuickBuildWindowLayout.CATALOG_H);
            if (bounds.contains(mouseX, mouseY)) {
                dispatchCore(QuickBuildUiAction.catalog(QuickBuildUiCatalogPage.values()[i]));
                return true;
            }
        }
        if (isSmartFillMode()) {
            if (this.smartFillToolButton != null
                    && this.smartFillToolButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.smartFillMaxBlocksSlider != null
                    && this.smartFillMaxBlocksSlider.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.smartFillDiameterSlider != null
                    && this.smartFillDiameterSlider.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            // 智能填坑没有形状按钮；吞掉内容区点击，避免隐藏的 BUILD 控件收到输入。
            return true;
        }
        if (!isConvenienceDestroyMode()) {
            return false;
        }
        for (int i = 0; i < this.convenienceToolButtons.length; i++) {
            if (convenienceToolBounds(layout, i).contains(mouseX, mouseY)) {
                dispatchCore(QuickBuildUiAction.convenienceTool(QuickBuildUiConvenienceTool.values()[i]));
                return true;
            }
        }
        for (QuickBuildUiConvenienceParameter parameter : activeConvenienceParameters(this.convenienceTool)) {
            WindowSlider slider = this.convenienceSliders.get(parameter);
            if (slider != null && slider.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 父窗口正在淡入、淡出或被更高层窗口遮盖时，不能让已按下的滑杆继续改写设置。
        if (areChildControlsSuppressed()) {
            return isInsideWindow(mouseX, mouseY);
        }
        if (isSmartFillMode()) {
            return (this.smartFillMaxBlocksSlider != null
                    && this.smartFillMaxBlocksSlider.mouseDragged(mouseX, mouseY, button))
                    || (this.smartFillDiameterSlider != null
                    && this.smartFillDiameterSlider.mouseDragged(mouseX, mouseY, button));
        }
        if (isConvenienceDestroyMode()) {
            for (QuickBuildUiConvenienceParameter parameter : activeConvenienceParameters(this.convenienceTool)) {
                WindowSlider slider = this.convenienceSliders.get(parameter);
                if (slider != null && slider.mouseDragged(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        if (this.chainLimitSlider != null && isRangeDestroyChainMode()) {
            if (this.chainLimitSlider.mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean convenienceHandled = false;
        if (this.smartFillMaxBlocksSlider != null) {
            convenienceHandled |= this.smartFillMaxBlocksSlider.mouseReleased(mouseX, mouseY, button);
        }
        if (this.smartFillDiameterSlider != null) {
            convenienceHandled |= this.smartFillDiameterSlider.mouseReleased(mouseX, mouseY, button);
        }
        for (WindowSlider slider : this.convenienceSliders.values()) {
            convenienceHandled |= slider.mouseReleased(mouseX, mouseY, button);
        }
        if (this.chainLimitSlider != null) {
            this.chainLimitSlider.mouseReleased(mouseX, mouseY, button);
        }
        return convenienceHandled || super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleModeToggleClick(double mouseX, double mouseY) {
        if (modeBounds(QuickBuildMode.BUILD).contains(mouseX, mouseY)) {
            dispatchCore(QuickBuildUiAction.mode(QuickBuildUiMode.BUILD));
            return true;
        }
        if (modeBounds(QuickBuildMode.DESTROY).contains(mouseX, mouseY)) {
            dispatchCore(QuickBuildUiAction.mode(QuickBuildUiMode.DESTROY));
            return true;
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
        int y = QuickBuildWindowLayout.defaultY(TOP_H);
        int availableH = screen.getFloatingPanelAvailableHeight(y);
        if (availableH >= QUICK_BUILD_PANEL_MIN_H) {
            this.windowHeight = QUICK_BUILD_PANEL_H;
        }
        this.windowX = QuickBuildWindowLayout.defaultX(screen.width);
        this.windowY = y;
    }

    @Override
    protected boolean canShowWindow() {
        return super.canShowWindow() && screen != null && screen.canUseQuickBuild();
    }

    // ======================== 抽象方法实现 & API ========================

    @Override
    protected void onClose() {
        restoreSingleBlockCursor();
        this.conveniencePreviewPlanner.invalidate();
        this.smartFill.clear();
        if (screen != null) {
            screen.persistUiState();
        }
    }

    public QuickBuildMode getMode() {
        return this.quickBuildMode;
    }

    /**
     * 所有 Quick Build 生产输入先经过纯 Core reducer，再回到现有控制器与持久化入口。
     * 这不会复制 Build/Destroy 状态，也不会改变原有远程放置或批量破坏网络链。
     */
    private QuickBuildUiTransition dispatchCore(QuickBuildUiAction action) {
        QuickBuildUiTransition transition = QuickBuildUiReducer.apply(
                QuickBuildUiAdapter.snapshot(this), action);
        QuickBuildUiAdapter.apply(this, transition);
        return transition;
    }

    BuilderScreen uiScreen() {
        return this.screen;
    }

    ClientRtsController uiController() {
        return this.controller;
    }

    public void setMode(QuickBuildMode mode) {
        QuickBuildMode next = mode == null ? QuickBuildMode.BUILD : mode;
        if (next == QuickBuildMode.DESTROY && !canUseRangeDestroy()) {
            next = QuickBuildMode.BUILD;
        } else if (next == QuickBuildMode.DESTROY) {
            this.rangeDestroyShape = effectiveRangeDestroyShape();
        }
        if (this.quickBuildMode == next) {
            if (isOpen()) {
                applyActiveShapeToController();
            } else {
                restoreSingleBlockCursor();
            }
            return;
        }
        this.smartFill.clear();
        this.quickBuildMode = next;
        if (isOpen()) {
            // 切换模式时，将 ScreenShapeController 的活跃状态在 BUILD/DESTROY 独立字段间交换
            if (isDestroyModeActive()) {
                screen.getShapeController().switchToDestroy();
            } else {
                screen.getShapeController().switchToBuild();
            }
            applyActiveShapeToController();
            screen.clearShapeBuildSession();
            this.controller.clearAreaMineSession();
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
        return isRangeDestroyMode() && !isConvenienceDestroyMode()
                && effectiveRangeDestroyShape() == AreaMineShape.CHAIN;
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

    BuildShape activeAdvancedShape() {
        return isConvenienceDestroyMode() || isSmartFillMode() ? BuildShape.BLOCK
                : isDestroyModeActive() ? toBuildShape(effectiveRangeDestroyShape()) : this.buildModeShape;
    }

    static boolean supportsAdvancedShape(BuildShape shape) {
        return switch (shape == null ? BuildShape.BLOCK : shape) {
            case SQUARE, WALL, CIRCLE, CYLINDER, BALL, BOX -> true;
            case BLOCK, LINE -> false;
        };
    }

    static boolean supportsVerticalToggle(BuildShape shape) {
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

    boolean isAdvancedShape(BuildShape shape) {
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

    void setAdvancedShape(BuildShape shape, boolean value) {
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

    void setRoundShapeVertical(BuildShape shape, boolean value) {
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

    // ======================== 私有辅助方法 ========================

    /**
     * 是否显示底部提示文字。
     * 仅在玩家选中了可放置的方块物品时扩展面板并显示。
     */
    private int currentBasePanelHeight() {
        return isDestroyModeActive() ? QUICK_BUILD_DESTROY_PANEL_H : QUICK_BUILD_PANEL_H;
    }

    QuickBuildMode effectiveMode() {
        return this.quickBuildMode == QuickBuildMode.DESTROY && !canUseRangeDestroy()
                ? QuickBuildMode.BUILD
                : this.quickBuildMode;
    }

    boolean isDestroyModeActive() {
        return effectiveMode() == QuickBuildMode.DESTROY;
    }

    boolean canUseRangeDestroy() {
        return QuickBuildUnlockPolicy.canUseAnyDestroyShape(
                this.controller.isProgressionEnabled(),
                hasPlugin(BuiltInRtsPluginCatalog.CHAIN_BREAK_PLUGIN),
                hasPlugin(BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN));
    }

    boolean canUseDestroyShape(AreaMineShape shape) {
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

    private boolean hasPlugin(Identifier pluginId) {
        return pluginId != null && this.controller.hasInstalledPlugin(pluginId.toString());
    }

    private void applyActiveShapeToController() {
        if (isDestroyModeActive()) {
            if (isConvenienceDestroyMode()) {
                this.controller.setAreaMineShape(AreaMineShape.BLOCK);
                this.controller.setBuildShape(BuildShape.BLOCK);
                return;
            }
            AreaMineShape shape = effectiveRangeDestroyShape();
            this.rangeDestroyShape = shape;
            this.controller.setAreaMineShape(shape);
            this.controller.setBuildShape(toBuildShape(shape));
            if (shape != AreaMineShape.CHAIN) {
                screen.ensureFillModeForShape(this.controller.getBuildShape());
            }
            return;
        }
        if (effectiveMode() == QuickBuildMode.SMART_FILL) {
            this.controller.setBuildShape(BuildShape.BLOCK);
            this.screen.ensureFillModeForShape(BuildShape.BLOCK);
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
        return mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot());
    }
}
