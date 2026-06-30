package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoundaryPass;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelectionPass;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ThemeSwitchComponent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ColorBlockComponent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ColorPickerButton;
import net.minecraft.util.Mth;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 渲染设置折叠分区——在设置面板中管理"渲染设置"分区的渲染和交互。
 */
public class RenderingSection extends SettingsSection {

    private static final double ALPHA_MIN = 0.02;
    private static final double ALPHA_MAX = 0.80;

    /** 颜色块与标签间距 */
    private static final int COLOR_BLOCK_GAP = 4;

    // ======================== 行号（布局顺序）=======================
    //
    //  行号反映自然视觉排列，条件行紧跟在被依赖行下方。
    //  渲染时手动跳过条件行（而非依赖固定行索引），后续行自动上移。
    // =============================================================

    private static final int ROW_FLOW = 0;
    private static final int ROW_SMOOTH = 1;
    private static final int ROW_UI_SMOOTH = 2;
    private static final int ROW_DEPTH = 3;          // 主依赖条目
    private static final int ROW_BARRIER_COLOR = 5;
    /** 始终可见的行数（穿透层及以上的行） */
    private static final int ALWAYS_VISIBLE_ROW_COUNT = 4;
    /** 始终可见 + 屏障颜色高度（4 + 1 行） */
    private static final int MIN_CONTENT_H = 106;  // 5 * 20 + 6
    /** 依赖穿透层的条件行高度（透明度标签+滑条合并为一行） */
    private static final int EXTRA_ROWS_H = 20;   // 1 * 20

    private final ThemeSwitchComponent depthToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent flowToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent smoothToggle = new ThemeSwitchComponent();
    private final ThemeSwitchComponent uiSmoothToggle = new ThemeSwitchComponent();
    private final ScaleSliderComponent alphaSlider = new ScaleSliderComponent();
    /** 透明度滑条轨道位置缓存 */
    private final SliderTrack alphaTrack = new SliderTrack();

    /** 调色盘按钮组件 */
    private final ColorPickerButton colorPickerButton = new ColorPickerButton();
    /** 颜色块组件 */
    private final ColorBlockComponent colorBlock = new ColorBlockComponent();

