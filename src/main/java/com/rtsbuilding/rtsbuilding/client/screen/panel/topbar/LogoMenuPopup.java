package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Logo 图标点击后弹出的下拉菜单列表。
 *
 * <p>在 Logo 下方展开，使用九宫格面板背景，每个菜单项支持悬浮高亮动画和点击回调。</p>
 */
public final class LogoMenuPopup {

    /** 单个菜单项：显示文本 + 点击回调 */
    public record MenuItem(Component label, Runnable action) {}

    private final List<MenuItem> items;
    private boolean open;
    private int x;
    private int y;

    // ======================== 外观常量 ========================

    /** 菜单项高度 */
    private static final int ITEM_HEIGHT = 22;
    /** 菜单左右边缘与内容的 padding */
    private static final int PAD_H = 6;
    /** 菜单上下边框与内容区域的 padding */
    private static final int POPUP_PAD_Y = 4;
    /** 菜单宽度 */
    private static final int POPUP_WIDTH = 120;
    /** 菜单项悬浮背景插值——起始（无背景） */
    private static final int ITEM_BG_NORMAL = 0x00000000;
    /** 菜单项悬浮背景插值——终点（半透明深色） */
    private static final int ITEM_BG_HOVER = 0x442A3442;

    // ======================== 设置图标贴图 ========================

    /** 设置图标贴图（1024×512，横向双主题，左半=暗色，右半=亮色） */
    private static final ResourceLocation SETTING_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/top/setting.png");
    private static final int SETTING_TEX_W = 1024;
    private static final int SETTING_TEX_H = 512;
    /** 单主题半区宽度 */
    private static final int SETTING_HALF_W = 512;
    /** 图标绘制尺寸 */
    private static final int SETTING_ICON_SIZE = 17;

    // ======================== 动画器 ========================

    /** 每个菜单项独立的悬浮动画器 */
    private final List<SmoothAnimator> hoverAnims;
    /** 上一帧悬浮的菜单项 index */
    private int lastHoveredIndex = -1;

    /**
     * @param items 菜单项列表，按从上到下的顺序渲染
     */
    public LogoMenuPopup(List<MenuItem> items) {
        this.items = new ArrayList<>(items);
        this.hoverAnims = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            hoverAnims.add(AnimationFactory.createHoverAnim());
        }
    }

    // ======================== 位置 ========================

    /** 设置弹出菜单左上角位置（通常为 Logo 左下角） */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // ======================== 打开/关闭 ========================

    /** 切换打开/关闭状态 */
    public void toggle() {
        this.open = !this.open;
        if (!this.open) {
            // 关闭时重置所有悬浮动画
            for (SmoothAnimator anim : hoverAnims) {
                anim.snapTo(0.0f);
            }
            lastHoveredIndex = -1;
        }
    }

    /** 打开菜单 */
    public void open() {
        if (!this.open) toggle();
    }

    /** 关闭菜单 */
    public void close() {
        if (this.open) toggle();
    }

    /** 菜单是否处于打开状态 */
    public boolean isOpen() {
        return open;
    }

    // ======================== 尺寸与命中检测 ========================

    private int menuWidth() {
        return POPUP_WIDTH;
    }

    private int menuHeight() {
        return POPUP_PAD_Y * 2 + items.size() * ITEM_HEIGHT;
    }

    /** 检测像素坐标是否在弹出菜单区域内 */
    public boolean contains(int mx, int my) {
        if (!open) return false;
        return mx >= x && mx < x + menuWidth()
                && my >= y && my < y + menuHeight();
    }

    /** 获取指定菜单项的 Y 坐标 */
    private int itemY(int index) {
        return y + POPUP_PAD_Y + index * ITEM_HEIGHT;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染弹出菜单。
     * <p>在 TopBarPanel 的 render() 末尾调用。</p>
     */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!open) return;

        // 启用 blend 以支持半透明背景
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int pw = menuWidth();
        int ph = menuHeight();

        // 1) 九宫格面板背景
        RtsClientUiUtil.drawNineSlicePanel(g, x, y, pw, ph, false);

        // 2) 检测当前悬浮项
        int hoveredIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            int iy = itemY(i);
            if (mouseX >= x + PAD_H && mouseX < x + pw - PAD_H
                    && mouseY >= iy && mouseY < iy + ITEM_HEIGHT) {
                hoveredIndex = i;
                break;
            }
        }

        // 3) 悬浮状态变更 → 启动动画
        if (hoveredIndex != lastHoveredIndex) {
            if (lastHoveredIndex >= 0 && lastHoveredIndex < hoverAnims.size()) {
                hoverAnims.get(lastHoveredIndex).start(0.0f);
            }
            if (hoveredIndex >= 0 && hoveredIndex < hoverAnims.size()) {
                hoverAnims.get(hoveredIndex).start(1.0f);
            }
            lastHoveredIndex = hoveredIndex;
        }

        // 4) 推进所有动画
        for (SmoothAnimator anim : hoverAnims) {
            anim.tick();
        }

        // 5) 绘制每个菜单项
        for (int i = 0; i < items.size(); i++) {
            int iy = itemY(i);
            float t = hoverAnims.get(i).getValue();

            // 悬浮背景（从完全透明到半透明深色）
            if (t > 0.001f) {
                int bgColor = SmoothAnimator.lerpColor(ITEM_BG_NORMAL, ITEM_BG_HOVER, t);
                g.fill(x + PAD_H, iy, x + pw - PAD_H, iy + ITEM_HEIGHT, bgColor);
            }

            // 设置图标（精灵图画法，双主题横向偏移）
            int themeU = ThemeManager.getInstance().themeU(SETTING_HALF_W);
            int iconX = x + PAD_H;
            int iconY = iy + (ITEM_HEIGHT - SETTING_ICON_SIZE) / 2;
            RtsClientUiUtil.drawScaledImage(g, SETTING_TEXTURE,
                    iconX, iconY, SETTING_ICON_SIZE, SETTING_ICON_SIZE,
                    themeU, 0,
                    SETTING_HALF_W, SETTING_TEX_H,
                    SETTING_TEX_W, SETTING_TEX_H);

            // 文字（跟在图标后面）
            int textColor = t > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
            String label = items.get(i).label().getString();
            int textX = iconX + SETTING_ICON_SIZE + 4;
            int textY = iconY + (SETTING_ICON_SIZE - Minecraft.getInstance().font.lineHeight) / 2 + 1;
            RtsClientUiUtil.drawUiText(g, label, textX, textY, textColor);
        }
    }

    // ======================== 点击处理 ========================

    /**
     * 处理鼠标点击。
     *
     * @return true 表示事件被消费（点击到了某个菜单项）
     */
    public boolean handleClick(int mx, int my) {
        if (!open) return false;

        for (int i = 0; i < items.size(); i++) {
            int iy = itemY(i);
            if (mx >= x + PAD_H && mx < x + menuWidth() - PAD_H
                    && my >= iy && my < iy + ITEM_HEIGHT) {
                // 先关闭再执行回调，避免递归问题
                close();
                items.get(i).action().run();
                return true;
            }
        }
        return false;
    }
}
