package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.group_button.CameraModeGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.group_button.UtilityButtonGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup.DebugMenuPopup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup.LogoMenuPopup;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 顶部栏面板——编排顶部栏各组件、背景、Logo、弹窗的渲染与生命周期。
 *
 * <p>按钮组独立由以下类管理：</p>
 * <ul>
 *   <li>{@link CameraModeGroup} — 自由模式 / 环绕玩家模式切换按钮</li>
 *   <li>{@link UtilityButtonGroup} — 辅助显示切换 / Debug 弹出菜单按钮</li>
 * </ul>
 *
 * <p>本类专注于：背景绘制、Logo（含下拉菜单）、Debug 弹出菜单渲染、各按钮组的编排调度。</p>
 */
public final class TopBarPanel implements RtsPanelApi {

    private BuildingModule buildingModule;
    private CameraModule cameraModule;
    /** 所属的 BuilderScreen 引用，在 init() 中设置 */
    private BuilderScreen screen;
    private boolean quickBuildOpen;
    private boolean guideOpen;

    /** 布局帮助类实例 */
    private final TopBarLayoutHelper layout = new TopBarLayoutHelper();

    // ======================== 按钮组 ========================

    private CameraModeGroup cameraModeGroup;
    private UtilityButtonGroup utilityGroup;

    // ======================== 模式切换器 ========================

    /** 模式切换器（下栏左侧） */
    private ModeSwitcher modeSwitcher;

    // ======================== Logo ========================

    /** Logo 悬浮状态管理器 */
    private final HoverStateManager logoHoverState = new HoverStateManager();

    /** Logo 点击状态（按下时为 true，下一帧自动重置） */
    private boolean logoPressed;

    /** Logo 下拉菜单 */
    private LogoMenuPopup logoPopup;
    /** 待设置的 Gear 菜单开关回调（init 前存入，logoPopup 创建后应用） */
    private Runnable pendingOnGearMenuToggle;

    /** Debug 选项弹出菜单 */
    private DebugMenuPopup debugPopup;

    // ======================== Logo 贴图 ========================

    /** Logo 纹理（512×1024 精灵图，上半=正常，下半=悬浮/按下高亮） */
    private static final ResourceLocation LOGO_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/logo.png");
    /** Logo 绘制尺寸 */
    private static final int LOGO_SIZE = TopBarLayoutHelper.LOGO_SIZE;
    /** Logo 源文件宽度（双主题横向翻倍） */
    private static final int LOGO_SHEET_WIDTH = 1024;
    /** Logo 源文件高度（正常+高亮纵向翻倍） */
    private static final int LOGO_SHEET_HEIGHT = 1024;

    // ======================== 顶部栏背景贴图 ========================

