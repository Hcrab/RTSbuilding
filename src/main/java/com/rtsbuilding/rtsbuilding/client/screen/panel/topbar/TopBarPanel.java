package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.util.*;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup.DebugMenuPopup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.popup.LogoMenuPopup;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import java.util.List;

/**
 * 顶部栏面板——编排按钮布局、渲染所有按钮（图标和文本）、处理鼠标点击分发。
 *
 * <p>所有按钮统一使用 {@link RtsButton} 实例，由 {@link RtsButton#render}
 * 管理背景与悬停视觉效果，顶部栏在此之上覆盖绘制图标纹理。</p>
 *
 * <p>状态栏文本渲染委托给 {@link TopBarStatusArea}，
 * 本类专注于按钮编排与 UI 状态管理。</p>
 *
 * <p>实现 {@link RtsPanelApi}，由 {@link BuilderScreen} 统一调度生命周期。
 * 的窗口框架——顶部栏是固定面板，无可拖拽、缩放或关闭行为。</p>
 */
public final class TopBarPanel implements RtsPanelApi {

    private BuildingModule buildingModule;
    /** 所属的 BuilderScreen 引用，在 init() 中设置 */
    private BuilderScreen screen;
    private boolean quickBuildOpen;
    private boolean guideOpen;

    /** Logo 悬浮高亮动画器 */
    private final SmoothAnimator logoHoverAnim = AnimationFactory.createHoverAnim();
    /** 上一帧 Logo 高亮状态（用于检测状态变更以触发动画） */
    private boolean prevLogoHighlighted;

    /** 折叠箭头状态切换动画器 */
    private final SmoothAnimator arrowRotateAnim = AnimationFactory.createExpandAnim();
    /** 上一帧折叠箭头激活状态 */
    private boolean prevArrowActive;

    /** Logo 点击状态（按下时为 true，下一帧自动重置） */
    private boolean logoPressed;

    /** Logo 下拉菜单 */
    private LogoMenuPopup logoPopup;
    /** 待设置的 Gear 菜单开关回调（init 前存入，logoPopup 创建后应用） */
    private Runnable pendingOnGearMenuToggle;

    /** Debug 选项弹出菜单 */
    private DebugMenuPopup debugPopup;

    /** 区块显示按钮快捷键浮窗（含延迟显示与淡入淡出动画） */
    private final FloatingTooltip chunkBtnTooltip = new FloatingTooltip();

    /** 区块显示按钮悬浮淡入动画器 */
    private final SmoothAnimator chunkDisplayHoverAnim = AnimationFactory.createHoverAnim();
    /** 区块显示按钮上一帧悬浮状态 */
    private boolean prevChunkDisplayHovered;

    /** button_right 悬浮淡入动画器 */
    private final SmoothAnimator btnRightHoverAnim = AnimationFactory.createHoverAnim();
    /** button_right 上一帧悬浮状态 */
    private boolean prevBtnRightHovered;

    /** button_right 悬浮状态 */
    private boolean btnRightHovered;
    /** button_right 点击标记 */
    private boolean btnRightPressed;

    /** Logo 纹理（512×1024 精灵图，上半=正常，下半=悬浮/按下高亮） */
    private static final ResourceLocation LOGO_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/base/logo.png");
    /** Logo 绘制尺寸 */
    private static final int LOGO_SIZE = TopBarLayoutHelper.LOGO_SIZE;
    /** Logo 源文件宽度（双主题横向翻倍） */
    private static final int LOGO_SHEET_WIDTH = 1024;
    /** Logo 源文件高度（正常+高亮纵向翻倍） */
    private static final int LOGO_SHEET_HEIGHT = 1024;

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
    /** 顶部栏上下部分间隔 */
    private static final int TOP_BAR_GAP = TopBarLayoutHelper.TOP_BAR_GAP;
    /** 屏幕背景九宫格边框宽度，用于定位下半部分顶部 Y */
    private static final int SCREEN_BORDER = TopBarLayoutHelper.SCREEN_BORDER;

    // ======================== 区块显示按钮 ========================

