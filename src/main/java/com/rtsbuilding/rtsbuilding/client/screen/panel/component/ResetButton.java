package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 重置按钮组件——在每个设置条目右侧渲染一个重置图标按钮，点击后恢复该条目的默认值。
 *
 * <p>使用 reset.png 贴图：128×128，水平双主题布局（左暗右亮），
 * 0-64 为正常态，64-128 为悬浮态，按钮绘制尺寸为 24×24。</p>
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

    // ======================== 贴图规格 ========================

    /** reset.png：128×128，水平左暗右亮，垂直上正常下悬浮 */
    private static final ResourceLocation RESET_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/reset.png");
    private static final int TEX_SIZE = 128;
    /** 单个状态尺寸（0-64 = 正常态，64-128 = 悬浮态） */
    private static final int STATE_SIZE = 64;
    /** 按钮绘制尺寸 */
    public static final int BTN_SIZE = 12;

    private static final TextureInfo RESET_TEX_INFO = new TextureInfo(
            RESET_TEXTURE, TEX_SIZE, TEX_SIZE,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.NORMAL);

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

        // 2) 交叉淡入淡出正常态 / 悬浮态
        SpriteRegion normalRegion = new SpriteRegion(RESET_TEX_INFO, 0, 0, STATE_SIZE, STATE_SIZE);
        SpriteRegion hoverRegion = new SpriteRegion(RESET_TEX_INFO, 0, STATE_SIZE, STATE_SIZE, STATE_SIZE);

        CrossFadeRenderer.render(t,
                () -> SpriteRenderer.drawSprite(g, normalRegion.withTheme(), btnX, btnY, BTN_SIZE, BTN_SIZE),
                () -> SpriteRenderer.drawSprite(g, hoverRegion.withTheme(), btnX, btnY, BTN_SIZE, BTN_SIZE));
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
