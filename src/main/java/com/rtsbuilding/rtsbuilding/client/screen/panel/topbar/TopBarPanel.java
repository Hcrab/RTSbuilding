package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.RtsButton;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 顶部栏面板——编排按钮布局、渲染所有按钮（图标和文本）、处理鼠标点击分发。
 *
 * <p>所有按钮统一使用 {@link RtsButton} 实例，由 {@link RtsButton#render}
 * 管理背景与悬停视觉效果，顶部栏在此之上覆盖绘制图标纹理。</p>
 *
 * <p>状态栏文本渲染委托给 {@link TopBarStatusArea}，按钮动作派发委托给
 * {@link TopBarActionDispatcher}，本类专注于按钮编排与 UI 状态管理。</p>
 *
 * <p>继承 {@link RtsPanel}，作为固定面板而非可拖拽窗口，
 * 由 {@link BuilderScreen} 统一调度生命周期。</p>
 */
public final class TopBarPanel extends RtsPanel {

    private BuildingModule buildingModule;
    private StorageModule storageModule;
    private TopBarActionDispatcher actionDispatcher;
    /** Gear 菜单打开状态（由 BuilderScreen 同步） */
    private boolean gearMenuOpen;
    /** Gear 菜单开关回调（由 BuilderScreen 注入，避免直接持有 GearMenuPanel 引用） */
    private Runnable onGearMenuToggle;

    /** 所有顶部栏按钮实例，按 ID 索引。在 {@link #init(BuilderScreen)} 中创建。 */
    private final Map<TopBarTypes.TopBarButtonId, RtsButton> buttons = new HashMap<>();

    private boolean quickBuildOpen;
    private boolean guideOpen;
    private boolean chunkBorderVisible;
    /** 辅助显示模式总开关（由 chunk_display 按钮控制） */
    private boolean debugOverlayEnabled;

    // ======================== Debug 选项弹出菜单状态 ========================

    /** 碰撞箱显示状态 */
    private boolean collisionBoxVisible;
    /** 坐标轴彩色线条显示状态 */
    private boolean axisLinesVisible;
    /** 实体支撑块显示状态 */
    private boolean entitySupportBlockVisible;
    /** 方块光照等级显示状态 */
    private boolean blockLightLevelsVisible;

    /**
     * 是否已调用 switchRenderChunkborder() 开启区块边框渲染。
     * <p>用于在退出/重新进入 RTS 模式时准确开关区块边框，
     * 避免对 Minecraft DebugRenderer 的 toggle 重复调用导致状态不同步。</p>
     */
    private boolean chunkBorderRenderingActive;

    /**
     * 是否已通过 {@code EntityRenderDispatcher.setRenderHitBoxes(true)} 开启碰撞箱渲染。
     * <p>用于在退出/重新进入 RTS 模式时准确开关实体碰撞箱，
     * 避免与玩家手动按 F3+B 的状态冲突。</p>
     */
    private boolean collisionBoxRenderingActive;

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

    /** Debug 选项弹出菜单 */
    private DebugMenuPopup debugPopup;

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
    private static final int LOGO_SIZE = 24;
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
    private static final int BOTTOM_SRC_H = 16;
    /** 顶部栏上半部分绘制高度 */
    private static final int TOP_BAR_HEIGHT = 24;
    /** 顶部栏上下部分间隔 */
    private static final int TOP_BAR_GAP = 3;

    // ======================== 区块显示按钮 ========================

    /** 区块显示按钮贴图（1024×1024，左半暗色/右半亮色，上下两状态纵向排列） */
    private static final ResourceLocation CHUNK_DISPLAY_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/chunk_display.png");
    /** 贴图文件总宽度 */
    private static final int CHUNK_DISPLAY_TEX_W = 1024;
    /** 贴图文件总高度 */
    private static final int CHUNK_DISPLAY_TEX_H = 1024;
    /** 单主题半区宽度 */
    private static final int CHUNK_DISPLAY_HALF_W = 512;
    /** 单个状态高度（1024÷2=512，纵向两状态：0=正常，512=启用） */
    private static final int CHUNK_DISPLAY_STATE_H = 512;
    /** 区块显示按钮绘制尺寸 */
    private static final int CHUNK_BTN_SIZE = 14;
    /** 区块按钮距右边缘间距 */
    private static final int CHUNK_BTN_MARGIN_R = 4;

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
    private static final int BTN_RIGHT_SIZE = 14;

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
        super.init(screen);
        // 创建 Logo 下拉菜单
        this.logoPopup = createLogoPopup();
        this.logoPopup.setPosition(0, LOGO_SIZE);
        // 创建 Debug 选项弹出菜单
        this.debugPopup = createDebugPopup();
    }

    private void createButtons() {
    }

    public boolean isQuickBuildOpen() {
        return false;
    }

    public void toggleQuickBuild() {
    }

    public void setGearMenuOpen(boolean open) {
    }

    public void setOnGearMenuToggle(Runnable toggle) {
        this.onGearMenuToggle = toggle;
    }

    public boolean isGuideOpen() {
        return false;
    }

    public void toggleTopGuide() {
    }

    public boolean isChunkBorderVisible() {
        return chunkBorderVisible;
    }

    public boolean isDebugOverlayEnabled() {
        return debugOverlayEnabled;
    }

    public boolean isCollisionBoxVisible() {
        return collisionBoxVisible;
    }

    public boolean isAxisLinesVisible() {
        return axisLinesVisible;
    }

    public boolean isEntitySupportBlockVisible() {
        return entitySupportBlockVisible;
    }

    public boolean isBlockLightLevelsVisible() {
        return blockLightLevelsVisible;
    }

    public void toggleChunkBorder() {
        // 切换本地 UI 状态（控制贴图显示）
        chunkBorderVisible = !chunkBorderVisible;
        // 同步切换 F3+G 调试区块边框
        syncChunkBorder(chunkBorderVisible);
    }

    /**
     * 退出 RTS 模式时调用——关闭所有实际渲染的调试覆盖层，
     * 但不擦除 UI 状态字段（留给下次进入时恢复用）。
     */
    public void onRtsExited() {
        // 如果辅助显示功能已启用，关闭所有当前活跃的调试覆盖层
        if (debugOverlayEnabled) {
            disableAllDebugFeatures();
        }
    }

    /**
     * UI 状态加载完成后调用——如果辅助显示总开关处于开启状态，
     * 则重新启用之前已打开的调试覆盖层。
     */
    public void onPostUiStateLoad() {
        if (debugOverlayEnabled) {
            enableAllDebugFeatures();
        }
    }

    /**
     * 启用所有已勾选的调试功能（当 debugOverlayEnabled 打开时调用）。
     * <p>每个功能只有在对应 checkbox 已勾选且未激活时才实际切换渲染。</p>
     */
    private void enableAllDebugFeatures() {
        if (chunkBorderVisible) {
            syncChunkBorder(true);
        }
        if (collisionBoxVisible) {
            syncCollisionBox(true);
        }
        // TODO: 坐标轴/支撑块/光照等级的实际渲染待后续实现
    }

    /**
     * 关闭所有当前活跃的调试功能（debugOverlayEnabled 关闭或退出 RTS 模式时调用）。
     */
    private void disableAllDebugFeatures() {
        if (chunkBorderRenderingActive) {
            syncChunkBorder(false);
        }
        if (collisionBoxRenderingActive) {
            syncCollisionBox(false);
        }
        // TODO: 坐标轴/支撑块/光照等级的实际渲染待后续实现
    }

    /**
     * 通过反射读取 Minecraft {@code DebugRenderer.renderChunkborder} 的实际状态，
     * 精确地将区块边框渲染同步到目标状态（只 toggle 一次）。
     *
     * <p>由于 {@link net.minecraft.client.renderer.debug.DebugRenderer#switchRenderChunkborder()}
     * 是 toggle 操作且 {@code renderChunkborder} 字段为 private，无法从外部直接读取当前状态。
     * 本方法通过反射获取该字段的真实值，仅在 {@code actual != desired} 时执行一次 toggle，
     * 避免了玩家在 RTS 模式外手动按 F3+G 后状态追踪不同步的问题。</p>
     *
     * @param desired true=开启区块边框，false=关闭
     */
    private void syncChunkBorder(boolean desired) {
        if (Minecraft.getInstance().debugRenderer == null) return;
        try {
            java.lang.reflect.Field f = net.minecraft.client.renderer.debug.DebugRenderer.class
                    .getDeclaredField("renderChunkborder");
            f.setAccessible(true);
            boolean actual = f.getBoolean(Minecraft.getInstance().debugRenderer);
            if (actual != desired) {
                Minecraft.getInstance().debugRenderer.switchRenderChunkborder();
            }
            this.chunkBorderRenderingActive = desired;
        } catch (Exception e) {
            // 反射失败时的回退方案：使用旧有的追踪标记
            if (desired != chunkBorderRenderingActive) {
                Minecraft.getInstance().debugRenderer.switchRenderChunkborder();
                chunkBorderRenderingActive = desired;
            }
        }
    }

    /**
     * 通过 {@code EntityRenderDispatcher} 的官方 API 精确开关实体碰撞箱渲染（F3+B）。
     *
     * <p>{@link net.minecraft.client.renderer.entity.EntityRenderDispatcher} 提供了
     * {@code shouldRenderHitBoxes()} 读取当前状态和 {@code setRenderHitBoxes(boolean)}
     * 设置目标状态，不需要反射。</p>
     *
     * @param desired true=开启实体碰撞箱，false=关闭
     */
    private void syncCollisionBox(boolean desired) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher.shouldRenderHitBoxes() != desired) {
            dispatcher.setRenderHitBoxes(desired);
        }
        this.collisionBoxRenderingActive = desired;
    }

    @Override
    public void setOpen(boolean open) {
        super.setOpen(true);
    }

    @Override
    protected boolean canShowWindow() {
        return false;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
    }

    @Override
    protected Component getTitle() {
        return Component.empty();
    }

    @Override
    protected int getDefaultWidth() {
        return 0;
    }

    @Override
    protected int getDefaultHeight() {
        return 0;
    }

    @Override
    protected void computeDefaultPosition() {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTopBarBackground(g);
        renderChunkDisplayButton(g, mouseX, mouseY);
        renderButtonRight(g, mouseX, mouseY);

        logoHoverAnim.tick();
        btnRightHoverAnim.tick();
        arrowRotateAnim.tick();

        boolean hovering = mouseX >= 0 && mouseX < LOGO_SIZE && mouseY >= 0 && mouseY < LOGO_SIZE;
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
            int popupX = screen.width - 260 - CHUNK_BTN_MARGIN_R;
            int popupY = TOP_BAR_HEIGHT + TOP_BAR_GAP + BOTTOM_SRC_H + 2;
            debugPopup.setPosition(popupX, popupY);
            debugPopup.render(g, mouseX, mouseY);
        }

        // 渲染 Logo 下拉菜单（如果已打开）
        if (logoPopup != null) {
            logoPopup.render(g, mouseX, mouseY);
        }
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

        // 下半部分：top_ui_down.png，源区域 (0,0, 16,7)，中间空 3px
        RtsClientUiUtil.drawNineSliceRegion(g, TOP_UI_DOWN_TEXTURE,
                0, TOP_BAR_HEIGHT + TOP_BAR_GAP, screenW, BOTTOM_SRC_H, TOP_UI_BORDER,
                TOP_UI_TEX_W, TOP_UI_TEX_H,
                0, 0, TOP_UI_HALF_W, BOTTOM_SRC_H);

        // 注：不在此关闭 blend！原因见 render() 末尾的 blend 恢复处理。
    }

    private void drawButtonIcon(GuiGraphics g, TopBarTypes.TopBarButtonLayout layout, int mouseX, int mouseY) {
    }

    /** 渲染区块显示按钮（底部栏右侧） */
    private void renderChunkDisplayButton(GuiGraphics g, int mouseX, int mouseY) {
        int btnRightX = screen.width - BTN_RIGHT_SIZE - CHUNK_BTN_MARGIN_R;
        int btnX = btnRightX - CHUNK_BTN_SIZE;
        int btnY = TOP_BAR_HEIGHT + TOP_BAR_GAP + (BOTTOM_SRC_H - CHUNK_BTN_SIZE) / 2;

        int halfW = CHUNK_DISPLAY_HALF_W;
        int stateH = CHUNK_DISPLAY_STATE_H;
        int themeOffset = RtsClientUiUtil.isLightMode() ? halfW : 0;
        int srcY = debugOverlayEnabled ? stateH : 0;

        // 直接绘制当前状态，不使用交叉淡入淡出（避免精灵图区域切换时的闪烁）
        RtsClientUiUtil.drawScaledImage(g, CHUNK_DISPLAY_TEXTURE,
                btnX, btnY, CHUNK_BTN_SIZE, CHUNK_BTN_SIZE,
                themeOffset, srcY, halfW, stateH,
                CHUNK_DISPLAY_TEX_W, CHUNK_DISPLAY_TEX_H);
    }

    /** 渲染右侧按钮（chunk_display 右边，精灵图） */
    private void renderButtonRight(GuiGraphics g, int mouseX, int mouseY) {
        int btnRightX = screen.width - BTN_RIGHT_SIZE - CHUNK_BTN_MARGIN_R;
        int btnY = TOP_BAR_HEIGHT + TOP_BAR_GAP + (BOTTOM_SRC_H - BTN_RIGHT_SIZE) / 2;

        // 悬浮检测
        btnRightHovered = mouseX >= btnRightX && mouseX < btnRightX + BTN_RIGHT_SIZE
                && mouseY >= btnY && mouseY < btnY + BTN_RIGHT_SIZE;

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
                    btnRightX, btnY, BTN_RIGHT_SIZE, BTN_RIGHT_SIZE,
                    themeOffset, stateH * 2, halfW, stateH,
                    BTN_RIGHT_TEX_W, BTN_RIGHT_TEX_H);
        } else if (t > 0.001f) {
            // 弹窗关闭，悬浮淡入中 → 正常态（srcY=0）全不透明 + 悬浮态（srcY=512）淡入覆盖
            Runnable baseRenderer = () -> RtsClientUiUtil.drawPixelImage(g, BTN_RIGHT_TEXTURE,
                    btnRightX, btnY, BTN_RIGHT_SIZE, BTN_RIGHT_SIZE,
                    themeOffset, 0, halfW, stateH,
                    BTN_RIGHT_TEX_W, BTN_RIGHT_TEX_H);
            Runnable hoverRenderer = () -> RtsClientUiUtil.drawPixelImage(g, BTN_RIGHT_TEXTURE,
                    btnRightX, btnY, BTN_RIGHT_SIZE, BTN_RIGHT_SIZE,
                    themeOffset, stateH, halfW, stateH,
                    BTN_RIGHT_TEX_W, BTN_RIGHT_TEX_H);
            RtsClientUiUtil.renderCrossFade(t, baseRenderer, hoverRenderer);
        } else {
            // 弹窗关闭，未悬浮 → 直接绘制正常态（srcY=0）
            RtsClientUiUtil.drawPixelImage(g, BTN_RIGHT_TEXTURE,
                    btnRightX, btnY, BTN_RIGHT_SIZE, BTN_RIGHT_SIZE,
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
        int arrowX = btnRightX + (BTN_RIGHT_SIZE - FOLD_ARROW_SIZE) / 2;
        int arrowY = btnY + (BTN_RIGHT_SIZE - FOLD_ARROW_SIZE) / 2;
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
        if (mx >= 0 && mx < LOGO_SIZE && my >= 0 && my < LOGO_SIZE) {
            logoPressed = true;
            if (logoPopup != null) {
                logoPopup.toggle();
            }
            return true;
        }

        // 右侧按钮点击 → 切换 Debug 弹出菜单（独立于 chunk_display，互不干扰）
        int btnRightX = screen.width - BTN_RIGHT_SIZE - CHUNK_BTN_MARGIN_R;
        int btnRightY = TOP_BAR_HEIGHT + TOP_BAR_GAP + (BOTTOM_SRC_H - BTN_RIGHT_SIZE) / 2;
        if (mx >= btnRightX && mx < btnRightX + BTN_RIGHT_SIZE
                && my >= btnRightY && my < btnRightY + BTN_RIGHT_SIZE) {
            btnRightPressed = true;
            if (debugPopup != null) {
                debugPopup.toggle();
            }
            return true;
        }

        // chunk_display 按钮点击 → 切换辅助显示模式总开关
        int chunkBtnX = btnRightX - CHUNK_BTN_SIZE;
        int chunkBtnY = TOP_BAR_HEIGHT + TOP_BAR_GAP + (BOTTOM_SRC_H - CHUNK_BTN_SIZE) / 2;
        if (mx >= chunkBtnX && mx < chunkBtnX + CHUNK_BTN_SIZE
                && my >= chunkBtnY && my < chunkBtnY + CHUNK_BTN_SIZE) {
            debugOverlayEnabled = !debugOverlayEnabled;
            if (debugOverlayEnabled) {
                enableAllDebugFeatures();
            } else {
                disableAllDebugFeatures();
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

    /** 创建 Logo 下拉菜单项列表 */
    private LogoMenuPopup createLogoPopup() {
        List<LogoMenuPopup.MenuItem> items = new ArrayList<>();
        items.add(new LogoMenuPopup.MenuItem(
                Component.translatable("screen.rtsbuilding.settings.title"),
                this::toggleGearMenuFromPopup));
        return new LogoMenuPopup(items);
    }

    /** 从下拉菜单打开/关闭齿轮菜单 */
    private void toggleGearMenuFromPopup() {
        if (onGearMenuToggle != null) {
            onGearMenuToggle.run();
        }
    }

    /** 创建 Debug 选项弹出菜单 */
    private DebugMenuPopup createDebugPopup() {
        List<DebugMenuPopup.DebugToggleItem> items = new ArrayList<>();

        // 1) 区块显示（默认勾选）
        items.add(new DebugMenuPopup.DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.chunk_border"),
                state -> {
                    boolean was = this.chunkBorderVisible;
                    this.chunkBorderVisible = state;
                    // 仅当 debug 总开关开启且状态发生变化时同步实际的区块边框渲染
                    if (this.debugOverlayEnabled && was != state) {
                        syncChunkBorder(state);
                    }
                }));

        // 2) 碰撞箱显示
        items.add(new DebugMenuPopup.DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.collision_box"),
                state -> {
                    boolean was = this.collisionBoxVisible;
                    this.collisionBoxVisible = state;
                    // 仅当 debug 总开关开启且状态发生变化时同步实际渲染
                    if (this.debugOverlayEnabled && was != state) {
                        syncCollisionBox(state);
                    }
                }));

        // 3) 坐标轴彩色线条（X/红，Y/绿，Z/蓝）
        items.add(new DebugMenuPopup.DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.axis_lines"),
                state -> this.axisLinesVisible = state));

        // 4) visualize_entity_supporting_block
        items.add(new DebugMenuPopup.DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.entity_support_block"),
                state -> this.entitySupportBlockVisible = state));

        // 5) visualize_block_light_levels
        items.add(new DebugMenuPopup.DebugToggleItem(
                Component.translatable("screen.rtsbuilding.debug.block_light_levels"),
                state -> this.blockLightLevelsVisible = state));

        boolean[] defaultStates = {true, false, false, false, false};
        DebugMenuPopup popup = new DebugMenuPopup(items, defaultStates);

        return popup;
    }

    private void dispatchButtonAction(TopBarTypes.TopBarButtonId id) {
    }

    public List<TopBarTypes.TopBarButtonLayout> buildTopBarButtonLayouts() {
        return List.of();
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        return List.of(
                // 辅助显示模式总开关
                PersistableProperty.boolField(
                        "debug.overlayEnabled",
                        s -> s.debug.debugOverlayEnabled,
                        (s, v) -> s.debug.debugOverlayEnabled = v,
                        () -> this.debugOverlayEnabled,
                        v -> this.debugOverlayEnabled = v),
                // 区块边框显示
                PersistableProperty.boolField(
                        "debug.chunkBorderVisible",
                        s -> s.debug.chunkBorderVisible,
                        (s, v) -> s.debug.chunkBorderVisible = v,
                        () -> this.chunkBorderVisible,
                        v -> {
                            this.chunkBorderVisible = v;
                            if (debugPopup != null) debugPopup.setItemState(0, v);
                        }),
                // 碰撞箱显示
                PersistableProperty.boolField(
                        "debug.collisionBoxVisible",
                        s -> s.debug.collisionBoxVisible,
                        (s, v) -> s.debug.collisionBoxVisible = v,
                        () -> this.collisionBoxVisible,
                        v -> {
                            this.collisionBoxVisible = v;
                            if (debugPopup != null) debugPopup.setItemState(1, v);
                        }),
                // 坐标轴彩色线条显示
                PersistableProperty.boolField(
                        "debug.axisLinesVisible",
                        s -> s.debug.axisLinesVisible,
                        (s, v) -> s.debug.axisLinesVisible = v,
                        () -> this.axisLinesVisible,
                        v -> {
                            this.axisLinesVisible = v;
                            if (debugPopup != null) debugPopup.setItemState(2, v);
                        }),
                // 实体支撑块显示
                PersistableProperty.boolField(
                        "debug.entitySupportBlockVisible",
                        s -> s.debug.entitySupportBlockVisible,
                        (s, v) -> s.debug.entitySupportBlockVisible = v,
                        () -> this.entitySupportBlockVisible,
                        v -> {
                            this.entitySupportBlockVisible = v;
                            if (debugPopup != null) debugPopup.setItemState(3, v);
                        }),
                // 方块光照等级显示
                PersistableProperty.boolField(
                        "debug.blockLightLevelsVisible",
                        s -> s.debug.blockLightLevelsVisible,
                        (s, v) -> s.debug.blockLightLevelsVisible = v,
                        () -> this.blockLightLevelsVisible,
                        v -> {
                            this.blockLightLevelsVisible = v;
                            if (debugPopup != null) debugPopup.setItemState(4, v);
                        })
        );
    }

    public TopBarTypes.TopAction topActionForMode() {
        return null;
    }
}