    /** 区块显示按钮贴图（1024×1024，左半暗色/右半亮色，上下两状态纵向排列） */
    private static final ResourceLocation CHUNK_DISPLAY_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/chunk_display.png");
    /** 贴图文件总宽度 */
    private static final int CHUNK_DISPLAY_TEX_W = 1024;
    /** 贴图文件总高度 */
    private static final int CHUNK_DISPLAY_TEX_H = 1536;
    /** 单主题半区宽度 */
    private static final int CHUNK_DISPLAY_HALF_W = 512;
    /** 单个状态高度（1536÷3=512，纵向三状态：0=正常，512=悬浮，1024=按下） */
    private static final int CHUNK_DISPLAY_STATE_H = 512;
    /** 区块显示按钮绘制尺寸 */
    private static final int CHUNK_BTN_SIZE = TopBarLayoutHelper.BTN_SIZE;
    /** 区块按钮距右边缘间距 */
    private static final int CHUNK_BTN_MARGIN_R = TopBarLayoutHelper.BTN_MARGIN_R;

    // ======================== 右侧按钮 ========================

    /** 右侧按钮贴图（1024×1536，左半暗色/右半亮色，三状态纵向排列） */
    private static final ResourceLocation BTN_RIGHT_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/button_right.png");
    /** 贴图文件总宽度 */
    private static final int BTN_RIGHT_TEX_W = 1024;
    /** 贴图文件总高度 */
    private static final int BTN_RIGHT_TEX_H = 1536;
    /** 单主题半区宽度 */
    private static final int BTN_RIGHT_HALF_W = 512;
    /** 单个状态高度（1536÷3=512，三状态：0=正常，512=悬浮，1024=按下） */
    private static final int BTN_RIGHT_STATE_H = 512;
    /** 按钮绘制尺寸（与 chunk_display 一致） */
    private static final int BTN_RIGHT_SIZE = TopBarLayoutHelper.BTN_SIZE;

    // ======================== 折叠箭头 ========================

    /** 折叠箭头贴图（1024×1024，左半暗色/右半亮色，两状态纵向排列） */
    private static final ResourceLocation FOLD_ARROW_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/base/fold_arrow.png");
    /** 贴图文件总宽度 */
    private static final int FOLD_ARROW_TEX_W = 1024;
    /** 贴图文件总高度 */
    private static final int FOLD_ARROW_TEX_H = 1024;
    /** 单主题半区宽度 */
    private static final int FOLD_ARROW_HALF_W = 512;
    /** 单个状态高度（1024÷2=512，两状态：0=正常，512=点击后） */
    private static final int FOLD_ARROW_STATE_H = 512;
    /** 箭头绘制尺寸（在 14×14 按钮内居中，左右留 3px 边距） */
    private static final int FOLD_ARROW_SIZE = 8;

    @Override
    public void init(BuilderScreen screen) {
        this.screen = screen;
        // 从内核获取模块实例
        this.buildingModule = RtsClientKernel.get().module("building");
        // 创建 Logo 下拉菜单
        this.logoPopup = createLogoPopup();
        this.logoPopup.positionFromButton(LOGO_SIZE / 2, LOGO_SIZE, screen.width);
        // 创建 Debug 选项弹出菜单
        this.debugPopup = createDebugPopup();
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
        renderChunkDisplayButton(g, mouseX, mouseY);
        renderButtonRight(g, mouseX, mouseY);

        chunkDisplayHoverAnim.tick();
        chunkBtnTooltip.tick();
        logoHoverAnim.tick();
        btnRightHoverAnim.tick();
        arrowRotateAnim.tick();

        boolean hovering = TopBarLayoutHelper.logoRect().contains(mouseX, mouseY);
        boolean shouldHighlight = hovering || logoPressed;

        // 状态变化时触发动画
        if (shouldHighlight != prevLogoHighlighted) {
            prevLogoHighlighted = shouldHighlight;
            logoHoverAnim.start(shouldHighlight ? 1.0f : 0.0f);
        }

        // 按下状态仅持续一帧，后续靠 hover 检测
        if (logoPressed) {
            logoPressed = false;
        }

        // 用交叉渐变过渡 Logo 正常态 ↔ 高亮态
        renderLogoCrossFade(g);

        // 恢复 blend 状态！renderCrossFade 在过渡期内会调用 endCrossFadeBlend → disableBlend，
        // 但 GuiGraphics 的 blit 是延迟批量提交的（flush 前 blend 状态必须正确），
        // 所以重新开启 blend，确保顶部栏九宫格的半透明区域能正确渲染。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 渲染 Debug 选项弹出菜单（位置每帧更新，跟随按钮）
        if (debugPopup != null) {
            TopBarLayoutHelper.Rect rightBtn = TopBarLayoutHelper.btnRightRect(screen.width, screen.getRightSidebarWidth());
            debugPopup.positionFromButton(
                    rightBtn.x() + rightBtn.width() / 2,
                    rightBtn.y() + rightBtn.height(),
                    screen.width);
            debugPopup.render(g, mouseX, mouseY);
        }

        // 渲染 Logo 下拉菜单（如果已打开）
        if (logoPopup != null) {
            logoPopup.render(g, mouseX, mouseY);
        }

        // 关闭 blend——与 renderTopBarBackground() 中 enableBlend 配对
        RenderSystem.disableBlend();
    }

