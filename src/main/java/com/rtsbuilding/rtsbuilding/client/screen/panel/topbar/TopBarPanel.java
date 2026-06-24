package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.RtsButton;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
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
    /** Logo 悬浮高亮动画器 */
    private final SmoothAnimator logoHoverAnim = AnimationFactory.createHoverAnim();
    /** 上一帧 Logo 高亮状态（用于检测状态变更以触发动画） */
    private boolean prevLogoHighlighted;

    /** Logo 点击状态（按下时为 true，下一帧自动重置） */
    private boolean logoPressed;

    /** Logo 下拉菜单 */
    private LogoMenuPopup logoPopup;

    /** Logo 纹理（512×1024 精灵图，上半=正常，下半=悬浮/按下高亮） */
    private static final ResourceLocation LOGO_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/base/logo.png");
    /** Logo 绘制尺寸 */
    private static final int LOGO_SIZE = 24;
    /** Logo 源文件宽度（双主题横向翻倍） */
    private static final int LOGO_SHEET_WIDTH = 1024;
    /** Logo 源文件高度（正常+高亮纵向翻倍） */
    private static final int LOGO_SHEET_HEIGHT = 1024;

    /** 顶部栏背景贴图（32×16，左半=暗色，右半=明亮） */
    private static final ResourceLocation TOP_UI_TEXTURE =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/top_ui.png");
    /** top_ui.png 贴图文件总宽度（双主题横向翻倍） */
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
    /** 下半部分源 Y 起始（y=9） */
    private static final int BOTTOM_SRC_Y = 9;
    /**
     * 下半部分源高度（(0,9)〜(16,16)=7 行，即 y=9~15）。
     * <p>经实测贴图像素验证，下半部分共 7 行（y=9~15），恰好填满 y=16 之前的空间。
     * 半透明灰色 (53,53,53,120)，需要 blend 启用才能正确渲染。</p>
     */
    private static final int BOTTOM_SRC_H = 7;
    /** 顶部栏上半部分绘制高度 */
    private static final int TOP_BAR_HEIGHT = 24;
    /** 顶部栏上下部分间隔 */
    private static final int TOP_BAR_GAP = 3;

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        // 创建 Logo 下拉菜单
        this.logoPopup = createLogoPopup();
        this.logoPopup.setPosition(0, LOGO_SIZE);
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
        return false;
    }

    public void toggleChunkBorder() {
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

        logoHoverAnim.tick();

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

        // 上半部分：源区域 (0,0, 16,8)，绘制高度 24px，九宫格拉伸
        // drawNineSliceRegion 已自动处理双主题偏移，无需外部传 themeOffset
        RtsClientUiUtil.drawNineSliceRegion(g, TOP_UI_TEXTURE,
                0, 0, screenW, TOP_BAR_HEIGHT, TOP_UI_BORDER,
                TOP_UI_TEX_W, TOP_UI_TEX_H,
                0, 0, TOP_UI_HALF_W, TOP_SRC_H);

        // 下半部分：源区域 (0,9, 16,7)，中间空 3px
        RtsClientUiUtil.drawNineSliceRegion(g, TOP_UI_TEXTURE,
                0, TOP_BAR_HEIGHT + TOP_BAR_GAP, screenW, BOTTOM_SRC_H, TOP_UI_BORDER,
                TOP_UI_TEX_W, TOP_UI_TEX_H,
                0, BOTTOM_SRC_Y, TOP_UI_HALF_W, BOTTOM_SRC_H);

        // 注：不在此关闭 blend！原因见 render() 末尾的 blend 恢复处理。
    }

    private void drawButtonIcon(GuiGraphics g, TopBarTypes.TopBarButtonLayout layout, int mouseX, int mouseY) {
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

        // 下拉菜单已打开
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

    private void dispatchButtonAction(TopBarTypes.TopBarButtonId id) {
    }

    public List<TopBarTypes.TopBarButtonLayout> buildTopBarButtonLayouts() {
        return List.of();
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        return List.of();
    }

    public TopBarTypes.TopAction topActionForMode() {
        return null;
    }
}
