package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.BasePopup;
import com.rtsbuilding.rtsbuilding.client.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 模式切换器——在顶部栏下栏左侧显示当前模式按钮，点击弹出模式选择列表。
 *
 * <p>三种模式（以弹出列表顺序排列）：交互模式（默认）、建造模式、蓝图模式。
 * 每种模式配有专属图标贴图，由 lang 集中管理显示名称。</p>
 *
 * <p>背景贴图 {@code mode.png} 规格：128×64，水平双主题，纵向正常(0~32)与悬浮(32~64)两状态。</p>
 */
public final class ModeSwitcher {

    // ======================== 模式枚举 ========================

    public enum Mode {
        INTERACTIVE(0, "interactive"),
        BUILD(1, "build"),
        BLUEPRINT(2, "blueprint");

        final int index;
        final String langKey;

        Mode(int index, String name) {
            this.index = index;
            this.langKey = "screen.rtsbuilding.mode." + name;
        }

        public Component getDisplayName() {
            return Component.translatable(langKey);
        }
    }

    // ======================== 背景贴图（mode.png）=======================

    /** 模式切换器背景（128×64，水平双主题，纵向 2 状态） */
    private static final ResourceLocation MODE_BG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/mode.png");
    private static final int MODE_BG_TEX_W = 128;
    private static final int MODE_BG_TEX_H = 64;
    /** 正常态源高度 = 32（v=0~32） */
    private static final int MODE_BG_NORMAL_H = 32;
    /** 九宫格边框宽度 */
    private static final int MODE_BG_BORDER = 4;