    /**
     * 交叉渐变绘制 Logo 贴图。
     * <p>过渡阶段：正常态（上半）全不透明 + 高亮态（下半）按 alpha = t 叠加。</p>
     */
    private void renderLogoCrossFade(GuiGraphics g) {
        int halfW = LOGO_SHEET_WIDTH / 2;                           // 单主题半区宽度
        int halfH = LOGO_SHEET_HEIGHT / 2;                          // 单个状态高度
        int srcX = RtsClientUiUtil.isLightMode() ? halfW : 0;       // 双主题横向偏移
        Runnable normal = () -> RtsClientUiUtil.drawScaledImage(g, LOGO_TEXTURE, 0, 0, LOGO_SIZE, LOGO_SIZE,
                srcX, 0, halfW, halfH, LOGO_SHEET_WIDTH, LOGO_SHEET_HEIGHT);
        Runnable highlighted = () -> RtsClientUiUtil.drawScaledImage(g, LOGO_TEXTURE, 0, 0, LOGO_SIZE, LOGO_SIZE,
                srcX, halfH, halfW, halfH, LOGO_SHEET_WIDTH, LOGO_SHEET_HEIGHT);
        logoHoverAnim.renderCrossFade(normal, highlighted);
    }

    /** 绘制顶部栏背景（九宫格拼接，上半+间隔+下半，支持透明度） */
    private void renderTopBarBackground(GuiGraphics g) {
        int screenW = screen.width;

        // 启用透明度混合，让贴图半透明区域正确渲染
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 上半部分：top_ui_up.png，源区域 (0,0, 16,8)，绘制高度 24px，九宫格拉伸
        // drawNineSliceRegion 已自动处理双主题偏移，无需外部传 themeOffset
        RtsClientUiUtil.drawNineSliceRegion(g, TOP_UI_UP_TEXTURE,
                0, 0, screenW, TOP_BAR_HEIGHT, TOP_UI_BORDER,
                TOP_UI_TEX_W, TOP_UI_TEX_H,
                0, 0, TOP_UI_HALF_W, TOP_SRC_H);

        // 下半部分：top_ui_down.png，紧贴屏幕背景上边框底部
        int bottomY = TOP_BAR_HEIGHT + SCREEN_BORDER;
        RtsClientUiUtil.drawNineSliceRegion(g, TOP_UI_DOWN_TEXTURE,
                0, bottomY, screenW, BOTTOM_SRC_H, TOP_UI_BORDER,
                TOP_UI_TEX_W, TOP_UI_TEX_H,
                0, 0, TOP_UI_HALF_W, BOTTOM_SRC_H);

        // 注：不在此关闭 blend！原因见 render() 末尾的 blend 恢复处理。
    }

