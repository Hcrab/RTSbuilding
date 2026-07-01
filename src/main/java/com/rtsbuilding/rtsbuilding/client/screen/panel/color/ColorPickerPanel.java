package com.rtsbuilding.rtsbuilding.client.screen.panel.color;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoundaryPass;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 调色盘面板——提供颜色轮盘 + 灰度条 + 色调/饱和度滑条的屏障颜色选择器。
 *
 * <p>浮动窗口面板，继承 {@link RtsPanel}，由 {@link BuilderScreen} 管理。
 * 通过颜色轮盘、灰度条或色调/饱和度滑条来修改 {@link BoundaryPass#barrierColor}。</p>
 *
 * <p>本面板为布局协调器，将渲染和交互委托给 {@link ColorWheelComponent}、
 * {@link GrayscaleBarComponent} 和 {@link ScaleSliderComponent} 等子组件，
 * 颜色数学运算委托给 {@link ColorMath} 工具类。</p>
 */
public class ColorPickerPanel extends RtsPanel {

    // ======================== 面板尺寸 ========================

    private static final int PANEL_W = 197;
    private static final int PANEL_H = 250;

    // ======================== 颜色预览条布局 ========================

    private static final int PREVIEW_BAR_H = 16;

    // ======================== 色调/饱和度滑条布局 ========================

    private static final int SLIDER_LABEL_W = 36;
    private static final int SLIDER_GAP = 6;
    private static final int SLIDER_ROW_GAP = 14;
    private static final int SLIDER_CLICK_PAD = 3;
    private static final int SLIDER_TRACK_H = 4;

    // ======================== 子组件 ========================

    private final ColorWheelComponent wheelComponent = new ColorWheelComponent();
    private final GrayscaleBarComponent grayscaleComponent = new GrayscaleBarComponent();

    // ======================== 指示点状态 ========================

    /** 指示点在轮盘绘制区域中的连续归一化位置 [0,1] */
    private float indicatorRelX = 0.5f;
    private float indicatorRelY = 0.5f;
    /** 从色盘选中的基色（灰度条从此色渐变到黑色，用于调节明度） */
    private int wheelBaseColor = BoundaryPass.barrierColor;
    /** 是否正在拖拽轮盘取色 */
    private boolean wheelDragging;
    /** 是否正在拖拽灰度条取色 */
    private boolean grayscaleDragging;
    /** 灰度条指示器的归一化 Y 位置 [0,1]：0=顶部（基色），1=底部（黑色） */
    private float grayscaleIndicatorRelY;

    // ======================== 色调/饱和度滑条 ========================

    private final ScaleSliderComponent hueSlider = new ScaleSliderComponent();
    private final ScaleSliderComponent satSlider = new ScaleSliderComponent();
    /** 当前色调值 [0,1] */
    private float hueValue;
    /** 当前饱和度值 [0,1] */
    private float saturationValue;
    /** 滑条拖拽索引：-1=无，0=色调，1=饱和度 */
    private int sliderDraggingIndex = -1;
    /** 拖拽起始鼠标 X */
    private double sliderDragStartX;
    /** 拖拽起始值 */
    private double sliderDragStartVal;

    // ======================== 指示器状态动画器 ========================

    /** 轮盘指示点状态过渡动画器（0=正常，1=悬浮，2=拖拽） */
    private final SmoothAnimator indicatorStateAnim = AnimationFactory.createHoverAnim();
    /** 灰度条指示器状态过渡动画器 */
    private final SmoothAnimator grayscaleIndicatorStateAnim = AnimationFactory.createHoverAnim();

    public ColorPickerPanel() {
    }

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        this.resizable = false;
        this.draggable = true;
        this.closable = true;
    }

    // ======================== 布局计算 ========================

    /**
     * 计算轮盘 + 灰度条区域的浮窗面板坐标。
     *
     * @return int[] { panelX, panelY, panelW, panelH, wheelImgX, wheelImgY, grayBarX, grayBarY }
     */
    private int[] computeWheelSectionLayout(int cx, int cy, int cw) {
        int panelW = ColorWheelComponent.AREA_SIZE + GrayscaleBarComponent.GAP + GrayscaleBarComponent.BAR_W;
        int panelX = cx + (cw - panelW) / 2;
        int panelY = cy + 4;

        int wheelImgX = panelX + ColorWheelComponent.PAD;
        int wheelImgY = panelY + ColorWheelComponent.PAD;

        int grayBarX = wheelImgX + ColorWheelComponent.DRAW_SIZE + GrayscaleBarComponent.GAP;
        int grayBarY = wheelImgY;

        return new int[]{
                panelX, panelY, panelW, ColorWheelComponent.AREA_SIZE,
                wheelImgX, wheelImgY, grayBarX, grayBarY
        };
    }

    /**
     * 计算滑条区域的基础布局。
     *
     * @return int[] { sliderSectionY, sliderTrackX, sliderTrackW }
     */
    private int[] computeSliderSectionLayout(int cx, int cw, int wheelSectionBottom) {
        int sliderSectionY = wheelSectionBottom + 6 + PREVIEW_BAR_H + SLIDER_GAP;
        int sliderTrackX = cx + SLIDER_LABEL_W + 4;
        int sliderTrackW = cw - SLIDER_LABEL_W - 10;
        return new int[]{sliderSectionY, sliderTrackX, sliderTrackW};
    }

    // ======================== 渲染 ========================

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        int textColor = ThemeManager.getTextColor();
        Font font = Minecraft.getInstance().font;

        // ---- 1. 轮盘 + 灰度条区域 ----
        int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
        int panelX = wheelLayout[0], panelY = wheelLayout[1], panelW = wheelLayout[2], panelH = wheelLayout[3];
        int wheelImgX = wheelLayout[4], wheelImgY = wheelLayout[5];
        int grayBarX = wheelLayout[6], grayBarY = wheelLayout[7];

        try (BlendScope blend = BlendScope.normal()) {
            // 浮窗背景（向右延伸以囊括灰度条）
            RtsClientUiUtil.drawNineSliceFloatingPanel(g, panelX, panelY, panelW, panelH);

            // 轮盘
            wheelComponent.renderWheel(g, wheelImgX, wheelImgY);

            // 轮盘指示点
            wheelComponent.renderIndicator(g, wheelImgX, wheelImgY,
                    indicatorRelX, indicatorRelY, indicatorStateAnim,
                    mouseX, mouseY, wheelDragging);

            // 灰度条渐变
            grayscaleComponent.renderBar(g, grayBarX, grayBarY, wheelBaseColor);

            // 灰度条指示器
            grayscaleComponent.renderIndicator(g, grayBarX, grayBarY,
                    grayscaleIndicatorRelY, grayscaleIndicatorStateAnim,
                    mouseX, mouseY, grayscaleDragging);
        }

        // ---- 2. 当前颜色预览条 ----
        int previewY = panelY + panelH + 6;
        int previewX = cx + 6;
        int previewW = cw - 12;

        g.fill(previewX, previewY, previewX + previewW, previewY + PREVIEW_BAR_H,
                BoundaryPass.barrierColor);
        int borderColor = 0xFF666666;
        g.hLine(previewX, previewX + previewW, previewY, borderColor);
        g.hLine(previewX, previewX + previewW, previewY + PREVIEW_BAR_H, borderColor);
        g.vLine(previewX, previewY, previewY + PREVIEW_BAR_H, borderColor);
        g.vLine(previewX + previewW, previewY, previewY + PREVIEW_BAR_H, borderColor);

        String hexStr = String.format("#%06X", BoundaryPass.barrierColor & 0xFFFFFF);
        int hexColor = ColorMath.isDarkColor(BoundaryPass.barrierColor) ? 0xFFFFFFFF : 0xFF000000;
        int hexX = cx + cw / 2 - font.width(hexStr) / 2;
        int hexY = previewY + (PREVIEW_BAR_H - font.lineHeight) / 2 + 1;
        g.drawString(font, hexStr, hexX, hexY, hexColor, false);

        // ---- 3. 色调和饱和度滑条 ----
        int wheelSectionBottom = panelY + panelH;
        int[] sliderLayout = computeSliderSectionLayout(cx, cw, wheelSectionBottom);
        int sliderSectionY = sliderLayout[0];
        int sliderTrackX = sliderLayout[1];
        int sliderTrackW = sliderLayout[2];

        RtsClientUiUtil.drawUiText(g, "色相", cx + 2, sliderSectionY - 1, textColor);
        hueSlider.render(g, mouseX, mouseY, sliderTrackX, sliderSectionY, sliderTrackW, 0.0, 1.0, hueValue);

        int satSliderY = sliderSectionY + SLIDER_ROW_GAP;
        RtsClientUiUtil.drawUiText(g, "饱和度", cx + 2, satSliderY - 1, textColor);
        satSlider.render(g, mouseX, mouseY, sliderTrackX, satSliderY, sliderTrackW, 0.0, 1.0, saturationValue);
    }

    // ======================== 交互 ========================

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
        int wheelImgX = wheelLayout[4], wheelImgY = wheelLayout[5];
        int grayBarX = wheelLayout[6], grayBarY = wheelLayout[7];

        // ---- 1. 检测颜色轮盘点击 ----
        if (mouseX >= wheelImgX && mouseX < wheelImgX + ColorWheelComponent.DRAW_SIZE
                && mouseY >= wheelImgY && mouseY < wheelImgY + ColorWheelComponent.DRAW_SIZE) {
            pickWheelColor(mouseX, mouseY, wheelImgX, wheelImgY);
            this.wheelDragging = true;
            return;
        }

        // ---- 2. 检测灰度条点击 ----
        if (mouseX >= grayBarX && mouseX < grayBarX + GrayscaleBarComponent.BAR_W
                && mouseY >= grayBarY && mouseY < grayBarY + GrayscaleBarComponent.BAR_H) {
            pickGrayscaleColor(mouseY, grayBarY);
            this.grayscaleDragging = true;
            return;
        }

        // ---- 3. 检测色调滑条点击 ----
        int wheelSectionBottom = wheelLayout[1] + wheelLayout[3];
        int[] sliderLayout = computeSliderSectionLayout(cx, cw, wheelSectionBottom);
        int sliderSectionY = sliderLayout[0];
        int sliderTrackX = sliderLayout[1];
        int sliderTrackW = sliderLayout[2];

        if (mouseY >= sliderSectionY - SLIDER_CLICK_PAD && mouseY < sliderSectionY + SLIDER_TRACK_H + SLIDER_CLICK_PAD
                && mouseX >= sliderTrackX && mouseX < sliderTrackX + sliderTrackW) {
            double relX = (mouseX - sliderTrackX) / (double) sliderTrackW;
            this.hueValue = (float) Mth.clamp(relX, 0.0, 1.0);
            this.sliderDraggingIndex = 0;
            this.sliderDragStartX = mouseX;
            this.sliderDragStartVal = this.hueValue;
            hueSlider.handleClick(mouseX, mouseY, sliderTrackX, sliderSectionY, sliderTrackW, 0.0, 1.0);
            updateColorFromSliders();
            return;
        }

        // ---- 4. 检测饱和度滑条点击 ----
        int satSliderY = sliderSectionY + SLIDER_ROW_GAP;
        if (mouseY >= satSliderY - SLIDER_CLICK_PAD && mouseY < satSliderY + SLIDER_TRACK_H + SLIDER_CLICK_PAD
                && mouseX >= sliderTrackX && mouseX < sliderTrackX + sliderTrackW) {
            double relX = (mouseX - sliderTrackX) / (double) sliderTrackW;
            this.saturationValue = (float) Mth.clamp(relX, 0.0, 1.0);
            this.sliderDraggingIndex = 1;
            this.sliderDragStartX = mouseX;
            this.sliderDragStartVal = this.saturationValue;
            satSlider.handleClick(mouseX, mouseY, sliderTrackX, satSliderY, sliderTrackW, 0.0, 1.0);
            updateColorFromSliders();
        }
    }

    // ======================== 拖拽取色 ========================

    private void pickWheelColor(double mouseX, double mouseY, int wheelImgX, int wheelImgY) {
        ColorWheelComponent.WheelPickResult result = wheelComponent.pickColor(mouseX, mouseY, wheelImgX, wheelImgY);
        if (result != null) {
            BoundaryPass.barrierColor = result.color;
            this.wheelBaseColor = result.color;
            this.grayscaleIndicatorRelY = 0.0f;
            this.indicatorRelX = result.relX;
            this.indicatorRelY = result.relY;

            float[] hsv = ColorMath.rgbToHsv(this.wheelBaseColor);
            this.hueValue = hsv[0];
            this.saturationValue = hsv[1];
        }
    }

    private void pickGrayscaleColor(double mouseY, int grayBarY) {
        this.grayscaleIndicatorRelY = grayscaleComponent.pickColor(mouseY, grayBarY);
        BoundaryPass.barrierColor = ColorMath.blendGrayscale(this.wheelBaseColor, this.grayscaleIndicatorRelY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;

        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        // 轮盘拖拽取色
        if (this.wheelDragging && button == 0) {
            int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
            pickWheelColor(mouseX, mouseY, wheelLayout[4], wheelLayout[5]);
            return true;
        }

        // 灰度条拖拽取色
        if (this.grayscaleDragging && button == 0) {
            int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
            pickGrayscaleColor(mouseY, wheelLayout[7]);
            return true;
        }

        // 滑条拖拽
        if (this.sliderDraggingIndex >= 0 && button == 0) {
            int trackW = cw - SLIDER_LABEL_W - 10;
            double pixelRange = Math.max(1.0, trackW - 8.0);
            double dx = mouseX - this.sliderDragStartX;
            double newVal = Mth.clamp(this.sliderDragStartVal + dx / pixelRange, 0.0, 1.0);
            if (this.sliderDraggingIndex == 0) {
                this.hueValue = (float) newVal;
            } else {
                this.saturationValue = (float) newVal;
            }
            updateColorFromSliders();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.wheelDragging = false;
        this.grayscaleDragging = false;
        this.sliderDraggingIndex = -1;
        hueSlider.endDrag();
        satSlider.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ======================== 面板属性 ========================

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.color_picker.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return PANEL_H;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen != null) {
            this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
            this.windowY = Math.max(60, (this.screen.height - this.windowHeight) / 2);
        }
    }

    // ======================== 滑条同步 ========================

    /**
     * 从色调/饱和度滑条值重新计算 wheelBaseColor，同步指示点，并应用灰度。
     */
    private void updateColorFromSliders() {
        this.wheelBaseColor = ColorMath.hsvToRgb(this.hueValue, this.saturationValue, 1.0f);

        ColorWheelComponent.IndicatorPos pos = wheelComponent.calcIndicatorUVFromHS(this.hueValue, this.saturationValue);
        this.indicatorRelX = pos.relX;
        this.indicatorRelY = pos.relY;

        BoundaryPass.barrierColor = ColorMath.blendGrayscale(this.wheelBaseColor, this.grayscaleIndicatorRelY);
    }
}
