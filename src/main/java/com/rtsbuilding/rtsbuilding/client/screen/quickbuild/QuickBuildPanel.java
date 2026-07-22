package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.client.widget.WindowSlider;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShapeOption;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiReducer;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

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
    private static final int MODE_TOGGLE_GAP = QuickBuildWindowLayout.MODE_GAP;
    private static final int MODE_ROW_TOP = QuickBuildWindowLayout.MODE_TOP;
    private static final int SECTION_TOP = QuickBuildWindowLayout.SECTION_TOP;
    /** 连锁破坏滑条 */

    // ======================== 面板尺寸 ========================
    private static final int QUICK_BUILD_PANEL_W = QuickBuildWindowLayout.WINDOW_W;
    private static final int QUICK_BUILD_PANEL_H = QuickBuildWindowLayout.BUILD_BASE_H;
    private static final int QUICK_BUILD_DESTROY_PANEL_H = QuickBuildWindowLayout.DESTROY_BASE_H;
    private static final int QUICK_BUILD_PANEL_MIN_H = QuickBuildWindowLayout.BUILD_BASE_H;

    /** 底部提示文字区域额外高度 */
    private static final int BOTTOM_INFO_H = QuickBuildWindowLayout.BOTTOM_INFO_H;
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
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        shapeButtons = new WindowButton[currentShapeCount()];
        for (int i = 0; i < shapeButtons.length; i++) {
            shapeButtons[i] = createShapeButton(core, i);
        }
    }

    /**
     * 创建指定索引的形状按钮，使用 WindowButton 内置纹理渲染。
     * 选中状态：始终显示下半（active）贴图；未选中：上半（inactive），悬停时切换至下半。
     */
    private WindowButton createShapeButton(QuickBuildUiState core, int index) {
        QuickBuildUiShapeOption option = core.shapes.get(index);
        ResourceLocation texture = currentShapeTexture(index);
        boolean selected = option.selected;
        int normalV = selected ? SHAPE_STATE_H : 0;
        WindowButton button = new WindowButton(0, 0,
                QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT,
                Component.empty(),
                texture,
                0, normalV,
                SHAPE_SHEET_W, SHAPE_STATE_H,
                SHAPE_STATE_H, SHAPE_STATE_H,
                SHAPE_SHEET_W, SHAPE_SHEET_H,
                btn -> dispatchCore(QuickBuildUiAction.shape(option.shape)));
        button.active = option.enabled;
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
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        List<WindowButton> rightButtons = new java.util.ArrayList<>();
        this.connectToggle = null;
        for (QuickBuildUiControl control : core.controls) {
            WindowButton button = new WindowButton(0, 0,
                    QuickBuildWindowLayout.CONTROL_W, QuickBuildWindowLayout.CONTROL_H,
                    Component.literal(control.label),
                    btn -> dispatchCore(QuickBuildUiAction.control(control.id)));
            button.active = control.enabled;
            if (control.id == QuickBuildUiControl.Id.CONNECT) this.connectToggle = button;
            else rightButtons.add(button);
        }
        fillModeButtons = rightButtons.toArray(new WindowButton[0]);
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
        this.chainLimitSlider.onChange(value -> dispatchCore(QuickBuildUiAction.limit(value)));
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
        this.windowHeight = QuickBuildWindowLayout.windowHeight(isDestroyModeActive());
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

    private void renderModeToggles(GuiGraphics g, QuickBuildUiState core, int mouseX, int mouseY) {
        QuickBuildWindowLayout.Geometry layout = QuickBuildWindowLayout.geometry(
                this.windowX, this.windowY, core.mode == QuickBuildUiMode.DESTROY);
        renderModeToggle(g, core, layout.buildModeX, layout.modeY, layout.modeW, QuickBuildUiMode.BUILD,
                Component.translatable("screen.rtsbuilding.quick_build.mode_build"), mouseX, mouseY);
        renderModeToggle(g, core, layout.destroyModeX, layout.modeY, layout.modeW, QuickBuildUiMode.DESTROY,
                Component.translatable("screen.rtsbuilding.quick_build.mode_destroy"), mouseX, mouseY);
    }

    private void renderModeToggle(GuiGraphics g, QuickBuildUiState core,
            int x, int y, int w, QuickBuildUiMode mode,
            Component label, int mouseX, int mouseY) {
        boolean enabled = mode != QuickBuildUiMode.DESTROY || core.destroyEnabled;
        boolean active = core.mode == mode && enabled;
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

    private void renderProgressStrip(GuiGraphics g, QuickBuildUiState core, int x, int dividerY) {
        int barX = x + 8;
        int barY = dividerY + 4;
        int barW = this.windowWidth - 16;
        int barH = 4;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF0B1118);
        int processed = core.progressCompleted;
        int total = core.progressTotal;
        if (processed >= 0 && total > 0) {
            int filled = Math.min(barW, Math.round(barW * (processed / (float) total)));
            g.fill(barX, barY, barX + filled, barY + barH, 0xFFFF8EAD);
        } else {
            g.fill(barX, barY, barX + 1, barY + barH, 0xFF5F6F7F);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        int x = this.windowX;
        int y = this.windowY;
        int bodyY = contentY();
        QuickBuildWindowLayout.Geometry sharedLayout = QuickBuildWindowLayout.geometry(
                x, y, core.mode == QuickBuildUiMode.DESTROY);
        renderModeToggles(g, core, mouseX, mouseY);
        int shapeTitleY = sharedLayout.sectionTitleY;

        // --- 形状模式 ---
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.shape"),
                x + 10, shapeTitleY, 0xD8E3EE, false);

        // --- 形状按钮 ---
        for (int i = 0; i < shapeButtons.length; i++) {
            int slotX = sharedLayout.shapeX(i);
            int slotY = sharedLayout.shapeY(i);
            shapeButtons[i].setX(slotX);
            shapeButtons[i].setY(slotY);
            QuickBuildUiShapeOption option = core.shapes.get(i);
            shapeButtons[i].active = option.enabled;
            if (core.mode == QuickBuildUiMode.DESTROY
                    && option.shape == com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape.CHAIN
                    && core.chainMode()) {
                g.fill(slotX, slotY, slotX + QUICK_BUILD_SHAPE_SLOT, slotY + QUICK_BUILD_SHAPE_SLOT, 0xFF78B28C);
                g.fill(slotX + 2, slotY + 2, slotX + QUICK_BUILD_SHAPE_SLOT - 2,
                        slotY + QUICK_BUILD_SHAPE_SLOT - 2, 0xFF163222);
            }
            shapeButtons[i].render(g, mouseX, mouseY, partialTick);
        }

        // --- 填充模式 ---
        int rightX = sharedLayout.rightX;
        g.drawString(screen.font(), Component.translatable("screen.rtsbuilding.quick_build.fill"),
                rightX, shapeTitleY, 0xD8E3EE, false);

        if (core.chainMode()) {
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
            String valueStr = Integer.toString(core.chainLimit);
            g.drawString(screen.font(), valueStr, rightX + sliderW + 6, labelY + 16, 0xFFEAF4FF, false);
        } else if (fillModeButtons == null || controller.getBuildShape() != lastFillShape
                || (isDestroyModeActive() && this.rangeDestroyShape != this.lastAreaMineShape)) {
            rebuildFillModeButtons();
        }
        List<QuickBuildUiControl> visibleControls = coreControlsWithoutConnect(core);
        for (int i = 0; fillModeButtons != null && i < fillModeButtons.length; i++) {
            int rowY = sharedLayout.controlY(i);
            fillModeButtons[i].setX(rightX);
            fillModeButtons[i].setY(rowY);
            fillModeButtons[i].render(g, mouseX, mouseY, partialTick);

            boolean selected = i < visibleControls.size() && visibleControls.get(i).selected;
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
            int connectRowY = sharedLayout.controlY(
                    fillModeButtons == null ? visibleControls.size() : fillModeButtons.length);
            this.connectToggle.setX(rightX);
            this.connectToggle.setY(connectRowY);
            this.connectToggle.render(g, mouseX, mouseY, partialTick);

            QuickBuildUiControl connect = core.control(QuickBuildUiControl.Id.CONNECT);
            boolean connected = connect != null && connect.selected;
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
            int dividerY = sharedLayout.dividerY;
            g.fill(x + 6, dividerY - 1, x + windowWidth - 6, dividerY, 0xFF647B92);
            renderProgressStrip(g, core, x, dividerY);

            // 扩展区域中心线
            int textY = dividerY + 12;
            int itemY = textY - 4;

            if (core.mode == QuickBuildUiMode.DESTROY) {
                if (core.progressCompleted >= 0 && core.progressTotal > 0) {
                    String fullText = core.progressText + "    "
                            + screen.text("screen.rtsbuilding.quick_build.destroy_remaining", core.remainingBlocks);
                    g.drawString(screen.font(), fullText, x + 8, textY, 0xFFB8FFB8, false);
                    renderDimensionInfo(g, core, x + 8, textY + screen.font().lineHeight + 4, this.windowWidth - 16);
                } else {
                    int nextY = renderBottomInfoText(g, Component.translatable(core.hintKey, core.confirmKeyLabel),
                            x + 8, textY, this.windowWidth - 16, 0xFFB8B8);
                    renderDimensionInfo(g, core, x + 8, nextY + 3, this.windowWidth - 16);
                }
                return;
            }

            String costText = "x " + core.costText;
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
                String selectedId = core.selectedItemId;
                if (!selectedId.isBlank()) {
                    long missing = core.missingBlocks;
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
                }
            }
            int nextY = renderBottomInfoText(g,
                    Component.translatable("screen.rtsbuilding.quick_build.build_hint"),
                    x + 8,
                    textY + screen.font().lineHeight + 3,
                    this.windowWidth - 16,
                    0xFFD8E8FF);
            renderDimensionInfo(g, core, x + 8, nextY + 3, this.windowWidth - 16);
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

    private void renderDimensionInfo(GuiGraphics g, QuickBuildUiState core, int x, int y, int maxWidth) {
        Component text = Component.translatable(
                "screen.rtsbuilding.quick_build.dimensions",
                core.dimensions);
        String trimmed = screen.font().plainSubstrByWidth(text.getString(), Math.max(1, maxWidth));
        g.drawString(screen.font(), trimmed, x, y, 0xFFC9D8E8, false);
    }

    String confirmKeyLabel(boolean destroyMode) {
        return (destroyMode ? ClientKeyMappings.CONFIRM_BATCH_DESTROY : ClientKeyMappings.CONFIRM_BATCH_PLACE)
                .getTranslatedKeyMessage()
                .getString();
    }

    /** 返回右栏非连接控件，顺序与生产按钮数组及共享 Core 完全一致。 */
    private static List<QuickBuildUiControl> coreControlsWithoutConnect(QuickBuildUiState state) {
        List<QuickBuildUiControl> result = new java.util.ArrayList<>();
        for (QuickBuildUiControl control : state.controls) {
            if (control.id != QuickBuildUiControl.Id.CONNECT) result.add(control);
        }
        return result;
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
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        QuickBuildWindowLayout.Geometry layout = QuickBuildWindowLayout.geometry(
                this.windowX, this.windowY, core.mode == QuickBuildUiMode.DESTROY);
        int buttonW = layout.modeW;
        int buildX = layout.buildModeX;
        int destroyX = layout.destroyModeX;
        int y = layout.modeY;
        if (mouseY < y || mouseY >= y + MODE_TOGGLE_H) {
            return false;
        }
        if (mouseX >= buildX && mouseX < buildX + buttonW) {
            dispatchCore(QuickBuildUiAction.mode(QuickBuildUiMode.BUILD));
            return true;
        }
        if (mouseX >= destroyX && mouseX < destroyX + buttonW) {
            if (!canUseRangeDestroy()) {
                return true;
            }
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

    /**
     * 所有生产按钮先经过纯 reducer，再由 1.21.1 adapter 执行副作用。
     * 这样离屏输入回放与真实窗口不会再维护两套模式/形状切换规则。
     */
    private QuickBuildUiTransition dispatchCore(QuickBuildUiAction action) {
        QuickBuildUiTransition transition = QuickBuildUiReducer.apply(
                QuickBuildUiAdapter.snapshot(this), action);
        QuickBuildUiAdapter.apply(this, transition);
        return transition;
    }

    /** 仅供同包生产 adapter 读取真实 Screen 副作用入口。 */
    BuilderScreen uiScreen() {
        return this.screen;
    }

    /** 仅供同包生产 adapter 读取真实控制器快照。 */
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

    BuildShape activeAdvancedShape() {
        return isDestroyModeActive() ? toBuildShape(effectiveRangeDestroyShape()) : this.buildModeShape;
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
        return isDestroyModeActive() ? QuickBuildWindowLayout.DESTROY_BASE_H
                : QuickBuildWindowLayout.BUILD_BASE_H;
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