    /** 顶部栏上半部分贴图（32×16，左半=暗色，右半=明亮） */
    private static final ResourceLocation TOP_UI_UP_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/top_ui_up.png");
    /** 顶部栏下半部分贴图（32×16，左半=暗色，右半=明亮） */
    private static final ResourceLocation TOP_UI_DOWN_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/top_ui_down.png");
    /** 贴图文件总宽度（双主题横向翻倍） */
    private static final int TOP_UI_TEX_W = 32;
    /** top_ui.png 贴图文件总高度 */
    private static final int TOP_UI_TEX_H = 16;
    /** 单主题半区宽度 */
    private static final int TOP_UI_HALF_W = 16;
    /** 九宫格边框宽度（源图上半仅 8px 高，4px border 会导致 srcInnerH=0，改为 2px） */
    private static final int TOP_UI_BORDER = 2;
    private static final TextureInfo TOP_UP_TEX_INFO = new TextureInfo(
            TOP_UI_UP_TEXTURE, TOP_UI_TEX_W, TOP_UI_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final TextureInfo TOP_DOWN_TEX_INFO = new TextureInfo(
            TOP_UI_DOWN_TEXTURE, TOP_UI_TEX_W, TOP_UI_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /**
     * 上半部分源高度（(0,0)〜(16,8)=8 行，即 y=0~7）。
     * <p>经实测贴图像素验证，上半部分共 8 行（y=0~7），非包含端点的 9 行。
     * y=8 为完全透明的间隔行。</p>
     */
    private static final int TOP_SRC_H = 8;
    /**
     * 下半部分源/绘制高度（top_ui_down.png 为 32×16，srcH=16 用满整张贴图高度）。
     * <p>半透明灰色，需要 blend 启用才能正确渲染。</p>
     */
    private static final int BOTTOM_SRC_H = TopBarLayoutHelper.BOTTOM_SRC_H;
    private static final NineSliceRegion TOP_UP_NINE_SLICE = NineSliceRegion.fullTheme(
            TOP_UP_TEX_INFO, TOP_SRC_H, TOP_UI_BORDER);
    private static final NineSliceRegion TOP_DOWN_NINE_SLICE = NineSliceRegion.fullTheme(
            TOP_DOWN_TEX_INFO, BOTTOM_SRC_H, TOP_UI_BORDER);
    /** 顶部栏上半部分绘制高度 */
    private static final int TOP_BAR_HEIGHT = TopBarLayoutHelper.TOP_BAR_HEIGHT;
    /** 屏幕背景九宫格边框宽度，用于定位下半部分顶部 Y */
    private static final int SCREEN_BORDER = TopBarLayoutHelper.SCREEN_BORDER;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = screen;
        // 从内核获取模块实例
        this.buildingModule = RtsClientKernel.get().module(BuildingModule.class);
        this.cameraModule = RtsClientKernel.get().module(CameraModule.class);
        // 创建按钮组
        this.cameraModeGroup = new CameraModeGroup(cameraModule);
        this.debugPopup = createDebugPopup();
        this.utilityGroup = new UtilityButtonGroup(debugPopup);
        // 创建模式切换器
        this.modeSwitcher = new ModeSwitcher();
        // 模式变化时自动持久化
        this.modeSwitcher.setOnModeChange(mode -> {
            if (screen != null) {
                screen.persistUiState();
            }
        });
        // 创建 Logo 下拉菜单
        this.logoPopup = createLogoPopup();
        this.logoPopup.positionFromButton(LOGO_SIZE / 2, LOGO_SIZE, screen.width);
        // 应用之前存入的待办回调
        if (this.pendingOnGearMenuToggle != null) {
            this.logoPopup.setOnGearMenuToggle(this.pendingOnGearMenuToggle);
            this.pendingOnGearMenuToggle = null;
        }
    }

    private void createButtons() {
    }

    public boolean isQuickBuildOpen() {
        return quickBuildOpen;
    }

    public void toggleQuickBuild() {
        this.quickBuildOpen = !this.quickBuildOpen;
    }

    public boolean isGuideOpen() {
        return guideOpen;
    }

    public void toggleTopGuide() {
        this.guideOpen = !this.guideOpen;
    }

    public void onRtsExited() {
        if (debugPopup != null) {
            debugPopup.onRtsExited();
        }
    }

    /**
     * 设置 Gear 菜单开关回调（转发给 LogoMenuPopup）
     */
    public void setOnGearMenuToggle(Runnable runnable) {
        this.pendingOnGearMenuToggle = runnable;
        if (this.logoPopup != null) {
            this.logoPopup.setOnGearMenuToggle(runnable);
        }
    }

    /**
     * 设置 Gear 菜单打开状态（转发给 LogoMenuPopup）
     */
    public void setGearMenuOpen(boolean open) {
        if (this.logoPopup != null) {
            this.logoPopup.setGearMenuOpen(open);
        }
    }

    /**
     * 切换辅助显示模式（等同于点击 chunk_display 按钮）。
     */
    public void toggleDebugOverlay() {
        if (this.debugPopup != null) {
            this.debugPopup.toggleDebugOverlay();
        }
    }

    /** 循环切换模式（Tab 快捷键委托） */
    public void cycleMode() {
        if (modeSwitcher != null) {
            modeSwitcher.cycleMode();
        }
    }

    /**
     * 返回当前大模式（INTERACTIVE / BUILD / BLUEPRINT）。
     * 由 {@link LeftSidebarPanel} 等组件在判断子功能可见性时使用。
     */
    public ModeSwitcher.Mode getCurrentMode() {
        return modeSwitcher != null ? modeSwitcher.getCurrentMode() : ModeSwitcher.Mode.INTERACTIVE;
    }

    /** 设置大模式（委托给模式切换器） */
    public void setMode(ModeSwitcher.Mode mode) {
        if (modeSwitcher != null) {
            modeSwitcher.setMode(mode);
        }
    }

    public void onPostUiStateLoad() {
        if (debugPopup != null) {
            debugPopup.onPostUiStateLoad();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTopBarBackground(g);

        // 渲染下栏左侧模式切换器
        if (modeSwitcher != null) {
            modeSwitcher.render(g, mouseX, mouseY);
        }

        // 一次性计算所有按钮组布局，委托各按钮组渲染
        var groupLayout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        cameraModeGroup.render(g, mouseX, mouseY, groupLayout.modeGroup());
        utilityGroup.render(g, mouseX, mouseY, groupLayout.utilityGroup());

        // 刷新所有动画器
        cameraModeGroup.tick();
        utilityGroup.tick();
        // Logo 悬浮检测
        boolean hovering = layout.logoRect().contains(mouseX, mouseY);
        boolean shouldHighlight = hovering || logoPressed;
        this.logoHoverState.update(shouldHighlight);
        if (logoPressed) {
            logoPressed = false;
        }

        // 用交叉渐变过渡 Logo 正常态 ↔ 高亮态
        renderLogoCrossFade(g);

        // blend 已在 renderTopBarBackground 中启用，此处只需重置 blend 函数
        RenderSystem.defaultBlendFunc();

        // 渲染 Debug 选项弹出菜单（位置每帧更新，跟随右侧按钮）
        if (debugPopup != null) {
            var anchor = utilityGroup.getPopupAnchor(groupLayout.utilityGroup());
            debugPopup.positionFromButton(
                    anchor.x() + anchor.width() / 2,
                    anchor.y() + anchor.height(),
                    screen.width);
            debugPopup.render(g, mouseX, mouseY);
        }

        // 渲染 Logo 下拉菜单（如果已打开）
        if (logoPopup != null) {
            logoPopup.render(g, mouseX, mouseY);
        }

        RenderSystem.disableBlend();
    }

    /**
     * 渲染工具提示覆盖层（在所有 UI 之上绘制），确保浮窗不被其他面板遮挡。
     */
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        var groupLayout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        cameraModeGroup.renderTooltipOverlay(g, groupLayout.modeGroup(), screen.width, screen.height);
        utilityGroup.renderTooltipOverlay(g, groupLayout.utilityGroup(), screen.width, screen.height);

        // 模式切换器弹窗（在左侧面板之后渲染，避免被遮挡）
        if (modeSwitcher != null) {
            modeSwitcher.renderPopup(g, mouseX, mouseY);
        }
    }

    /**
     * 交叉渐变绘制 Logo 贴图。
     */
    private void renderLogoCrossFade(GuiGraphics g) {
        TextureInfo logoInfo = new TextureInfo(
                LOGO_TEXTURE, LOGO_SHEET_WIDTH, LOGO_SHEET_HEIGHT,
                TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                TextureInfo.FilterMode.PIXEL);
        int halfW = LOGO_SHEET_WIDTH / 2;
        int halfH = LOGO_SHEET_HEIGHT / 2;
        SpriteRegion normal = new SpriteRegion(logoInfo, 0, 0, halfW, halfH);
        SpriteRegion highlighted = normal.withVOffset(halfH);
        Runnable normalRender = () -> SpriteRenderer.drawSprite(g, normal.withTheme(), 0, 0, LOGO_SIZE, LOGO_SIZE);
        Runnable highlightedRender = () -> SpriteRenderer.drawSprite(g, highlighted.withTheme(), 0, 0, LOGO_SIZE, LOGO_SIZE);
        logoHoverState.renderCrossFade(normalRender, highlightedRender);
    }

    /** 绘制顶部栏背景（九宫格拼接，上半+间隔+下半，支持透明度） */
    private void renderTopBarBackground(GuiGraphics g) {
        int screenW = screen.width;

        // 上半部分
        SpriteRenderer.drawNineSlice(g, TOP_UP_NINE_SLICE.withTheme(),
                0, 0, screenW, TOP_BAR_HEIGHT);

        // 下半部分
        int bottomY = TOP_BAR_HEIGHT + SCREEN_BORDER;
        SpriteRenderer.drawNineSlice(g, TOP_DOWN_NINE_SLICE.withTheme(),
                0, bottomY, screenW, BOTTOM_SRC_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // ==== 弹出菜单优先处理（避免被 Logo 区域抢断） ====

        // Logo 下拉菜单已打开
        if (logoPopup != null && logoPopup.isOpen()) {
            if (logoPopup.contains(mx, my)) {
                return logoPopup.handleClick(mx, my);
            }
            logoPopup.close();
            return true;
        }

        // Debug 弹出菜单已打开
        if (debugPopup != null && debugPopup.isOpen()) {
            if (debugPopup.contains(mx, my)) {
                return debugPopup.handleClick(mx, my);
            }
            debugPopup.close();
            return true;
        }

        // 模式切换器点击（含弹出菜单处理）
        if (modeSwitcher != null && modeSwitcher.mouseClicked(mx, my)) return true;

        // Logo 点击：切换下拉菜单
        if (layout.logoRect().contains(mx, my)) {
            logoPressed = true;
            if (logoPopup != null) {
                logoPopup.toggle();
            }
            return true;
        }

        // 委托按钮组处理点击
        var groupLayout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        if (cameraModeGroup.mouseClicked(mx, my, groupLayout.modeGroup())) return true;
        if (utilityGroup.mouseClicked(mx, my, groupLayout.utilityGroup())) return true;

        return false;
    }

    /** 创建 Logo 下拉菜单 */
    private LogoMenuPopup createLogoPopup() {
        return new LogoMenuPopup();
    }

    /** 创建 Debug 选项弹出菜单 */
    private DebugMenuPopup createDebugPopup() {
        return new DebugMenuPopup();
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        List<PersistableProperty> props = new ArrayList<>();

        // 调试弹出菜单状态持久化
        // 注意：相机模式/目标坐标的持久化已移入 CameraPersistenceHandler
        if (debugPopup != null) {
            props.addAll(debugPopup.persistableProperties());
        }

        // 大模式持久化（交互/建造/蓝图）
        if (modeSwitcher != null) {
            props.add(PersistableProperty.enumField(
                    "mode",
                    state -> state.mode,
                    (state, v) -> state.mode = v,
                    modeSwitcher::getCurrentMode,
                    modeSwitcher::setMode,
                    ModeSwitcher.Mode.INTERACTIVE,
                    ModeSwitcher.Mode.class
            ));
        }

        return props;
    }

    /**
     * 检测鼠标是否悬停在任意弹出菜单（Logo 下拉菜单 / Debug 选项菜单 / 模式切换器菜单）上。
     * <p>用于判断交互目标渲染是否应被遮挡。</p>
     *
     * @return true 如果有弹出菜单处于打开状态且鼠标在其区域内
     */
    public boolean isMouseOverAnyPopup(int mouseX, int mouseY) {
        if (debugPopup != null && debugPopup.isOpen() && debugPopup.contains(mouseX, mouseY)) {
            return true;
        }
        if (modeSwitcher != null && modeSwitcher.isMouseOverPopup(mouseX, mouseY)) {
            return true;
        }
        return logoPopup != null && logoPopup.isOpen() && logoPopup.contains(mouseX, mouseY);
    }
}

