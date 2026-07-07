package com.rtsbuilding.rtsbuilding.client.screen.panel.color;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoundaryPass;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorSource;
import com.rtsbuilding.rtsbuilding.client.screen.panel.component.ColorPreviewComponent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.component.HexInputComponent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.component.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.component.SwatchSelectorComponent;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.render.BlendScope;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
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
 * {@link GrayscaleBarComponent}、{@link ColorPreviewComponent}、
 * {@link SwatchSelectorComponent} 和 {@link ScaleSliderComponent} 等子组件，
 * 颜色数学运算委托给 {@link ColorMath} 工具类。</p>
 */
public class ColorPickerPanel extends RtsPanel {

    // ======================== 面板尺寸（由组件自动计算）========================

    // ======================== 色调/饱和度滑条布局 ========================

    private static final int SLIDER_LABEL_W = 36;
    private static final int SLIDER_GAP = 6;
    private static final int SLIDER_ROW_GAP = 14;
    private static final int SLIDER_CLICK_PAD = 3;
    private static final int SLIDER_TRACK_H = 4;

    // ======================== Hex/Dec 输入组件 ========================

    /** 颜色值输入组件（标签 + 输入框 + 模式切换按钮） */
    private final HexInputComponent hexInput = new HexInputComponent();

    // ======================== 子组件 ========================

    private final ColorWheelComponent wheelComponent = new ColorWheelComponent();
    private final GrayscaleBarComponent grayscaleComponent = new GrayscaleBarComponent();
    private final ColorPreviewComponent colorPreview = new ColorPreviewComponent();
    private final SwatchSelectorComponent swatchSelector = new SwatchSelectorComponent();

    // ======================== 颜色源（由按钮打开时设置，面板只读写此源）=======================

    @javax.annotation.Nullable
    private ColorGroup colorGroup;
    /** 当前编辑的条目在组内的索引 */
    private int activeSlotIndex;

    /**
     * 设置当前编辑的颜色组，并加载第一个条目的颜色到面板状态。
     * <p>当组内有多个条目时，面板底部会显示色块选择器，点击即可切换编辑目标。</p>
     */
    public void setColorGroup(@javax.annotation.Nullable ColorGroup group) {
        this.colorGroup = group;
        this.activeSlotIndex = 0;
        if (group != null && group.size() > 0) {
            int color = group.slot(0).source().getColor();
            this.initialColor = color;  // 记录打开时的初始颜色用于新旧对照
            syncToColor(color);
        }
        // 面板已打开时动态调整尺寸以适应颜色组变化
        if (isOpen()) {
            int neededW = Math.max(getMinWindowWidth(), computeContentWidth() + 2);
            int neededH = Math.max(getMinWindowHeight(), computeContentHeight() + getTitleBarHeight() + 8);
            if (getWindowWidth() < neededW || getWindowHeight() < neededH) {
                setSize(neededW, neededH);
            }
        }
    }

    /**
     * 设置单个颜色源（兼容旧接口，自动包装为单条目组）。
     */
    public void setColorSource(@javax.annotation.Nullable ColorSource source) {
        if (source != null) {
            setColorGroup(ColorGroup.single("", "颜色", source));
        } else {
            this.colorGroup = null;
        }
    }

    /**
     * 设置当前活跃的色块索引（用于打开面板后从外部指定选中条目）。
     * <p>先将当前编辑的颜色写回旧槽位，再加载新槽位的颜色。</p>
     */
    public void setActiveSlot(int index) {
        switchToSlot(index);
    }

    /** 获取当前活跃的颜色源 */
    @javax.annotation.Nullable
    private ColorSource activeColorSource() {
        if (colorGroup == null || activeSlotIndex < 0 || activeSlotIndex >= colorGroup.size()) {
            return null;
        }
        return colorGroup.slot(activeSlotIndex).source();
    }

    /** 色块选择器是否需要渲染（组内有多于一个条目时） */
    private boolean hasSwatchSelector() {
        return colorGroup != null && colorGroup.size() > 1;
    }

