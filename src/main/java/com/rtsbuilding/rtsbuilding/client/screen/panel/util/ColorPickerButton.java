package com.rtsbuilding.rtsbuilding.client.screen.panel.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 调色盘按钮组件——渲染一个带颜色轮盘图标的按钮，点击后切换调色盘面板显隐。
 *
 * <p>按钮背景使用九宫格浮动面板，图标居中绘制。点击区域在渲染时自动缓存，
 * 点击检测无需重复传入坐标。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * ColorPickerButton btn = new ColorPickerButton();
 * btn.setColorPickerPanel(colorPickerPanel);
 *
 * // 渲染（自动缓存点击区域）
 * btn.render(g, btnX, btnY);
 *
 * // 点击检测
 * if (btn.handleClick(mouseX, mouseY)) {
 *     // 面板已自动切换
 * }
 * }</pre>
 */
public class ColorPickerButton {

    /** colorwheel.png：89×89 单帧精灵图 */
    private static final ResourceLocation COLOR_WHEEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/color/colorwheel.png");
    private static final int COLOR_WHEEL_TEX_W = 89;
    private static final int COLOR_WHEEL_TEX_H = 89;
    /** 图标绘制尺寸 */
    private static final int COLOR_WHEEL_FRAME = 12;

    /** 按钮尺寸（宽=高） */
    public static final int BTN_SIZE = 16;

    // ======================== 内部状态 ========================

    /** 点击区域缓存 */
    private int areaX, areaY;

    /** 关联的调色盘面板 */
    private ColorPickerPanel colorPickerPanel;

    /** 父面板引用——唤出调色盘的面板（如 GearMenuPanel），用于建立父子层级关系 */
    private RtsPanel parentPanel;

    // ======================== 注入 ========================

    /**
     * 注入调色盘面板引用，点击按钮时切换其显隐。
     */
    public void setColorPickerPanel(ColorPickerPanel panel) {
        this.colorPickerPanel = panel;
    }

    /**
     * 设置父面板引用。
     * <p>当点击按钮打开调色盘面板时，自动通过 {@link RtsPanel#openChild(RtsPanel)}
     * 建立父子层级关系，使父面板关闭时自动关闭调色盘面板。</p>
     */
    public void setParentPanel(RtsPanel parent) {
        this.parentPanel = parent;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染调色盘按钮，同时自动缓存点击区域。
     *
     * @param g     渲染上下文
     * @param btnX  按钮左上角 X
     * @param btnY  按钮左上角 Y
     */
    public void render(GuiGraphics g, int btnX, int btnY) {
        this.areaX = btnX;
        this.areaY = btnY;

        // 九宫格浮动背景
        RenderSystem.enableBlend();
        RtsClientUiUtil.drawNineSliceFloatingPanel(g, btnX, btnY, BTN_SIZE, BTN_SIZE);

        // 上方精灵图颜色轮盘 — 89×89 原图缩放到图标尺寸
        int iconX = btnX + (BTN_SIZE - COLOR_WHEEL_FRAME) / 2;
        int iconY = btnY + (BTN_SIZE - COLOR_WHEEL_FRAME) / 2;
        RtsClientUiUtil.drawHighQualityImage(g, COLOR_WHEEL_TEXTURE,
                iconX, iconY, COLOR_WHEEL_FRAME, COLOR_WHEEL_FRAME,
                0, 0, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H,
                COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H);
        RenderSystem.disableBlend();
    }

    // ======================== 交互 ========================

    /**
     * 检测是否点击了按钮区域，若点击则切换调色盘面板显隐。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return true 如果点击了按钮区域且面板引用已设置
     */
    public boolean handleClick(double mouseX, double mouseY) {
        if (mouseX >= areaX && mouseX < areaX + BTN_SIZE
                && mouseY >= areaY && mouseY < areaY + BTN_SIZE) {
            if (colorPickerPanel != null) {
                if (!colorPickerPanel.isOpen() && parentPanel != null) {
                    // 通过父面板打开——自动建立双向父子关系
                    parentPanel.openChild(colorPickerPanel);
                } else {
                    colorPickerPanel.toggleOpen();
                }
            }
            return true;
        }
        return false;
    }
}
