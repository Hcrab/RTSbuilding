package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.group_button.CameraModeGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.group_button.UtilityButtonGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup.DebugMenuPopup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup.LogoMenuPopup;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.*;
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

    // ======================== 按钮组 ========================

    private CameraModeGroup cameraModeGroup;
    private UtilityButtonGroup utilityGroup;

    // ======================== Logo ========================

    /** Logo 悬浮高亮动画器 */
    private final SmoothAnimator logoHoverAnim = AnimationFactory.createHoverAnim();
    /** 上一帧 Logo 高亮状态（用于检测状态变更以触发动画） */
    private boolean prevLogoHighlighted;

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

    public void onPostUiStateLoad() {
        if (debugPopup != null) {
            debugPopup.onPostUiStateLoad();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTopBarBackground(g);

        // 一次性计算所有按钮组布局，委托各按钮组渲染
        var layout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        cameraModeGroup.render(g, mouseX, mouseY, layout.modeGroup());
        utilityGroup.render(g, mouseX, mouseY, layout.utilityGroup());

        // 刷新所有动画器
        cameraModeGroup.tick();
        utilityGroup.tick();
        logoHoverAnim.tick();

        // Logo 悬浮检测
        boolean hovering = TopBarLayoutHelper.logoRect().contains(mouseX, mouseY);
        boolean shouldHighlight = hovering || logoPressed;
        if (shouldHighlight != prevLogoHighlighted) {
            prevLogoHighlighted = shouldHighlight;
            logoHoverAnim.start(shouldHighlight ? 1.0f : 0.0f);
        }
        if (logoPressed) {
            logoPressed = false;
        }

        // 用交叉渐变过渡 Logo 正常态 ↔ 高亮态
        renderLogoCrossFade(g);

        // 恢复 blend 状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 渲染 Debug 选项弹出菜单（位置每帧更新，跟随右侧按钮）
        if (debugPopup != null) {
            var anchor = utilityGroup.getPopupAnchor(layout.utilityGroup());
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
        var layout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        cameraModeGroup.renderTooltipOverlay(g, layout.modeGroup(), screen.width, screen.height);
        utilityGroup.renderTooltipOverlay(g, layout.utilityGroup(), screen.width, screen.height);
    }

    /**
     * 交叉渐变绘制 Logo 贴图。
     */
    private void renderLogoCrossFade(GuiGraphics g) {
        int halfW = LOGO_SHEET_WIDTH / 2;
        int halfH = LOGO_SHEET_HEIGHT / 2;
        int srcX = RtsClientUiUtil.isLightMode() ? halfW : 0;
        Runnable normal = () -> RtsClientUiUtil.drawScaledImage(g, LOGO_TEXTURE, 0, 0, LOGO_SIZE, LOGO_SIZE,
                srcX, 0, halfW, halfH, LOGO_SHEET_WIDTH, LOGO_SHEET_HEIGHT);
        Runnable highlighted = () -> RtsClientUiUtil.drawScaledImage(g, LOGO_TEXTURE, 0, 0, LOGO_SIZE, LOGO_SIZE,
                srcX, halfH, halfW, halfH, LOGO_SHEET_WIDTH, LOGO_SHEET_HEIGHT);
        logoHoverAnim.renderCrossFade(normal, highlighted);
    }

    /** 绘制顶部栏背景（九宫格拼接，上半+间隔+下半，支持透明度） */
    private void renderTopBarBackground(GuiGraphics g) {
        int screenW = screen.width;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 上半部分
        RtsClientUiUtil.drawNineSliceRegion(g, TOP_UI_UP_TEXTURE,
                0, 0, screenW, TOP_BAR_HEIGHT, TOP_UI_BORDER,
                TOP_UI_TEX_W, TOP_UI_TEX_H,
                0, 0, TOP_UI_HALF_W, TOP_SRC_H);

        // 下半部分
        int bottomY = TOP_BAR_HEIGHT + SCREEN_BORDER;
        RtsClientUiUtil.drawNineSliceRegion(g, TOP_UI_DOWN_TEXTURE,
                0, bottomY, screenW, BOTTOM_SRC_H, TOP_UI_BORDER,
                TOP_UI_TEX_W, TOP_UI_TEX_H,
                0, 0, TOP_UI_HALF_W, BOTTOM_SRC_H);
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

        // Logo 点击：切换下拉菜单
        if (TopBarLayoutHelper.logoRect().contains(mx, my)) {
            logoPressed = true;
            if (logoPopup != null) {
                logoPopup.toggle();
            }
            return true;
        }

        // 委托按钮组处理点击
        var layout = TopBarLayoutHelper.GroupLayout.create(screen.width, screen.getRightSidebarWidth());
        if (cameraModeGroup.mouseClicked(mx, my, layout.modeGroup())) return true;
        if (utilityGroup.mouseClicked(mx, my, layout.utilityGroup())) return true;

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

        // 相机模式持久化（自由视角/方块轨道/玩家环绕，优先级：玩家环绕 > 方块轨道 > 自由视角）
        if (cameraModule != null) {
            props.add(PersistableProperty.boolField(
                    "camera.playerOrbitMode",
                    state -> state.camera.playerOrbitMode,
                    (state, v) -> state.camera.playerOrbitMode = v,
                    () -> cameraModule.isPlayerOrbitMode(),
                    v -> {
                        if (v) cameraModule.enablePlayerOrbitMode();
                        else cameraModule.disablePlayerOrbitMode();
                    }));
            // 方块轨道环绕（自由视角下的子功能，仅在玩家环绕未启用时恢复）
            // 保存/恢复目标方块坐标，避免重新进入时 mc.hitResult 不准导致错位
            props.add(new PersistableProperty.FieldProperty<>(
                    "camera.orbitTargetX",
                    state -> state.camera.orbitTargetX,
                    (state, v) -> state.camera.orbitTargetX = v,
                    () -> cameraModule.getState().getOrbitTargetX(),
                    v -> cameraModule.getState().setOrbitTargetX(v)));
            props.add(new PersistableProperty.FieldProperty<>(
                    "camera.orbitTargetY",
                    state -> state.camera.orbitTargetY,
                    (state, v) -> state.camera.orbitTargetY = v,
                    () -> cameraModule.getState().getOrbitTargetY(),
                    v -> cameraModule.getState().setOrbitTargetY(v)));
            props.add(new PersistableProperty.FieldProperty<>(
                    "camera.orbitTargetZ",
                    state -> state.camera.orbitTargetZ,
                    (state, v) -> state.camera.orbitTargetZ = v,
                    () -> cameraModule.getState().getOrbitTargetZ(),
                    v -> cameraModule.getState().setOrbitTargetZ(v)));
            // 方块轨道模式开关（恢复时使用已恢复的目标坐标，不依赖 mc.hitResult）
            props.add(PersistableProperty.boolField(
                    "camera.orbitMode",
                    state -> state.camera.orbitMode,
                    (state, v) -> state.camera.orbitMode = v,
                    () -> cameraModule.isOrbitMode(),
                    v -> {
                        if (!cameraModule.isPlayerOrbitMode()) {
                            if (v) {
                                cameraModule.restoreOrbitMode(
                                        cameraModule.getState().getOrbitTargetX(),
                                        cameraModule.getState().getOrbitTargetY(),
                                        cameraModule.getState().getOrbitTargetZ());
                            } else {
                                cameraModule.disableOrbitMode();
                            }
                        }
                    }));
        }

        // 调试弹出菜单状态持久化
        if (debugPopup != null) {
            props.addAll(debugPopup.persistableProperties());
        }
        return props;
    }

    /**
     * 检测鼠标是否悬停在任意弹出菜单（Logo 下拉菜单 / Debug 选项菜单）上。
     * <p>用于判断交互目标渲染是否应被遮挡。</p>
     *
     * @return true 如果有弹出菜单处于打开状态且鼠标在其区域内
     */
    public boolean isMouseOverAnyPopup(int mouseX, int mouseY) {
        if (debugPopup != null && debugPopup.isOpen() && debugPopup.contains(mouseX, mouseY)) {
            return true;
        }
        return logoPopup != null && logoPopup.isOpen() && logoPopup.contains(mouseX, mouseY);
    }
}
