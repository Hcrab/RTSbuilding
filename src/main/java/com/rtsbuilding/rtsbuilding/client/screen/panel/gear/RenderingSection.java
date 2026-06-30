package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelectionPass;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ThemeSwitchComponent;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 渲染设置折叠分区——在设置面板中管理"渲染设置"分区的渲染和交互。
 */
public class RenderingSection extends SettingsSection {

    private static final double ALPHA_MIN = 0.02;
    private static final double ALPHA_MAX = 0.80;

    /** 最小内容区高度（流动动画 + 线框平滑 + UI平滑 + 穿透层 4 行） */
    private static final int MIN_CONTENT_H = 70;  // 4 * 16 + 6
    /** 额外 2 行的高度（透明度标签 + 滑条） */
    private static final int EXTRA_ROWS_H = 32;   // 2 * 16

    private final ThemeSwitchComponent depthToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent flowToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent smoothToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent uiSmoothToggle = new ThemeSwitchComponent();
    private final ScaleSliderComponent alphaSlider = new ScaleSliderComponent();
    /** 透明度滑条轨道位置缓存 */
    private final SliderTrack alphaTrack = new SliderTrack();

    /** 内容区高度平滑动画器 */
    private final SmoothAnimator heightAnim = AnimationFactory.createExpandAnim();
    private boolean lastDepthEnabled;

    public RenderingSection() {
        super("screen.rtsbuilding.settings.category.rendering");
        setExpanded(false);
        lastDepthEnabled = BoxSelectionPass.depthTestEnabled;
        heightAnim.snapTo(lastDepthEnabled ? 1.0f : 0.0f);
    }

    @Override
    protected int getContentRowCount() {
        return 6;
    }

    @Override
    protected int getEffectiveContentHeight() {
        return MIN_CONTENT_H + Math.round(EXTRA_ROWS_H * heightAnim.getValue());
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String[] lines) {
        // 驱动内容区高度动画
        heightAnim.tick();
        boolean depthOn = BoxSelectionPass.depthTestEnabled;
        if (depthOn != lastDepthEnabled) {
            lastDepthEnabled = depthOn;
            heightAnim.start(depthOn ? 1.0f : 0.0f);
        }

        // 线框流动动画开关（第一行，始终可见）
        renderLabel(g, Component.translatable("screen.rtsbuilding.settings.flow_animation").getString(), x, y, 0);
        renderToggle(g, mouseX, mouseY, x, y, w, 0, flowToggle, BoxSelectionPass.flowAnimationEnabled);

        // 线框平滑动画开关（第二行，始终可见）
        renderLabel(g, Component.translatable("screen.rtsbuilding.settings.smooth_animation").getString(), x, y, 1);
        renderToggle(g, mouseX, mouseY, x, y, w, 1, smoothToggle, CornerBracketRenderer.SmoothTarget.enabled);

        // UI 平滑动画开关（第三行，始终可见）
        renderLabel(g, Component.translatable("screen.rtsbuilding.settings.ui_smooth_animation").getString(), x, y, 2);
        renderToggle(g, mouseX, mouseY, x, y, w, 2, uiSmoothToggle, SmoothAnimator.enabled);

        // 穿透层开关（第四行，始终可见——主依赖条目）
        renderLabel(g, Component.translatable("screen.rtsbuilding.settings.depth_test").getString(), x, y, 3);
        renderToggle(g, mouseX, mouseY, x, y, w, 3, depthToggle, BoxSelectionPass.depthTestEnabled);

        // 线框透明度标签（第五行）和滑条（第六行）——由内容区高度动画控制显隐
        String alphaLabel = Component.translatable("screen.rtsbuilding.settings.overlay_alpha").getString();
        renderLabel(g, alphaLabel, x, y, 4);
        renderSliderTrack(g, mouseX, x, y, w, 5, alphaSlider, alphaTrack,
                ALPHA_MIN, ALPHA_MAX, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA);
    }

    /** 在指定行渲染滑条轨道（不含标签），轨道占满整行宽度 */
    private void renderSliderTrack(GuiGraphics g, int mouseX, int x, int y, int w, int row,
                                    ScaleSliderComponent slider, SliderTrack trackPos,
                                    double min, double max, double value) {
        int lineCenterY = rowY(y, row) + getLineHeight() / 2;
        trackPos.trackX = x + LEFT_PAD;
        trackPos.trackY = lineCenterY - 2;
        trackPos.trackW = w - LEFT_PAD - RIGHT_PAD;
        trackPos.slider = slider;
        slider.render(g, mouseX, 0, trackPos.trackX, trackPos.trackY, trackPos.trackW,
                min, max, value);
    }

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        if (smoothToggle.handleClick(mouseX, mouseY)) {
            CornerBracketRenderer.SmoothTarget.enabled = !CornerBracketRenderer.SmoothTarget.enabled;
            return true;
        }
        if (flowToggle.handleClick(mouseX, mouseY)) {
            BoxSelectionPass.flowAnimationEnabled = !BoxSelectionPass.flowAnimationEnabled;
            return true;
        }
        if (uiSmoothToggle.handleClick(mouseX, mouseY)) {
            SmoothAnimator.enabled = !SmoothAnimator.enabled;
            return true;
        }
        if (depthToggle.handleClick(mouseX, mouseY)) {
            BoxSelectionPass.depthTestEnabled = !BoxSelectionPass.depthTestEnabled;
            return true;
        }
        if (BoxSelectionPass.depthTestEnabled) {
            Double newVal = alphaSlider.handleClick(mouseX, mouseY,
                    alphaTrack.trackX, alphaTrack.trackY, alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
            if (newVal != null) {
                CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = newVal.floatValue();
                return true;
            }
        }
        return false;
    }

    // ======================== 滑条拖拽 ========================

    public boolean isSliderDragging() {
        return BoxSelectionPass.depthTestEnabled && alphaSlider.isDragging();
    }

    public void handleSliderDrag(double mouseX) {
        if (alphaSlider.isDragging() && alphaTrack.trackW > 0) {
            double val = alphaSlider.handleDrag(mouseX, alphaTrack.trackX, alphaTrack.trackW,
                    ALPHA_MIN, ALPHA_MAX);
            CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = (float) val;
        }
    }

    public void endSliderDrag() {
        alphaSlider.endDrag();
    }

    public boolean handleSliderScroll(double mouseX, double mouseY, double scrollY) {
        Double newVal = alphaSlider.handleScroll(mouseX, mouseY, scrollY,
                alphaTrack.trackX, alphaTrack.trackY, alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
        if (newVal != null) {
            CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = newVal.floatValue();
            return true;
        }
        return false;
    }
}
