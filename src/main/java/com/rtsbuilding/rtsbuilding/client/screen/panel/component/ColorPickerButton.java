package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorSource;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 调色盘按钮组件——渲染一个带颜色轮盘图标的按钮，点击后切换调色盘面板显隐。
 *
 * <p>按钮背景使用 fold_ui.png 九宫格精灵图（32×32，水平左暗右亮双主题），
 * 0-16 为正常态、16-32 为悬浮态，通过 {@link HoverStateManager} 管理
 * 悬浮动画过渡。图标居中绘制在九宫格背景之上。</p>
 *
 * <p>点击区域在渲染时自动缓存，点击检测无需重复传入坐标。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * ColorPickerButton btn = new ColorPickerButton();
 * btn.setColorPickerPanel(colorPickerPanel);
 *
 * // 渲染（自动缓存点击区域+悬浮检测）
 * btn.render(g, mouseX, mouseY, btnX, btnY);
 *
 * // 点击检测
 * if (btn.handleClick(mouseX, mouseY)) {
 *     // 面板已自动切换
 * }
 * }</pre>
 */
public class ColorPickerButton {

    // ======================== 按钮背景贴图 (fold_ui.png) ========================

    /** fold_ui.png：32×32，水平左暗右亮，垂直上正常下悬浮 */
    private static final ResourceLocation FOLD_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/fold_ui.png");
    private static final int FOLD_TEX_W = 32;
    private static final int FOLD_TEX_FILE_H = 32;
    /** 单个状态高度（0-16=正常态，16-32=悬浮态） */
    private static final int FOLD_TEX_STATE_H = 16;
    /** 九宫格边框宽度 */
    private static final int FOLD_BORDER = 4;
    private static final TextureInfo FOLD_TEX_INFO = new TextureInfo(
            FOLD_TEXTURE, FOLD_TEX_W, FOLD_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion FOLD_NINE_SLICE = NineSliceRegion.fullTheme(
            FOLD_TEX_INFO, FOLD_TEX_STATE_H, FOLD_BORDER);

    /** colorwheel.png：89×89 单帧精灵图 */
    private static final ResourceLocation COLOR_WHEEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/color/colorwheel.png");
    private static final int COLOR_WHEEL_TEX_W = 89;
    private static final int COLOR_WHEEL_TEX_H = 89;
    /** 图标绘制尺寸 */
    private static final int COLOR_WHEEL_FRAME = 12;

    /** 颜色轮盘贴图元数据（避免每帧 new） */
    private static final TextureInfo COLOR_WHEEL_TEX_INFO = new TextureInfo(
            COLOR_WHEEL_TEXTURE, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H,
            TextureInfo.ThemeLayout.NONE, TextureInfo.FilterMode.NORMAL);

    /** 按钮尺寸（宽=高） */
    public static final int BTN_SIZE = 16;

    // ======================== 内部状态 ========================

    /** 悬浮状态管理器 */
    private final HoverStateManager hoverState = new HoverStateManager();

    /** 点击区域缓存 */
    private int areaX, areaY;

    /** 关联的调色盘面板 */
    private ColorPickerPanel colorPickerPanel;

    /** 点击时传递给调色盘面板的颜色源 */
    @Nullable
    private ColorSource colorSource;

    /** 点击时传递给调色盘面板的颜色组（优先级高于 colorSource） */
    @Nullable
    private ColorGroup colorGroup;

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
     * 设置点击按钮时传递给调色盘面板的颜色源。
     * <p>打开调色盘时，面板会自动加载此源的当前颜色并编辑，修改实时写回。</p>
     */
    public void setColorSource(@Nullable ColorSource source) {
        this.colorSource = source;
        this.colorGroup = null;
    }

    /**
     * 设置点击按钮时传递给调色盘面板的颜色组。
     * <p>打开调色盘时，面板会显示组内所有条目的色块，点击即可切换编辑目标。
     * 适用于需要同时编辑多个相关颜色的场景。</p>
     */
    public void setColorGroup(@Nullable ColorGroup group) {
        this.colorGroup = group;
        this.colorSource = null;
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
     * 渲染调色盘按钮，同时自动缓存点击区域并更新悬浮状态。
     *
     * @param g      渲染上下文
     * @param mouseX 鼠标 X（用于悬浮检测）
     * @param mouseY 鼠标 Y（用于悬浮检测）
     * @param btnX   按钮左上角 X
     * @param btnY   按钮左上角 Y
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, int btnX, int btnY) {
        this.areaX = btnX;
        this.areaY = btnY;

        // 1) 更新悬浮状态
        boolean hovering = mouseX >= btnX && mouseX < btnX + BTN_SIZE
                && mouseY >= btnY && mouseY < btnY + BTN_SIZE;
        float t = this.hoverState.update(hovering);

        // 2) 九宫格背景（fold_ui.png）——正常态(vOffset=0) / 悬浮态(vOffset=16) 交叉淡入淡出
        CrossFadeRenderer.render(t,
                () -> renderBackground(g, btnX, btnY, 0),
                () -> renderBackground(g, btnX, btnY, FOLD_TEX_STATE_H));

        // 3) 上方精灵图颜色轮盘 — 89×89 原图缩放到图标尺寸
        int iconX = btnX + (BTN_SIZE - COLOR_WHEEL_FRAME) / 2;
        int iconY = btnY + (BTN_SIZE - COLOR_WHEEL_FRAME) / 2;
        SpriteRegion wheelRegion = new SpriteRegion(COLOR_WHEEL_TEX_INFO, 0, 0, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H);
        SpriteRenderer.drawSprite(g, wheelRegion,
                iconX, iconY, COLOR_WHEEL_FRAME, COLOR_WHEEL_FRAME);
    }

    /**
     * 渲染单一状态的九宫格背景贴图。
     *
     * @param vOffset 源 Y 偏移（0=正常态，FOLD_TEX_STATE_H=悬浮态）
     */
    private void renderBackground(GuiGraphics g, int btnX, int btnY, int vOffset) {
        SpriteRenderer.drawNineSlice(g,
                FOLD_NINE_SLICE.withTheme().withVOffset(vOffset),
                btnX, btnY, BTN_SIZE, BTN_SIZE);
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
                if (!colorPickerPanel.isOpen()) {
                    // 打开前设颜色组/源，面板自动加载对应颜色
                    applyColor();
                    if (parentPanel != null) {
                        parentPanel.openChild(colorPickerPanel);
                    } else {
                        colorPickerPanel.setOpen(true);
                    }
                } else {
                    // 面板已打开时切换颜色组/源，不关闭面板
                    applyColor();
                }
            }
            return true;
        }
        return false;
    }

    /** 将当前绑定的颜色组/源应用到调色盘面板 */
    private void applyColor() {
        if (colorGroup != null) {
            colorPickerPanel.setColorGroup(colorGroup);
        } else if (colorSource != null) {
            colorPickerPanel.setColorSource(colorSource);
        }
    }
}
