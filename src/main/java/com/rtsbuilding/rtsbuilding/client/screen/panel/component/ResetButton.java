package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 重置按钮组件——在每个设置条目右侧渲染一个重置图标按钮，点击后恢复该条目的默认值。
 *
 * <p>使用 base_ui_2.png 九宫格作为按钮背景（0-16=正常，16-32=悬浮），
 * 再叠加上 reset.png（128×64，水平双主题）作为重置图标，按钮绘制尺寸为 16×16。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * private final ResetButton resetBtn = new ResetButton();
 *
 * // 设置重置回调
 * resetBtn.setResetAction(() -> { ... });
 *
 * // 渲染（自动缓存点击区域）
 * resetBtn.render(g, mouseX, mouseY, btnX, btnY);
 *
 * // 点击检测
 * if (resetBtn.handleClick(mouseX, mouseY)) { ... }
 * }</pre>
 */
public class ResetButton {

    // ======================== 按钮背景贴图（base_ui_2.png）=======================

    /** base_ui_2.png：32×48，水平双主题，Y轴仅使用0-32，0-16=正常，16-32=悬浮 */
    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final int BASE_TEX_W = 32;
    private static final int BASE_TEX_H = 48;
    /** 单状态高度（0-16=正常，16-32=悬浮） */
    private static final int BASE_STATE_H = 16;
    private static final int BASE_BORDER = 4;
    private static final TextureInfo BASE_TEX_INFO = new TextureInfo(
            BASE_TEXTURE, BASE_TEX_W, BASE_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion BASE_NINE_SLICE = NineSliceRegion.fullTheme(
            BASE_TEX_INFO, BASE_STATE_H, BASE_BORDER);

    // ======================== 重置图标贴图（reset.png）=======================

    /** reset.png：128×64，水平双主题（左暗右亮） */
    private static final ResourceLocation RESET_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/reset.png");
    private static final int RESET_TEX_W = 128;
    private static final int RESET_TEX_H = 64;
    private static final TextureInfo RESET_TEX_INFO = new TextureInfo(
            RESET_TEXTURE, RESET_TEX_W, RESET_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.NORMAL);
    /** 重置图标区域（半区64×64） */
    private static final SpriteRegion RESET_SPRITE = new SpriteRegion(
            RESET_TEX_INFO, 0, 0, RESET_TEX_W / 2, RESET_TEX_H);

    // ======================== 按钮尺寸 ========================

    /** 按钮绘制尺寸（与切换按钮滑块高度对齐） */
    public static final int BTN_SIZE = 16;

    // ======================== 内部状态 ========================

    /** 悬浮状态管理器 */
    private final HoverStateManager hoverState = new HoverStateManager();

    /** 点击区域缓存 */
    private int areaX, areaY;

    /** 重置回调 */
    private Runnable resetAction;

    // ======================== 注入 ========================

    /**
     * 设置点击按钮时的重置回调。
     */
    public void setResetAction(Runnable action) {
        this.resetAction = action;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染重置按钮，同时自动缓存点击区域并更新悬浮状态。
     *
     * @param g     渲染上下文
     * @param mx    鼠标 X
     * @param my    鼠标 Y
     * @param btnX  按钮左上角 X
     * @param btnY  按钮左上角 Y
     */
    public void render(GuiGraphics g, int mx, int my, int btnX, int btnY) {
        this.areaX = btnX;
        this.areaY = btnY;

        // 1) 更新悬浮状态
        boolean hovering = mx >= btnX && mx < btnX + BTN_SIZE
                && my >= btnY && my < btnY + BTN_SIZE;
        float t = this.hoverState.update(hovering);

        // 2) 绘制按钮背景（base_ui_2.png 九宫格，正常/悬浮交叉淡入淡出）
        CrossFadeRenderer.render(t,
                () -> SpriteRenderer.drawNineSlice(g, BASE_NINE_SLICE.withTheme(), btnX, btnY, BTN_SIZE, BTN_SIZE),
                () -> SpriteRenderer.drawNineSlice(g, BASE_NINE_SLICE.withTheme().withVOffset(BASE_STATE_H), btnX, btnY, BTN_SIZE, BTN_SIZE));

        // 3) 绘制重置图标（reset.png）叠在背景上方
        SpriteRenderer.drawSprite(g, RESET_SPRITE.withTheme(), btnX, btnY, BTN_SIZE, BTN_SIZE);
    }

    // ======================== 交互 ========================

    /**
     * 检测是否点击了按钮区域，若点击则执行重置回调。
     *
     * @param mx 鼠标 X
     * @param my 鼠标 Y
     * @return true 如果点击了按钮区域
     */
    public boolean handleClick(double mx, double my) {
        if (mx >= areaX && mx < areaX + BTN_SIZE
                && my >= areaY && my < areaY + BTN_SIZE) {
            if (resetAction != null) {
                resetAction.run();
            }
            return true;
        }
        return false;
    }
}