    /**
     * 将面板内部状态同步到指定的颜色值。
     * <p>计算该颜色对应的轮盘指示点位置、灰度指示点位置、色相/饱和度值。</p>
     */
    private void syncToColor(int color) {
        this.wheelBaseColor = color;

        float[] hsv = ColorMath.rgbToHsv(color);
        this.hueValue = hsv[0];
        this.saturationValue = hsv[1];

        // 通过扫描轮盘贴图实际像素找到最匹配的颜色位置，而非数学公式近似
        ColorWheelComponent.IndicatorPos pos = wheelComponent.syncIndicatorToColor(color);
        this.indicatorRelX = pos.relX;
        this.indicatorRelY = pos.relY;

        // 计算灰度条指示点位置：当前颜色相对于 wheelBaseColor 的明度比例
        float valueOnly = hsv[2];
        this.grayscaleIndicatorRelY = Math.max(0.0f, Math.min(1.0f, 1.0f - valueOnly));
    }

    /** 将面板当前选中的颜色写回当前活跃的 {@link ColorSource} */
    private void applyToSource() {
        ColorSource source = activeColorSource();
        if (source != null) {
            source.setColor(getCurrentColor());
        }
    }

    /**
     * 切换到颜色组中的指定条目，并加载该条目的颜色到面板状态。
     * <p>先将当前修改的颜色写回旧槽位，再加载新槽位的颜色。</p>
     */
    private void switchToSlot(int index) {
        if (colorGroup == null || index < 0 || index >= colorGroup.size() || index == activeSlotIndex) {
            return;
        }
        // 先将当前正在编辑的颜色写回旧槽位
        applyToSource();
        // 切换到新槽位
        this.activeSlotIndex = index;
        // 加载新槽位的颜色
        int color = colorGroup.slot(index).source().getColor();
        this.initialColor = color;  // 更新初始颜色为新槽位的颜色
        syncToColor(color);
    }

    /** 计算面板当前选中的 ARGB 颜色 */
    private int getCurrentColor() {
        return ColorMath.blendGrayscale(this.wheelBaseColor, this.grayscaleIndicatorRelY);
    }

    // ======================== 指示点状态 ========================

    /** 指示点在轮盘绘制区域中的连续归一化位置 [0,1] */
    private float indicatorRelX = 0.5f;
    private float indicatorRelY = 0.5f;
    /** 从色盘选中的基色（灰度条从此色渐变到黑色，用于调节明度） */
    private int wheelBaseColor;
    /** 是否正在拖拽轮盘取色 */
    private boolean wheelDragging;
    /** 是否正在拖拽灰度条取色 */
    private boolean grayscaleDragging;
    /** 灰度条指示器的归一化 Y 位置 [0,1]：0=顶部（基色），1=底部（黑色） */
    private float grayscaleIndicatorRelY;
    /** 打开面板时的初始颜色（用于新旧对照） */
    private int initialColor;

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
    private final FloatAnimation indicatorStateAnim = AnimationFactory.newHoverAnim();
    /** 灰度条指示器状态过渡动画器 */
    private final FloatAnimation grayscaleIndicatorStateAnim = AnimationFactory.newHoverAnim();