    /** 缓存的翻译文本（避免每帧 Component.translatable() */
    private String cachedFlowLabel;
    private String cachedSmoothLabel;
    private String cachedUiSmoothLabel;
    private String cachedDepthLabel;
    private String cachedAlphaLabel;
    private String cachedBarrierLabel;

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
        return ALWAYS_VISIBLE_ROW_COUNT + 2; // 始终可见4行 + 屏障颜色1行 + 条件行1行
    }

    @Override
    protected int getEffectiveContentHeight() {
        // 动态计算：始终可见行（含屏障颜色）+ 条件行高度（由动画控制）
        return MIN_CONTENT_H + Math.round(EXTRA_ROWS_H * heightAnim.getValue());
    }

    /** 基于 cursorY 渲染标签行（替代父类的 renderLabel + 固定行索引） */
    private void renderRowLabel(GuiGraphics g, String text, int x, int lineY) {
        RtsClientUiUtil.drawUiText(g, text, x + LEFT_PAD, lineY + 2, getTextColor());
    }

    /** 基于 cursorY 渲染开关行——开关中心对齐文字中心 */
    private void renderRowToggle(GuiGraphics g, int mx, int my, int x, int w, int lineY,
                                  ThemeSwitchComponent toggle, boolean state) {
        int textCenterY = lineY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int toggleY = textCenterY - ThemeSwitchComponent.SIZE / 2;
        toggle.render(g, mx, my, x + w - ThemeSwitchComponent.SIZE - RIGHT_PAD, toggleY, state);
    }

    /** 基于 cursorY 渲染标签 + 滑条（合并同一行，滑条紧挨标签右侧） */
    private void renderRowSlider(GuiGraphics g, int mx, int my, int x, int w, int lineY,
                                  String label, ScaleSliderComponent slider, SliderTrack trackPos,
                                  double min, double max, double value) {
        RtsClientUiUtil.drawUiText(g, label, x + LEFT_PAD, lineY + 2, getTextColor());
        int textW = Minecraft.getInstance().font.width(label);
        int centerY = lineY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        trackPos.trackX = x + LEFT_PAD + textW + SLIDER_GAP;
        trackPos.trackY = centerY - 2;
        trackPos.trackW = Mth.clamp(w - LEFT_PAD - RIGHT_PAD - textW - SLIDER_GAP, 20, w - LEFT_PAD - RIGHT_PAD);
        trackPos.slider = slider;
        slider.render(g, mx, my, trackPos.trackX, trackPos.trackY, trackPos.trackW, min, max, value);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int lineCount) {
        // 驱动内容区高度动画
        heightAnim.tick();
        boolean depthOn = BoxSelectionPass.depthTestEnabled;
        if (depthOn != lastDepthEnabled) {
            lastDepthEnabled = depthOn;
            heightAnim.start(depthOn ? 1.0f : 0.0f);
        }

        int lineH = getLineHeight();
        // cursorY：基于当前绘制位置，条件行跳过时后续行自动上移
        int cursorY = y + 4;

        // ---- 始终可见行 ----

        renderRowLabel(g, getFlowLabel(), x, cursorY);
        renderRowToggle(g, mouseX, mouseY, x, w, cursorY, flowToggle, BoxSelectionPass.flowAnimationEnabled);
        cursorY += lineH;

        renderRowLabel(g, getSmoothLabel(), x, cursorY);
        renderRowToggle(g, mouseX, mouseY, x, w, cursorY, smoothToggle, CornerBracketRenderer.SmoothTarget.enabled);
        cursorY += lineH;

        renderRowLabel(g, getUiSmoothLabel(), x, cursorY);
        renderRowToggle(g, mouseX, mouseY, x, w, cursorY, uiSmoothToggle, SmoothAnimator.enabled);
        cursorY += lineH;

        // 穿透层（主依赖条目）
        renderRowLabel(g, getDepthLabel(), x, cursorY);
        renderRowToggle(g, mouseX, mouseY, x, w, cursorY, depthToggle, BoxSelectionPass.depthTestEnabled);
        cursorY += lineH;

        // ---- 条件行：线框透明度（紧跟在穿透层下方，条件满足时才渲染和占位）----
        if (depthOn) {
            String alphaLabel = getAlphaLabel()
                    + String.format(java.util.Locale.ROOT, "：%.0f%%", CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA * 100);
            renderRowSlider(g, mouseX, mouseY, x, w, cursorY, alphaLabel,
                    alphaSlider, alphaTrack, ALPHA_MIN, ALPHA_MAX,
                    CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA);
            cursorY += lineH;
        }

        // ---- 屏障颜色（始终可见，自动跟随 cursorY）----
        String barrierLabel = getBarrierLabel();
        renderRowLabel(g, barrierLabel, x, cursorY);

        int labelW = Minecraft.getInstance().font.width(barrierLabel);
        int textCenterY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int blockX = x + LEFT_PAD + labelW + COLOR_BLOCK_GAP;
        int blockY = textCenterY - ColorBlockComponent.DEFAULT_SIZE / 2;
        colorBlock.render(g, blockX, blockY, BoundaryPass.barrierColor);

        int btnX = x + w - RIGHT_PAD - ColorPickerButton.BTN_SIZE;
        int btnY = textCenterY - ColorPickerButton.BTN_SIZE / 2;
        colorPickerButton.render(g, btnX, btnY);
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
        if (lineIndex == ROW_BARRIER_COLOR) {
            if (colorPickerButton.handleClick(mouseX, mouseY)) {
                return true;
            }
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

    /**
     * 注入调色盘面板引用，由 ColorPickerButton 代理。
     */
    public void setColorPickerPanel(ColorPickerPanel panel) {
        this.colorPickerButton.setColorPickerPanel(panel);
    }

    /**
     * 设置调色盘按钮的父面板引用（唤出调色盘的面板）。
     * <p>点击调色盘按钮时自动通过 {@link RtsPanel#openChild(RtsPanel)} 建立父子关系，
     * 使父面板关闭时自动关闭调色盘面板。</p>
     */
    public void setColorPickerButtonParent(RtsPanel parent) {
        this.colorPickerButton.setParentPanel(parent);
    }

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

    // ======================== 缓存翻译文本（惰性初始化，避免每帧 Component.translatable()）=======================

    private String getFlowLabel() {
        if (cachedFlowLabel == null) cachedFlowLabel = Component.translatable("screen.rtsbuilding.settings.flow_animation").getString();
        return cachedFlowLabel;
    }

    private String getSmoothLabel() {
        if (cachedSmoothLabel == null) cachedSmoothLabel = Component.translatable("screen.rtsbuilding.settings.smooth_animation").getString();
        return cachedSmoothLabel;
    }

    private String getUiSmoothLabel() {
        if (cachedUiSmoothLabel == null) cachedUiSmoothLabel = Component.translatable("screen.rtsbuilding.settings.ui_smooth_animation").getString();
        return cachedUiSmoothLabel;
    }

    private String getDepthLabel() {
        if (cachedDepthLabel == null) cachedDepthLabel = Component.translatable("screen.rtsbuilding.settings.depth_test").getString();
        return cachedDepthLabel;
    }

    private String getAlphaLabel() {
        if (cachedAlphaLabel == null) cachedAlphaLabel = Component.translatable("screen.rtsbuilding.settings.overlay_alpha").getString();
        return cachedAlphaLabel;
    }

    private String getBarrierLabel() {
        if (cachedBarrierLabel == null) cachedBarrierLabel = Component.translatable("screen.rtsbuilding.settings.barrier_color").getString();
        return cachedBarrierLabel;
    }
}