    /** 渲染区块显示按钮（底部栏右侧），含悬浮淡入动画与快捷键浮窗逻辑 */
    private void renderChunkDisplayButton(GuiGraphics g, int mouseX, int mouseY) {
        TopBarLayoutHelper.Rect chunkRect = TopBarLayoutHelper.chunkBtnRect(screen.width, screen.getRightSidebarWidth());

        // 悬浮检测
        boolean chunkHovered = chunkRect.contains(mouseX, mouseY);
        if (chunkHovered != prevChunkDisplayHovered) {
            prevChunkDisplayHovered = chunkHovered;
            chunkDisplayHoverAnim.start(chunkHovered ? 1.0f : 0.0f);
        }

        int halfW = CHUNK_DISPLAY_HALF_W;
        int stateH = CHUNK_DISPLAY_STATE_H;
        int themeOffset = RtsClientUiUtil.isLightMode() ? halfW : 0;
        boolean debugOverlayEnabled = debugPopup != null && debugPopup.isDebugOverlayEnabled();
        float t = chunkDisplayHoverAnim.getValue();

        if (debugOverlayEnabled) {
            // 辅助显示已启用 → 直接绘制按下态（v=1024），不做悬浮处理
            RtsClientUiUtil.drawScaledImage(g, CHUNK_DISPLAY_TEXTURE,
                    chunkRect.x(), chunkRect.y(), chunkRect.width(), chunkRect.height(),
                    themeOffset, stateH * 2, halfW, stateH,
                    CHUNK_DISPLAY_TEX_W, CHUNK_DISPLAY_TEX_H);
        } else if (t > 0.001f) {
            // 辅助显示关闭，悬浮淡入中 → 正常态（v=0）全不透明 + 悬浮态（v=512）淡入覆盖
            Runnable baseRenderer = () -> RtsClientUiUtil.drawScaledImage(g, CHUNK_DISPLAY_TEXTURE,
                    chunkRect.x(), chunkRect.y(), chunkRect.width(), chunkRect.height(),
                    themeOffset, 0, halfW, stateH,
                    CHUNK_DISPLAY_TEX_W, CHUNK_DISPLAY_TEX_H);
            Runnable hoverRenderer = () -> RtsClientUiUtil.drawScaledImage(g, CHUNK_DISPLAY_TEXTURE,
                    chunkRect.x(), chunkRect.y(), chunkRect.width(), chunkRect.height(),
                    themeOffset, stateH, halfW, stateH,
                    CHUNK_DISPLAY_TEX_W, CHUNK_DISPLAY_TEX_H);
            RtsClientUiUtil.renderCrossFade(t, baseRenderer, hoverRenderer);
        } else {
            // 辅助显示关闭，未悬浮 → 直接绘制正常态（v=0）
            RtsClientUiUtil.drawScaledImage(g, CHUNK_DISPLAY_TEXTURE,
                    chunkRect.x(), chunkRect.y(), chunkRect.width(), chunkRect.height(),
                    themeOffset, 0, halfW, stateH,
                    CHUNK_DISPLAY_TEX_W, CHUNK_DISPLAY_TEX_H);
        }

        // 悬浮检测与快捷键浮窗（Debug 弹窗打开时不显示浮窗）
        boolean popupOpen = debugPopup != null && debugPopup.isOpen();
        chunkBtnTooltip.update(chunkHovered, popupOpen);
        if (chunkBtnTooltip.shouldRender()) {
            String keyText = RtsKeyMappings.TOGGLE_DEBUG_OVERLAY_KEY.getTranslatedKeyMessage().getString();
            int textColor = ThemeManager.getTextColor();
            int shortcutColor = SmoothAnimator.scaleColor(textColor, 0.6f);
            chunkBtnTooltip.renderBelowButton(g, chunkRect.x(), chunkRect.y(), chunkRect.width(), chunkRect.height(),
                    6, 3, "辅助显示\n辅助显示，如显示区块和碰撞箱\n快捷键: " + keyText, textColor, shortcutColor);
        }
    }

