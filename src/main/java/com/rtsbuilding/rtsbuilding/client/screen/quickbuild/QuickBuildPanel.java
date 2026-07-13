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
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int SECTION_TOP = 31;
    /** 连锁破坏滑条 */

    // ======================== 面板尺寸 ========================
    private static final int QUICK_BUILD_PANEL_W = 178;
    private static final int QUICK_BUILD_PANEL_H = 222;
    private static final int QUICK_BUILD_DESTROY_PANEL_H = QUICK_BUILD_PANEL_H + SHAPE_ROW_PITCH;
    private static final int QUICK_BUILD_PANEL_MIN_H = 222;

    /** 底部提示文字区域额外高度 */
    private static final int BOTTOM_INFO_H = 72;
    private static final int BOTTOM_TEXT_MAX_LINES = 3;

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
        createShapeButtons();
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
        int buildX = this.windowX + 8;
        int destroyX = buildX + buttonW + MODE_TOGGLE_GAP;
        int y = bodyY + MODE_ROW_TOP;
        renderModeToggle(g, buildX, y, buttonW, QuickBuildMode.BUILD,
                Component.translatable("screen.rtsbuilding.quick_build.mode_build"), mouseX, mouseY);
        renderModeToggle(g, destroyX, y, buttonW, QuickBuildMode.DESTROY,
                Component.translatable("screen.rtsbuilding.quick_build.mode_destroy"), mouseX, mouseY);
    }

    private void renderModeToggle(GuiGraphics g, int x, int y, int w, QuickBuildMode mode,
            Component label, int mouseX, int mouseY) {
        boolean enabled = mode != QuickBuildMode.DESTROY || canUseRangeDestroy();
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

    // ======================== 输入处理 ========================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (this.chainLimitSlider != null && isRangeDestroyChainMode()) {
            if (this.chainLimitSlider.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
        if (handleModeToggleClick(mouseX, mouseY)) {
            return;
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
        if (this.chainLimitSlider != null && isRangeDestroyChainMode()) {
            if (this.chainLimitSlider.mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.chainLimitSlider != null) {
            this.chainLimitSlider.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleModeToggleClick(double mouseX, double mouseY) {
        int bodyY = contentY();
        int totalW = this.windowWidth - 16;
        int buttonW = (totalW - MODE_TOGGLE_GAP) / 2;
        int buildX = this.windowX + 8;
        int destroyX = buildX + buttonW + MODE_TOGGLE_GAP;
        int y = bodyY + MODE_ROW_TOP;
        if (mouseY < y || mouseY >= y + MODE_TOGGLE_H) {
            return false;
        }
        if (mouseX >= buildX && mouseX < buildX + buttonW) {
            setMode(QuickBuildMode.BUILD);
            return true;
        }
        if (mouseX >= destroyX && mouseX < destroyX + buttonW) {
            if (!canUseRangeDestroy()) {
                return true;
            }
            setMode(QuickBuildMode.DESTROY);
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
        if (this.quickBuildMode == next) {
            if (isOpen()) {
                applyActiveShapeToController();
            } else {
                restoreSingleBlockCursor();
            }
            return;
        }
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
        return isDestroyModeActive() ? QUICK_BUILD_DESTROY_PANEL_H : QUICK_BUILD_PANEL_H;
    }

    private QuickBuildMode effectiveMode() {
        return this.quickBuildMode == QuickBuildMode.DESTROY && !canUseRangeDestroy()
                ? QuickBuildMode.BUILD
                : this.quickBuildMode;
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