    public ColorPickerPanel() {
    }

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        this.resizable = false;
        this.draggable = true;
        this.closable = true;
        // 连接 HexInputComponent 的颜色解析回调：解析成功时同步颜色并写回数据源
        this.hexInput.setOnColorParsed(color -> {
            syncToColor(color);
            applyToSource();
        });
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
    private int[] computeSliderSectionLayout(int cx, int cw, int wheelSectionBottom, int extraSectionH) {
        int sliderSectionY = wheelSectionBottom + 6 + ColorPreviewComponent.PREVIEW_BAR_H + extraSectionH + SLIDER_GAP;
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

        Font font = Minecraft.getInstance().font;

        // ---- 1. 轮盘 + 灰度条区域 ----
        int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
        int panelX = wheelLayout[0], panelY = wheelLayout[1], panelW = wheelLayout[2], panelH = wheelLayout[3];
        int wheelImgX = wheelLayout[4], wheelImgY = wheelLayout[5];
        int grayBarX = wheelLayout[6], grayBarY = wheelLayout[7];

        try (BlendScope blend = BlendScope.normal()) {
            // 浮窗背景（向右延伸以囊括灰度条）
            SpriteRenderer.drawNineSliceFloatingPanel(g, panelX, panelY, panelW, panelH);

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

        // ---- 2. 新旧颜色对照预览条（委托给 ColorPreviewComponent）----
        int previewY = panelY + panelH + 6;
        int previewX = cx + 6;
        int previewW = cw - 12;
        int newColor = getCurrentColor();
        colorPreview.render(g, previewX, previewY, previewW,
                this.initialColor, newColor, hexInput.isHexDisplayMode());

        // ---- 3. Hex/Dec 手动输入行（委托给 HexInputComponent）----
        int hexInputY = previewY + ColorPreviewComponent.PREVIEW_BAR_H + 3;
        this.hexInput.render(g, mouseX, mouseY, previewX, previewW, hexInputY, newColor);

        // ---- 4. 色块选择器（委托给 SwatchSelectorComponent）----
        int swatchSectionTop = hexInputY + HexInputComponent.INPUT_H + 3;
        swatchSelector.render(g, mouseX, mouseY, colorGroup, activeSlotIndex, swatchSectionTop);

        // ---- 5. 色调和饱和度滑条 ----
        int wheelSectionBottom = panelY + panelH;
        int extraSectionH = HexInputComponent.INPUT_H + 6 + (hasSwatchSelector() ? SwatchSelectorComponent.ROW_H : 0);
        int[] sliderLayout = computeSliderSectionLayout(cx, cw, wheelSectionBottom, extraSectionH);
        int sliderSectionY = sliderLayout[0];
        int sliderTrackX = sliderLayout[1];
        int sliderTrackW = sliderLayout[2];

        int textColor = ThemeManager.getTextColor();
        TextRenderer.draw(g, "色相", cx + 6, sliderSectionY - 1, textColor);
        hueSlider.render(g, mouseX, mouseY, sliderTrackX, sliderSectionY, sliderTrackW, 0.0, 1.0, hueValue);

        int satSliderY = sliderSectionY + SLIDER_ROW_GAP;
        TextRenderer.draw(g, "饱和度", cx + 6, satSliderY - 1, textColor);
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

        // ---- 3. 检测色块选择器点击（委托给 SwatchSelectorComponent）----
        if (hasSwatchSelector()) {
            int previewY = wheelLayout[1] + wheelLayout[3] + 6;
            int hexInputY = previewY + ColorPreviewComponent.PREVIEW_BAR_H + 3;
            int swatchSectionTop = hexInputY + HexInputComponent.INPUT_H + 3;
            int hitIndex = swatchSelector.hitTest(mouseX, mouseY, colorGroup, swatchSectionTop);
            if (hitIndex >= 0) {
                switchToSlot(hitIndex);
                return;
            }
        }

        // ---- 4. 检测旧颜色预览区点击（点击可恢复为旧颜色）----
        int previewY = wheelLayout[1] + wheelLayout[3] + 6;
        int previewX = cx + 6;
        int previewW = cw - 12;
        if (colorPreview.isClickOnInitialColor(mouseX, mouseY, previewX, previewW, previewY)) {
            if (hexInput.isEditMode()) {
                hexInput.cancelEdit();
            }
            syncToColor(this.initialColor);
            applyToSource();
            return;
        }

        // ---- 5. 检测 Hex/Dec 输入框及模式切换按钮点击（委托给 HexInputComponent）----
        int hexInputY = previewY + ColorPreviewComponent.PREVIEW_BAR_H + 3;
        if (this.hexInput.handleClick(mouseX, mouseY, hexInputY, previewX, previewW, getCurrentColor())) {
            return;
        }

        // ---- 6. 检测色调滑条点击 ----
        int extraSectionH = HexInputComponent.INPUT_H + 6 + (hasSwatchSelector() ? SwatchSelectorComponent.ROW_H : 0);
        int wheelSectionBottom = wheelLayout[1] + wheelLayout[3];
        int[] sliderLayout = computeSliderSectionLayout(cx, cw, wheelSectionBottom, extraSectionH);
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

        // ---- 7. 检测饱和度滑条点击 ----
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

    // ======================== 面板生命周期 ========================

    @Override
    public void setOpen(boolean open) {
        if (open && !isOpen() && colorGroup != null && colorGroup.size() > 1) {
            // 首次打开且有多色块时，提前设好尺寸以免 init 缓存的默认值太小
            int w = Math.max(getMinWindowWidth(), computeContentWidth() + 2);
            int h = Math.max(getMinWindowHeight(), computeContentHeight() + getTitleBarHeight() + 8);
            setBounds(getWindowX(), getWindowY(), w, h);
        }
        super.setOpen(open);
    }

    @Override
    protected void onClose() {
        // 面板关闭时若处于文本编辑状态，先应用当前输入
        if (hexInput.isEditMode()) {
            hexInput.applyEdit();
        }
    }

    // ======================== 拖拽取色 ========================

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        return this.hexInput.handleKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return this.hexInput.handleCharTyped(codePoint, modifiers);
    }

    private void pickWheelColor(double mouseX, double mouseY, int wheelImgX, int wheelImgY) {
        ColorWheelComponent.WheelPickResult result = wheelComponent.pickColor(mouseX, mouseY, wheelImgX, wheelImgY);
        if (result != null) {
            this.wheelBaseColor = result.color;
            this.grayscaleIndicatorRelY = 0.0f;
            this.indicatorRelX = result.relX;
            this.indicatorRelY = result.relY;

            float[] hsv = ColorMath.rgbToHsv(this.wheelBaseColor);
            this.hueValue = hsv[0];
            this.saturationValue = hsv[1];

            applyToSource();
        }
    }

    private void pickGrayscaleColor(double mouseY, int grayBarY) {
        this.grayscaleIndicatorRelY = grayscaleComponent.pickColor(mouseY, grayBarY);
        applyToSource();
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
        if (colorGroup != null && activeSlotIndex >= 0 && activeSlotIndex < colorGroup.size()) {
            String groupName = colorGroup.groupDisplayName();
            String slotName = colorGroup.slot(activeSlotIndex).displayName();
            if (!groupName.isEmpty()) {
                return Component.literal(groupName + " - " + slotName);
            }
            return Component.literal(slotName);
        }
        return Component.translatable("screen.rtsbuilding.color_picker.title");
    }

    @Override
    protected int getDefaultWidth() {
        return Math.max(getMinWindowWidth(), computeContentWidth() + 2);
    }

    @Override
    protected int getDefaultHeight() {
        return Math.max(getMinWindowHeight(), computeContentHeight() + getTitleBarHeight() + 8);
    }

    /**
     * 计算内容区域所需的最小宽度。<p>
     * 取轮盘+灰度条区域宽度 与 输入行（标签+输入框+模式按钮）宽度 的较大值，
     * 当有色块选择器时还要确保能容纳所有色块+文字标签。</p>
     */
    private int computeContentWidth() {
        int wheelWidth = ColorWheelComponent.AREA_SIZE + GrayscaleBarComponent.GAP + GrayscaleBarComponent.BAR_W + 8;
        int inputLineWidth = computeInputLineWidth();
        int maxWidth = Math.max(wheelWidth, inputLineWidth);
        // 当有色块选择器时，确保宽度能容纳所有色块+文字标签
        if (colorGroup != null && colorGroup.size() > 1) {
            maxWidth = Math.max(maxWidth, swatchSelector.computeMinWidth(colorGroup));
        }
        return maxWidth;
    }

    /** 计算输入行（标签 + 输入框 + 模式按钮）所需的最小内容宽度 */
    private int computeInputLineWidth() {
        return this.hexInput.computeInputLineWidth();
    }

    /**
     * 计算内容区域所需的最小高度——包含所有组件的垂直布局。<p>
     * 涵盖：顶部偏移 → 轮盘 → 预览条 → Hex 输入行 →（可选色块）→ 色调/饱和度滑条 → 底部内边距。</p>
     */
    private int computeContentHeight() {
        int h = 4; // panelY = cy + 4
        h += ColorWheelComponent.AREA_SIZE; // 轮盘浮窗区
        h += 6 + ColorPreviewComponent.PREVIEW_BAR_H; // gap + 预览条
        h += 3 + HexInputComponent.INPUT_H + 3; // gap + Hex 输入行 + gap
        if (hasSwatchSelector()) {
            h += SwatchSelectorComponent.ROW_H; // 可选色块选择器
        }
        h += 6 + SLIDER_GAP; // 到滑条段的 gap
        h += SLIDER_ROW_GAP + SLIDER_TRACK_H + SLIDER_CLICK_PAD; // 饱和度滑条底部
        h += 10; // 底部内边距
        return h;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen != null) {
            setWindowX(Math.max(8, (this.screen.width - getWindowWidth()) / 2));
            setWindowY(Math.max(60, (this.screen.height - getWindowHeight()) / 2));
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

        applyToSource();
    }
}