    /** 渲染右侧按钮（chunk_display 右边，精灵图） */
    private void renderButtonRight(GuiGraphics g, int mouseX, int mouseY) {
        TopBarLayoutHelper.Rect rightRect = TopBarLayoutHelper.btnRightRect(screen.width, screen.getRightSidebarWidth());

        // 悬浮检测
        btnRightHovered = rightRect.contains(mouseX, mouseY);

        // 悬浮状态变化 → 触发淡入动画（悬浮态淡入覆盖，正常态始终不透明）
        if (btnRightHovered != prevBtnRightHovered) {
            prevBtnRightHovered = btnRightHovered;
            btnRightHoverAnim.start(btnRightHovered ? 1.0f : 0.0f);
        }

        // 清除瞬间按下标记
        if (btnRightPressed) {
            btnRightPressed = false;
        }

        int halfW = BTN_RIGHT_HALF_W;
        int stateH = BTN_RIGHT_STATE_H;
        int themeOffset = RtsClientUiUtil.isLightMode() ? halfW : 0;

        boolean popupOpen = debugPopup != null && debugPopup.isOpen();
        float t = btnRightHoverAnim.getValue();

        if (popupOpen) {
            // 弹窗打开 → 直接绘制 state 2（srcY=1024），不做悬浮处理
            RtsClientUiUtil.drawPixelImage(g, BTN_RIGHT_TEXTURE,
                    rightRect.x(), rightRect.y(), rightRect.width(), rightRect.height(),
                    themeOffset, stateH * 2, halfW, stateH,
                    BTN_RIGHT_TEX_W, BTN_RIGHT_TEX_H);
        } else if (t > 0.001f) {
            // 弹窗关闭，悬浮淡入中 → 正常态（srcY=0）全不透明 + 悬浮态（srcY=512）淡入覆盖
            Runnable baseRenderer = () -> RtsClientUiUtil.drawPixelImage(g, BTN_RIGHT_TEXTURE,
                    rightRect.x(), rightRect.y(), rightRect.width(), rightRect.height(),
                    themeOffset, 0, halfW, stateH,
                    BTN_RIGHT_TEX_W, BTN_RIGHT_TEX_H);
            Runnable hoverRenderer = () -> RtsClientUiUtil.drawPixelImage(g, BTN_RIGHT_TEXTURE,
                    rightRect.x(), rightRect.y(), rightRect.width(), rightRect.height(),
                    themeOffset, stateH, halfW, stateH,
                    BTN_RIGHT_TEX_W, BTN_RIGHT_TEX_H);
            RtsClientUiUtil.renderCrossFade(t, baseRenderer, hoverRenderer);
        } else {
            // 弹窗关闭，未悬浮 → 直接绘制正常态（srcY=0）
            RtsClientUiUtil.drawPixelImage(g, BTN_RIGHT_TEXTURE,
                    rightRect.x(), rightRect.y(), rightRect.width(), rightRect.height(),
                    themeOffset, 0, halfW, stateH,
                    BTN_RIGHT_TEX_W, BTN_RIGHT_TEX_H);
        }

        // 检测折叠箭头状态变化并触发旋转动画（弹窗打开 → 旋转）
        boolean arrowActive = debugPopup != null && debugPopup.isOpen();
        if (arrowActive != prevArrowActive) {
            prevArrowActive = arrowActive;
            arrowRotateAnim.start(arrowActive ? 1.0f : 0.0f);
        }

        // 在按钮中间绘制折叠箭头（参考 CollapsibleSection.renderArrow 的旋转动画）
        int arrowX = rightRect.x() + (rightRect.width() - FOLD_ARROW_SIZE) / 2;
        int arrowY = rightRect.y() + (rightRect.height() - FOLD_ARROW_SIZE) / 2;
        int arrowThemeOffset = RtsClientUiUtil.isLightMode() ? FOLD_ARROW_HALF_W : 0;
        g.pose().pushPose();
        // 位移至箭头中心 → 绕 Z 轴旋转 → 移回
        float halfArrow = FOLD_ARROW_SIZE / 2.0f;
        g.pose().translate(arrowX + halfArrow, arrowY + halfArrow, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(arrowRotateAnim.getValue() * 90.0f));
        g.pose().translate(-halfArrow, -halfArrow, 0);
        // 始终绘制正常态（srcY=0），通过旋转实现展开/收起视觉效果
        RtsClientUiUtil.drawPixelImage(g, FOLD_ARROW_TEXTURE,
                0, 0, FOLD_ARROW_SIZE, FOLD_ARROW_SIZE,
                arrowThemeOffset, 0, FOLD_ARROW_HALF_W, FOLD_ARROW_STATE_H,
                FOLD_ARROW_TEX_W, FOLD_ARROW_TEX_H);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Logo 点击：切换下拉菜单
        if (TopBarLayoutHelper.logoRect().contains(mx, my)) {
            logoPressed = true;
            if (logoPopup != null) {
                logoPopup.toggle();
            }
            return true;
        }

        // 右侧按钮点击 → 切换 Debug 弹出菜单（独立于 chunk_display，互不干扰）
        TopBarLayoutHelper.Rect rightRect = TopBarLayoutHelper.btnRightRect(screen.width, screen.getRightSidebarWidth());
        if (rightRect.contains(mx, my)) {
            btnRightPressed = true;
            if (debugPopup != null) {
                debugPopup.toggle();
            }
            return true;
        }

        // chunk_display 按钮点击 → 切换辅助显示模式总开关
        if (TopBarLayoutHelper.chunkBtnRect(screen.width, screen.getRightSidebarWidth()).contains(mx, my)) {
            if (debugPopup != null) {
                debugPopup.toggleDebugOverlay();
            }
            return true;
        }

        // Debug 弹出菜单已打开
        if (debugPopup != null && debugPopup.isOpen()) {
            if (debugPopup.contains(mx, my)) {
                // 点击菜单项
                return debugPopup.handleClick(mx, my);
            }
            // 点击菜单外部 → 关闭
            debugPopup.close();
            return true;
        }

        // Logo 下拉菜单已打开
        if (logoPopup != null && logoPopup.isOpen()) {
            if (logoPopup.contains(mx, my)) {
                // 点击菜单项
                return logoPopup.handleClick(mx, my);
            }
            // 点击菜单外部 → 关闭
            logoPopup.close();
            return true;
        }

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
        if (debugPopup != null) {
            return debugPopup.persistableProperties();
        }
        return List.of();
    }


}