    private static final TextureInfo MODE_BG_TEX_INFO = new TextureInfo(
            MODE_BG_TEXTURE, MODE_BG_TEX_W, MODE_BG_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private static final NineSliceRegion MODE_BG_NINE_SLICE = NineSliceRegion.fullTheme(
            MODE_BG_TEX_INFO, MODE_BG_NORMAL_H, MODE_BG_BORDER);

    // ======================== 模式图标贴图 ========================

    private static final ResourceLocation BLUEPRINT_MODE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/blueprint_mode.png");
    private static final int BLUEPRINT_MODE_TEX_W = 128;
    private static final int BLUEPRINT_MODE_TEX_H = 64;
    private static final TextureInfo BLUEPRINT_TEX_INFO = new TextureInfo(
            BLUEPRINT_MODE_TEX, BLUEPRINT_MODE_TEX_W, BLUEPRINT_MODE_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private static final ResourceLocation BUILD_MODE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/build_mode.png");
    private static final int BUILD_MODE_TEX_W = 128;
    private static final int BUILD_MODE_TEX_H = 64;
    private static final TextureInfo BUILD_TEX_INFO = new TextureInfo(
            BUILD_MODE_TEX, BUILD_MODE_TEX_W, BUILD_MODE_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    private static final ResourceLocation INTERACTIVE_MODE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/interactive_mode.png");
    private static final int INTERACTIVE_MODE_TEX_W = 128;
    private static final int INTERACTIVE_MODE_TEX_H = 96;
    private static final TextureInfo INTERACTIVE_TEX_INFO = new TextureInfo(
            INTERACTIVE_MODE_TEX, INTERACTIVE_MODE_TEX_W, INTERACTIVE_MODE_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    // ======================== 折叠箭头贴图 ========================

    /** 折叠箭头（复用 base/fold_arrow.png，规格参见 UtilityButtonGroup） */
    private static final ResourceLocation FOLD_ARROW_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/fold_arrow.png");
    private static final int FOLD_ARROW_TEX_W = 1024;
    private static final int FOLD_ARROW_TEX_H = 1024;
    private static final int FOLD_ARROW_HALF_W = 512;
    private static final int FOLD_ARROW_STATE_H = 512;

    private static final TextureInfo FOLD_ARROW_TEX_INFO = new TextureInfo(
            FOLD_ARROW_TEX, FOLD_ARROW_TEX_W, FOLD_ARROW_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    // ======================== 绘制常量 ========================

    /** 模式切换器高度（匹配底部栏内容区高度） */
    private static final int SWITCHER_HEIGHT = 14;
    /** 模式图标绘制尺寸 */
    private static final int ICON_SIZE = 12;
    /** 图标与文字间距 */
    private static final int ICON_TEXT_GAP = 3;
    /** 折叠箭头尺寸 */
    private static final int ARROW_SIZE = 8;
    /** 文字与折叠箭头间距 */
    private static final int TEXT_ARROW_GAP = 5;
    /** 左右边缘 padding */
    private static final int PAD_H = 5;
    /** 左侧外边距（距屏幕左边缘） */
    private static final int MARGIN_LEFT = 2;

    // ======================== 弹窗常量 ========================

    /** 弹窗菜单项高度 */
    private static final int POPUP_ITEM_HEIGHT = 22;
    /** 弹窗内容左右 padding */
    private static final int POPUP_PAD_H = 6;
    /** 弹窗中图标绘制尺寸 */
    private static final int POPUP_ICON_SIZE = 14;
    /** 弹窗图标固定槽位宽度（取 POPUP_ICON_SIZE，确保所有项文字对齐） */
    private static final int POPUP_ICON_SLOT_W = POPUP_ICON_SIZE;
    /** 弹窗中主文字与快捷键文字间距 */
    private static final int POPUP_SHORTCUT_GAP = 16;

    /** 亮色主题下快捷键文字颜色 */
    private static final int LIGHT_SHORTCUT_COLOR = 0xFF777777;
    /** 暗色主题下快捷键文字颜色 */
    private static final int DARK_SHORTCUT_COLOR = 0xFF888888;

    // ======================== 实例状态 ========================

    /** 当前选中模式（默认交互模式） */
    private Mode currentMode = Mode.INTERACTIVE;

    /** 背景悬浮状态管理器 */
    private final HoverStateManager hoverState = new HoverStateManager();

    /** 弹出模式选择列表 */
    private final ModePopup popup;

    /** 固定宽度（取三种模式文字中最宽值，避免切换时背景跳变） */
    private final int fixedWidth;

    /** 箭头旋转动画器（弹出菜单打开/关闭时旋转 90°） */
    private final SmoothAnimator arrowAnim = AnimationFactory.createHoverAnim();

    public ModeSwitcher() {
        this.popup = new ModePopup(this);
        this.fixedWidth = computeFixedWidth();
    }

    /** 计算固定宽度：取三种模式中文字最宽者 */
    private int computeFixedWidth() {
        var font = Minecraft.getInstance().font;
        int maxTextWidth = 0;
        for (Mode mode : Mode.values()) {
            int tw = font.width(mode.getDisplayName());
            if (tw > maxTextWidth) maxTextWidth = tw;
        }
        return PAD_H * 2 + ICON_SIZE + ICON_TEXT_GAP + maxTextWidth + TEXT_ARROW_GAP + ARROW_SIZE;
    }

    // ======================== 模式查询/切换 ========================

    public Mode getCurrentMode() {
        return currentMode;
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
    }

    /** 循环切换到下一个模式（Tab 快捷键调用） */
    public void cycleMode() {
        Mode[] modes = Mode.values();
        int next = (currentMode.index + 1) % modes.length;
        setMode(modes[next]);
    }

    /** 弹出菜单是否打开 */
    public boolean isPopupOpen() {
        return popup.isOpen();
    }

    /** 检测鼠标是否在弹出菜单区域中 */
    public boolean isMouseOverPopup(int mx, int my) {
        return popup.isOpen() && popup.contains(mx, my);
    }

    // ======================== 布局计算 ========================

    /** 模式切换器左上角 X */
    public int getX() {
        return MARGIN_LEFT;
    }

    /** 模式切换器左上角 Y（底部栏内容区垂直居中） */
    public int getY() {
        int bottomBarY = TopBarLayoutHelper.TOP_BAR_HEIGHT + TopBarLayoutHelper.SCREEN_BORDER;
        return bottomBarY + (TopBarLayoutHelper.BOTTOM_SRC_H - SWITCHER_HEIGHT) / 2;
    }

    /** 模式切换器宽度（固定值，取三种模式中最宽文字，防止切换时背景跳变） */
    public int getWidth() {
        return fixedWidth;
    }

    // ======================== 模式图标获取 ========================

    /** 获取指定模式的图标精灵区域（未应用主题偏移） */
    private SpriteRegion getModeIconRegion(Mode mode) {
        return switch (mode) {
            case INTERACTIVE -> new SpriteRegion(
                    INTERACTIVE_TEX_INFO, 0, 0,
                    INTERACTIVE_TEX_INFO.halfWidth(), INTERACTIVE_TEX_INFO.halfHeight());
            case BUILD -> new SpriteRegion(
                    BUILD_TEX_INFO, 0, 0,
                    BUILD_TEX_INFO.halfWidth(), BUILD_TEX_INFO.halfHeight());
            case BLUEPRINT -> new SpriteRegion(
                    BLUEPRINT_TEX_INFO, 0, 0,
                    BLUEPRINT_TEX_INFO.halfWidth(), BLUEPRINT_TEX_INFO.halfHeight());
        };
    }

    /**
     * 按指定绘制高度等比例计算图标宽度。
     * <p>三种模式源图宽高比不同（交互: 64×96=2:3，建造/蓝图: 64×64=1:1），
     * 统一固定高度、宽度按源比例缩放可避免交互模式图标拉伸失真。</p>
     */
    private static int getIconDrawWidth(Mode mode, int drawH) {
        return switch (mode) {
            case INTERACTIVE -> drawH * INTERACTIVE_TEX_INFO.halfWidth() / INTERACTIVE_TEX_INFO.halfHeight();
            case BUILD -> drawH * BUILD_TEX_INFO.halfWidth() / BUILD_TEX_INFO.halfHeight();
            case BLUEPRINT -> drawH * BLUEPRINT_TEX_INFO.halfWidth() / BLUEPRINT_TEX_INFO.halfHeight();
        };
    }

    // ======================== 渲染 ========================

    /**
     * 渲染模式切换器（含背景、图标、文字、箭头、弹出菜单）。
     */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        int x = getX();
        int y = getY();
        int w = getWidth();

        // 悬浮检测
        boolean hovering = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SWITCHER_HEIGHT;
        hoverState.update(hovering);

        // 渲染背景（九宫格，交叉淡入淡出正常态↔悬浮态）
        NineSliceRegion bgNormal = MODE_BG_NINE_SLICE.withTheme();
        NineSliceRegion bgHover = MODE_BG_NINE_SLICE.withVOffset(MODE_BG_NORMAL_H).withTheme();
        hoverState.renderCrossFade(
                () -> RtsClientUiUtil.drawNineSliceRegion(g, bgNormal, x, y, w, SWITCHER_HEIGHT),
                () -> RtsClientUiUtil.drawNineSliceRegion(g, bgHover, x, y, w, SWITCHER_HEIGHT));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 渲染模式图标（固定槽位居中，垂直居中，确保文字起点一致）
        int iconH = ICON_SIZE;
        int iconW = getIconDrawWidth(currentMode, iconH);
        int iconX = x + PAD_H + (ICON_SIZE - iconW) / 2;
        int iconY = y + (SWITCHER_HEIGHT - iconH) / 2;
        SpriteRegion iconRegion = getModeIconRegion(currentMode).withTheme();
        RtsClientUiUtil.drawSprite(g, iconRegion, iconX, iconY, iconW, iconH);

        // 渲染模式文字（图标槽位右边固定偏移）
        int textX = x + PAD_H + ICON_SIZE + ICON_TEXT_GAP;
        int textY = iconY + (iconH - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        int textColor = ThemeManager.getTextColor();
        RtsClientUiUtil.drawUiText(g, currentMode.getDisplayName(), textX, textY, textColor);

        // 渲染折叠箭头（文字右边）
        int arrowX = textX + Minecraft.getInstance().font.width(currentMode.getDisplayName()) + TEXT_ARROW_GAP;
        int arrowY = y + (SWITCHER_HEIGHT - ARROW_SIZE) / 2;
        renderArrow(g, arrowX, arrowY);

        RenderSystem.disableBlend();
    }

    /**
     * 在面板渲染完成后单独渲染弹出菜单（避免被左边栏遮挡）。
     * <p>由 {@code TopBarPanel.renderOverlays()} 在左侧面板渲染之后调用。</p>
     */
    public void renderPopup(GuiGraphics g, int mouseX, int mouseY) {
        if (popup.isOpen()) {
            popup.setPosition(getX(), getY() + SWITCHER_HEIGHT);
            popup.render(g, mouseX, mouseY);
        }
    }

    /** 渲染折叠箭头（带旋转动画：闭合 0°，展开 90°） */
    private void renderArrow(GuiGraphics g, int x, int y) {
        this.arrowAnim.tick();
        SpriteRegion arrowRegion = new SpriteRegion(
                FOLD_ARROW_TEX_INFO, 0, 0, FOLD_ARROW_HALF_W, FOLD_ARROW_STATE_H).withTheme();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float half = ARROW_SIZE / 2.0f;
        g.pose().translate(half, half, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(this.arrowAnim.getValue() * 90.0f));
        g.pose().translate(-half, -half, 0);
        RtsClientUiUtil.drawSprite(g, arrowRegion, 0, 0, ARROW_SIZE, ARROW_SIZE);
        g.pose().popPose();
    }

    // ======================== 点击处理 ========================

    /**
     * 鼠标点击处理。
     *
     * @return true 表示事件被消费
     */
    public boolean mouseClicked(int mx, int my) {
        int x = getX();
        int y = getY();
        int w = getWidth();

        // 弹出菜单优先处理
        if (popup.isOpen()) {
            if (popup.contains(mx, my)) {
                return popup.handleClick(mx, my);
            }
            popup.close();
            arrowAnim.start(0.0f);
            return true;
        }

        // 点击模式切换器区域，切换弹出菜单
        if (mx >= x && mx < x + w && my >= y && my < y + SWITCHER_HEIGHT) {
            popup.toggle();
            arrowAnim.start(1.0f);
            return true;
        }

        return false;
    }

    // ======================== 弹出模式选择列表 ========================

    /**
     * 模式选择弹出列表——点击模式切换器后展开的下拉菜单。
     * <p>列表项顺序：交互模式 → 建造模式 → 蓝图模式。</p>
     */
    private static final class ModePopup extends BasePopup {

        private final ModeSwitcher switcher;

        ModePopup(ModeSwitcher switcher) {
            this.switcher = switcher;
            initAnims(Mode.values().length);
            // 计算各菜单项的内容宽度（固定图标槽位 + 间距 + 文字 + 间距 + 快捷键）
            var font = Minecraft.getInstance().font;
            Mode[] modes = Mode.values();
            int[] widths = new int[modes.length];
            int shortcutW = font.width(RtsKeyMappings.CYCLE_MODE_KEY.getTranslatedKeyMessage());
            for (int i = 0; i < modes.length; i++) {
                widths[i] = POPUP_ICON_SLOT_W + 4 + font.width(modes[i].getDisplayName()) + POPUP_SHORTCUT_GAP + shortcutW;
            }
            setItemContentWidths(widths);
        }

        @Override
        protected int getItemCount() {
            return Mode.values().length;
        }

        @Override
        protected int getItemHeight() {
            return POPUP_ITEM_HEIGHT;
        }

        @Override
        protected int getPadH() {
            return POPUP_PAD_H;
        }

        @Override
        protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
            Mode mode = Mode.values()[index];

            // 图标（固定槽位居中，确保文字起点一致）
            int iconH = POPUP_ICON_SIZE;
            int iconW = switcher.getIconDrawWidth(mode, iconH);
            int iconX = x + getPadH() + (POPUP_ICON_SLOT_W - iconW) / 2;
            int iconY = itemY + (getItemHeight() - iconH) / 2;
            SpriteRegion iconRegion = switcher.getModeIconRegion(mode).withTheme();
            RtsClientUiUtil.drawSprite(g, iconRegion, iconX, iconY, iconW, iconH);

            // 文字（从固定槽位右边偏移）
            int textColor = hoverT > 0.5f
                    ? ThemeManager.getHoverTextColor()
                    : ThemeManager.getTextColor();
            String label = mode.getDisplayName().getString();
            int textX = x + getPadH() + POPUP_ICON_SLOT_W + 4;
            int textY = iconY + (iconH - Minecraft.getInstance().font.lineHeight) / 2 + 1;
            RtsClientUiUtil.drawUiText(g, label, textX, textY, textColor);

            // 快捷键（靠右边缘对齐，颜色更暗/弱，不受悬浮影响）
            int shortcutColor = ThemeManager.getInstance().isLightMode() ? LIGHT_SHORTCUT_COLOR : DARK_SHORTCUT_COLOR;
            String shortcutLabel = RtsKeyMappings.CYCLE_MODE_KEY.getTranslatedKeyMessage().getString();
            int shortcutX = x + getPopupWidth() - getPadH() - Minecraft.getInstance().font.width(shortcutLabel);
            RtsClientUiUtil.drawUiText(g, shortcutLabel, shortcutX, textY, shortcutColor);
        }

        @Override
        protected boolean onItemClick(int index) {
            Mode selectedMode = Mode.values()[index];
            if (selectedMode != switcher.currentMode) {
                switcher.setMode(selectedMode);
            }
            close();
            return true;
        }
    }
}
