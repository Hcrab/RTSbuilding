package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiCatalogPage;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 快速建造面板：形状选择 + 填充模式 + 旋转控制。
 * <p>
 * 继承 {@link RtsWindowPanel} 获得窗口能力。
 * 向后兼容 {@code isQuickBuildOpen() / setQuickBuildOpen() / toggleOpen()}。
 */
public final class QuickBuildPanel extends RtsWindowPanel {
    // ======================== 面板尺寸 ========================
    private static final int QUICK_BUILD_PANEL_W = QuickBuildWindowLayout.WINDOW_W;
    private static final int QUICK_BUILD_PANEL_H =
            QuickBuildWindowLayout.windowHeight(QuickBuildUiMode.BUILD);
    private static final int QUICK_BUILD_PANEL_MIN_H = QUICK_BUILD_PANEL_H;

    // ======================== 实例 ========================
    private QuickBuildControlSurface controlSurface;
    private final QuickBuildPreferenceState preferences = new QuickBuildPreferenceState();
    private final QuickBuildConvenienceController convenience =
            new QuickBuildConvenienceController(this.preferences);
    private final SmartFillClientSession smartFill = new SmartFillClientSession();

    // ======================== 持久化属性 ========================

    private final List<PersistableProperty> properties =
            QuickBuildPersistenceBindings.create(this, this.preferences);

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
        this.preferences.buildShape(controller.getBuildShape());
        AreaMineShape storedDestroyShape = controller.getAreaMineShape();
        this.preferences.destroyShape(storedDestroyShape);
        this.convenience.init(screen, controller);
        this.controlSurface = new QuickBuildControlSurface(this::dispatchCore);
        this.controlSurface.refreshAll(QuickBuildUiAdapter.snapshot(this));
    }

    void rebuildFillModeButtons() {
        if (this.controlSurface != null) {
            this.controlSurface.refreshControlButtons(QuickBuildUiAdapter.snapshot(this));
        }
    }

    public BuildShape getBuildModeShape() {
        return this.preferences.buildShape();
    }

    public AreaMineShape getRangeDestroyShape() {
        return effectiveRangeDestroyShape();
    }

    public void setBuildModeShape(BuildShape shape) {
        this.preferences.buildShape(shape);
        if (isOpen() && !isDestroyModeActive()) {
            this.controller.setBuildShape(this.preferences.buildShape());
            screen.ensureFillModeForShape(this.preferences.buildShape());
            screen.clearShapeBuildSession();
            this.controller.clearAreaMineSession();
        }
        screen.persistUiState();
        rebuildFillModeButtons();
        refreshShapeButtons();
    }

    public void setRangeDestroyShape(AreaMineShape shape) {
        AreaMineShape next = shape == null ? AreaMineShape.CHAIN : shape;
        if (!canUseDestroyShape(next)) {
            return;
        }
        this.preferences.destroyShape(next);
        if (isOpen() && isDestroyModeActive()) {
            applyActiveShapeToController();
            screen.clearShapeBuildSession();
            this.controller.clearAreaMineSession();
        }
        screen.persistUiState();
        rebuildFillModeButtons();
        refreshShapeButtons();
    }

    public void loadStoredShapes(BuildShape storedBuildShape, AreaMineShape storedDestroyShape) {
        this.preferences.buildShape(storedBuildShape);
        // 注意：不覆盖 rangeDestroyShape——由 area_mine_shape PersistableProperty 统一管理
        if (isOpen()) {
            applyActiveShapeToController();
        }
        rebuildFillModeButtons();
        refreshShapeButtons();
    }

    public int getChainDestroyLimit() {
        return this.preferences.chainLimit();
    }

    QuickBuildUiCatalogPage getCatalogPage() {
        return this.convenience.page(isDestroyModeActive());
    }

    QuickBuildUiConvenienceTool getConvenienceTool() {
        return this.convenience.tool();
    }

    /** 返回当前便捷破坏工具的具体玩家可见名称，不再使用泛化的分组标题。 */
    public String getConvenienceToolLabel() {
        String key = switch (this.convenience.tool()) {
            case REPEAT_BOX -> "screen.rtsbuilding.quick_build.tool.repeat_box";
            case CHUNK_QUARRY -> "screen.rtsbuilding.quick_build.tool.chunk_quarry";
            case TREE_FELL -> "screen.rtsbuilding.quick_build.tool.tree_fell";
        };
        return this.screen == null ? "" : this.screen.text(key);
    }

    QuickBuildUiConvenienceSettings getConvenienceSettings() {
        return this.convenience.settings();
    }

    void setCatalogPage(QuickBuildUiCatalogPage page) {
        this.convenience.setPage(page, isDestroyModeActive());
        afterConvenienceModeChanged();
    }

    void setConvenienceTool(QuickBuildUiConvenienceTool tool) {
        this.convenience.setTool(tool);
        afterConvenienceModeChanged();
    }

    void setConvenienceParameter(QuickBuildUiConvenienceParameter parameter, int value) {
        this.convenience.setParameter(parameter, value);
        if (screen != null) {
            screen.persistUiState();
        }
        if (this.controlSurface != null) {
            this.controlSurface.syncConvenienceSettings(this.preferences.convenienceSettings());
        }
    }

    public boolean isConvenienceDestroyMode() {
        return this.convenience.isActive(isDestroyModeActive());
    }

    public RtsConvenienceDestroyPlanner.Plan convenienceDestroyPreview() {
        return this.convenience.preview(isDestroyModeActive());
    }

    public com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords.GhostPreview
            convenienceGhostPreview() {
        return this.convenience.ghostPreview(isDestroyModeActive());
    }

    public boolean submitConvenienceDestroy(BlockHitResult hit,
            com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind inputKind) {
        return this.convenience.submit(
                isDestroyModeActive(), hit, this.screen.getSelectedToolSlot(), inputKind);
    }

    String convenienceDimensionLabel() {
        return this.convenience.dimensionLabel();
    }

    String convenienceHintKey() {
        return this.convenience.hintKey(isDestroyModeActive());
    }

    private void afterConvenienceModeChanged() {
        if (isOpen()) {
            applyActiveShapeToController();
            screen.clearShapeBuildSession();
            controller.clearAreaMineSession();
        }
        if (screen != null) screen.persistUiState();
        if (this.controlSurface != null) {
            this.controlSurface.refreshAll(QuickBuildUiAdapter.snapshot(this));
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
        if (this.preferences.chainLimit() == clamped) {
            syncSliderValue();
            return;
        }
        this.preferences.chainLimit(clamped);
        syncSliderValue();
        if (persist && screen != null) {
            screen.persistUiState();
        }
    }

    private void syncSliderValue() {
        if (this.controlSurface != null) {
            this.controlSurface.syncChainLimit(this.preferences.chainLimit());
        }
    }

    private void refreshShapeButtons() {
        if (this.controlSurface != null) {
            this.controlSurface.refreshShapeButtons(QuickBuildUiAdapter.snapshot(this));
        }
    }

    private static int sanitizeChainLimit(int value) {
        return Mth.clamp(value, ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT);
    }

    public boolean isSmartFillMode() {
        return isOpen() && effectiveMode() == QuickBuildMode.SMART_FILL;
    }

    int getSmartFillMaxBlocks() {
        return this.preferences.smartFillMaxBlocks();
    }

    int getSmartFillDiameter() {
        return this.preferences.smartFillDiameter();
    }

    void setSmartFillMaxBlocks(int value) {
        this.preferences.smartFillMaxBlocks(value);
        syncSmartFillSettings();
        if (screen != null) {
            screen.persistUiState();
        }
    }

    void setSmartFillDiameter(int value) {
        this.preferences.smartFillDiameter(value);
        syncSmartFillSettings();
        if (screen != null) {
            screen.persistUiState();
        }
    }

    public com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords.GhostPreview
            smartFillGhostPreview() {
        if (!isSmartFillMode() || screen == null) {
            return com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords.GhostPreview.EMPTY;
        }
        syncSmartFillSettings();
        return this.smartFill.preview(Minecraft.getInstance(), screen.pickBlockHit());
    }

    com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlan smartFillPlan() {
        syncSmartFillSettings();
        return this.smartFill.plan(
                Minecraft.getInstance(), screen == null ? null : screen.pickBlockHit());
    }

    boolean isSmartFillAnchored() {
        return this.smartFill.anchored();
    }

    public boolean submitOrAnchorSmartFill(
            BlockHitResult hit,
            Vec3 rayOrigin,
            Vec3 rayDirection) {
        if (!isSmartFillMode()) {
            return false;
        }
        syncSmartFillSettings();
        return this.smartFill.submitOrAnchor(
                Minecraft.getInstance(),
                hit,
                rayOrigin,
                rayDirection,
                this.controller::confirmSmartFill);
    }

    public boolean cancelSmartFillAnchor() {
        return isSmartFillMode() && this.smartFill.cancelAnchor();
    }

    private void syncSmartFillSettings() {
        this.smartFill.maxBlocks(this.preferences.smartFillMaxBlocks());
        this.smartFill.diameter(this.preferences.smartFillDiameter());
    }

    // ======================== 渲染 ========================

    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.open || !canShowWindow()) return;
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        QuickBuildWindowLayout.Geometry layout = QuickBuildWindowLayout.geometry(
                this.windowX, this.windowY, core.mode);
        this.controlSurface.renderTooltip(
                g, this.screen, core, layout, this.windowWidth, mouseX, mouseY);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        int x = this.windowX;
        int y = this.windowY;
        QuickBuildWindowLayout.Geometry sharedLayout = QuickBuildWindowLayout.geometry(
                x, y, core.mode);
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, screen.font(), screen);
        this.controlSurface.render(
                g, canvas, screen, core, sharedLayout,
                this.windowWidth, mouseX, mouseY, partialTick);

        boolean creative = hasCreativePlayer();
        QuickBuildStatusRenderer.render(
                g, canvas, screen, core, sharedLayout, resolveShapeBuildItem(), creative);
    }

    String confirmKeyLabel(boolean destroyMode) {
        return (destroyMode ? ClientKeyMappings.CONFIRM_BATCH_DESTROY : ClientKeyMappings.CONFIRM_BATCH_PLACE)
                .getTranslatedKeyMessage()
                .getString();
    }

    // ======================== 输入处理 ========================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        this.controlSurface.mouseClicked(
                core,
                QuickBuildWindowLayout.geometry(
                        this.windowX, this.windowY,
                        core.mode),
                this.windowWidth,
                mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        QuickBuildUiState core = QuickBuildUiAdapter.snapshot(this);
        if (this.controlSurface.mouseDragged(
                core,
                QuickBuildWindowLayout.geometry(
                        this.windowX, this.windowY,
                        core.mode),
                this.windowWidth,
                mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean contentHandled = this.controlSurface.mouseReleased(mouseX, mouseY, button);
        /*
         * 子按钮即使消费了松开事件，父窗口也必须清除 dragging/resizing。
         * 否则一次标题栏拖动若恰好在按钮上松开，后续点击任意内容都会继续移动旧窗口。
         */
        boolean windowHandled = super.mouseReleased(mouseX, mouseY, button);
        return contentHandled || windowHandled;
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

    /**
     * Quick Build 本身不可缩放，因此持久化边界只保留玩家摆放的位置，尺寸始终以当前母版为准。
     * 这也会自动迁移旧版本保存的 178×358 外框，避免新母版内容缩小后仍套着旧外壳。
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, QUICK_BUILD_PANEL_W, QUICK_BUILD_PANEL_H);
    }

    @Override
    protected int getTitleBarHeight() {
        return QuickBuildWindowLayout.TITLE_H;
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
        this.convenience.invalidate();
        this.smartFill.clear();
        if (screen != null) {
            screen.persistUiState();
        }
    }

    public QuickBuildMode getMode() {
        return this.preferences.mode();
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
            this.preferences.destroyShape(effectiveRangeDestroyShape());
        }
        if (this.preferences.mode() == next) {
            if (isOpen()) {
                applyActiveShapeToController();
            } else {
                restoreSingleBlockCursor();
            }
            return;
        }
        this.smartFill.clear();
        this.preferences.mode(next);
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
        refreshShapeButtons();
    }

    public boolean isRangeDestroyMode() {
        return effectiveMode() == QuickBuildMode.DESTROY;
    }

    public boolean isRangeDestroyChainMode() {
        return isRangeDestroyMode() && !isConvenienceDestroyMode()
                && effectiveRangeDestroyShape() == AreaMineShape.CHAIN;
    }

    /**
     * 创造覆盖只在建造模式且本地玩家仍处于创造模式时生效。
     * 偏好值可以保留，但切回生存后不会继续随请求发送。
     */
    public boolean isCreativeOverwriteEnabled() {
        return effectiveMode() == QuickBuildMode.BUILD
                && this.preferences.overwrite()
                && hasCreativePlayer();
    }

    /**
     * 构造期安全地读取本地玩家模式。
     *
     * <p>{@link BuilderScreen} 构造函数初始化面板时，Minecraft 尚未把 Screen 的
     * {@code minecraft} 字段挂上去，因此这里必须读取客户端单例，不能调用
     * {@code screen.getMinecraft()}。</p>
     */
    boolean hasCreativePlayer() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isCreative();
    }

    boolean isOverwriteSelected() {
        return this.preferences.overwrite();
    }

    void setOverwriteSelected(boolean value) {
        this.preferences.overwrite(value);
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
        return isConvenienceDestroyMode()
                ? BuildShape.BLOCK
                : isDestroyModeActive()
                ? toBuildShape(effectiveRangeDestroyShape())
                : this.preferences.buildShape();
    }

    static boolean supportsAdvancedShape(BuildShape shape) {
        return switch (shape == null ? BuildShape.BLOCK : shape) {
            case SQUARE, WALL, CIRCLE, CYLINDER, BALL, BOX -> true;
            case BLOCK, LINE -> false;
        };
    }

    static boolean supportsVerticalToggle(BuildShape shape) {
        return shape == BuildShape.LINE
                || shape == BuildShape.CIRCLE
                || shape == BuildShape.CYLINDER;
    }

    boolean isAdvancedShape(BuildShape shape) {
        return this.preferences.advanced(shape);
    }

    void setAdvancedShape(BuildShape shape, boolean value) {
        this.preferences.advanced(shape, value);
    }

    public boolean isRoundShapeVertical(BuildShape shape) {
        return this.preferences.vertical(shape);
    }

    void setRoundShapeVertical(BuildShape shape, boolean value) {
        this.preferences.vertical(shape, value);
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
            refreshShapeButtons();
            if (screen != null) {
                screen.persistUiState();
            }
        }
    }

    // ======================== 私有辅助方法 ========================

    QuickBuildMode effectiveMode() {
        return this.preferences.mode() == QuickBuildMode.DESTROY && !canUseRangeDestroy()
                ? QuickBuildMode.BUILD
                : this.preferences.mode();
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
        AreaMineShape current = this.preferences.destroyShape();
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
        this.preferences.destroyShape(fallback);
        if (isOpen() && this.preferences.mode() == QuickBuildMode.DESTROY && this.controller != null) {
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
            if (isConvenienceDestroyMode()) {
                this.controller.setAreaMineShape(AreaMineShape.BLOCK);
                this.controller.setBuildShape(BuildShape.BLOCK);
                return;
            }
            AreaMineShape shape = effectiveRangeDestroyShape();
            this.preferences.destroyShape(shape);
            this.controller.setAreaMineShape(shape);
            this.controller.setBuildShape(toBuildShape(shape));
            if (shape != AreaMineShape.CHAIN) {
                screen.ensureFillModeForShape(this.controller.getBuildShape());
            }
            return;
        }
        BuildShape buildShape = effectiveMode() == QuickBuildMode.SMART_FILL
                ? BuildShape.BLOCK : this.preferences.buildShape();
        this.controller.setBuildShape(buildShape);
        screen.ensureFillModeForShape(buildShape);
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
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        return mc.player.getInventory().getItem(mc.player.getInventory().selected);
    }
}
