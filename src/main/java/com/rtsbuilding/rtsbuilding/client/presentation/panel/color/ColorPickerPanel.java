package com.rtsbuilding.rtsbuilding.client.presentation.panel.color;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ColorPreviewComponent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.HexInputComponent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.SwatchSelectorComponent;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.BlendScope;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;


public class ColorPickerPanel extends RtsPanel {

    

    

    private static final int SLIDER_LABEL_W = 36;
    private static final int SLIDER_GAP = 6;
    private static final int SLIDER_ROW_GAP = 14;
    private static final int SLIDER_CLICK_PAD = 3;
    private static final int SLIDER_TRACK_H = 4;

    

    
    private final HexInputComponent hexInput = new HexInputComponent();

    

    private final ColorWheelComponent wheelComponent = new ColorWheelComponent();
    private final GrayscaleBarComponent grayscaleComponent = new GrayscaleBarComponent();
    private final ColorPreviewComponent colorPreview = new ColorPreviewComponent();
    private final SwatchSelectorComponent swatchSelector = new SwatchSelectorComponent();

    

    @javax.annotation.Nullable
    private ColorGroup colorGroup;
    
    private int activeSlotIndex;

    
    public void setColorGroup(@javax.annotation.Nullable ColorGroup group) {
        this.colorGroup = group;
        this.activeSlotIndex = 0;
        if (group != null && group.size() > 0) {
            int color = group.slot(0).source().getColor();
            this.initialColor = color;  
            syncToColor(color);
        }
        
        if (isOpen()) {
            int neededW = Math.max(getMinWindowWidth(), computeContentWidth() + 2);
            int neededH = Math.max(getMinWindowHeight(), computeContentHeight() + getTitleBarHeight() + 8);
            if (getWindowWidth() < neededW || getWindowHeight() < neededH) {
                setSize(neededW, neededH);
            }
        }
    }

    
    public void setColorSource(@javax.annotation.Nullable ColorSource source) {
        if (source != null) {
            setColorGroup(ColorGroup.single("", "颜色", source));
        } else {
            this.colorGroup = null;
        }
    }

    
    public void setActiveSlot(int index) {
        switchToSlot(index);
    }

    
    @javax.annotation.Nullable
    private ColorSource activeColorSource() {
        if (colorGroup == null || activeSlotIndex < 0 || activeSlotIndex >= colorGroup.size()) {
            return null;
        }
        return colorGroup.slot(activeSlotIndex).source();
    }

    
    private boolean hasSwatchSelector() {
        return colorGroup != null && colorGroup.size() > 1;
    }

    
    private void syncToColor(int color) {
        this.wheelBaseColor = color;

        float[] hsv = ColorMath.rgbToHsv(color);
        this.hueValue = hsv[0];
        this.saturationValue = hsv[1];

        
        ColorWheelComponent.IndicatorPos pos = wheelComponent.syncIndicatorToColor(color);
        this.indicatorRelX = pos.relX;
        this.indicatorRelY = pos.relY;

        
        float valueOnly = hsv[2];
        this.grayscaleIndicatorRelY = Math.max(0.0f, Math.min(1.0f, 1.0f - valueOnly));
    }

    
    private void applyToSource() {
        ColorSource source = activeColorSource();
        if (source != null) {
            source.setColor(getCurrentColor());
        }
    }

    
    private void switchToSlot(int index) {
        if (colorGroup == null || index < 0 || index >= colorGroup.size() || index == activeSlotIndex) {
            return;
        }
        
        applyToSource();
        
        this.activeSlotIndex = index;
        
        int color = colorGroup.slot(index).source().getColor();
        this.initialColor = color;  
        syncToColor(color);
    }

    
    private int getCurrentColor() {
        return ColorMath.blendGrayscale(this.wheelBaseColor, this.grayscaleIndicatorRelY);
    }

    

    
    private float indicatorRelX = 0.5f;
    private float indicatorRelY = 0.5f;
    
    private int wheelBaseColor;
    
    private boolean wheelDragging;
    
    private boolean grayscaleDragging;
    
    private float grayscaleIndicatorRelY;
    
    private int initialColor;

    

    private final ScaleSliderComponent hueSlider = new ScaleSliderComponent();
    private final ScaleSliderComponent satSlider = new ScaleSliderComponent();
    
    private float hueValue;
    
    private float saturationValue;
    
    private int sliderDraggingIndex = -1;
    
    private double sliderDragStartX;
    
    private double sliderDragStartVal;

    

    
    private final FloatAnimation indicatorStateAnim = AnimationFactory.newHoverAnim();
    
    private final FloatAnimation grayscaleIndicatorStateAnim = AnimationFactory.newHoverAnim();

    public ColorPickerPanel() {
    }

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        this.resizable = false;
        this.draggable = true;
        this.closable = true;
        
        this.hexInput.setOnColorParsed(color -> {
            syncToColor(color);
            applyToSource();
        });
    }

    

    
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

    
    private int[] computeSliderSectionLayout(int cx, int cw, int wheelSectionBottom, int extraSectionH) {
        int sliderSectionY = wheelSectionBottom + 6 + ColorPreviewComponent.PREVIEW_BAR_H + extraSectionH + SLIDER_GAP;
        int sliderTrackX = cx + SLIDER_LABEL_W + 4;
        int sliderTrackW = cw - SLIDER_LABEL_W - 10;
        return new int[]{sliderSectionY, sliderTrackX, sliderTrackW};
    }

    

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        Font font = Minecraft.getInstance().font;

        
        int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
        int panelX = wheelLayout[0], panelY = wheelLayout[1], panelW = wheelLayout[2], panelH = wheelLayout[3];
        int wheelImgX = wheelLayout[4], wheelImgY = wheelLayout[5];
        int grayBarX = wheelLayout[6], grayBarY = wheelLayout[7];

        try (BlendScope blend = BlendScope.normal()) {
            
            SpriteRenderer.drawNineSliceFloatingPanel(g, panelX, panelY, panelW, panelH, false);

            
            wheelComponent.renderWheel(g, wheelImgX, wheelImgY);

            
            wheelComponent.renderIndicator(g, wheelImgX, wheelImgY,
                    indicatorRelX, indicatorRelY, indicatorStateAnim,
                    mouseX, mouseY, wheelDragging);

            
            grayscaleComponent.renderBar(g, grayBarX, grayBarY, wheelBaseColor);

            
            grayscaleComponent.renderIndicator(g, grayBarX, grayBarY,
                    grayscaleIndicatorRelY, grayscaleIndicatorStateAnim,
                    mouseX, mouseY, grayscaleDragging);
        }

        
        int previewY = panelY + panelH + 6;
        int previewX = cx + 6;
        int previewW = cw - 12;
        int newColor = getCurrentColor();
        colorPreview.render(g, previewX, previewY, previewW,
                this.initialColor, newColor, hexInput.isHexDisplayMode());

        
        int hexInputY = previewY + ColorPreviewComponent.PREVIEW_BAR_H + 3;
        this.hexInput.render(g, mouseX, mouseY, previewX, previewW, hexInputY, newColor);

        
        int swatchSectionTop = hexInputY + HexInputComponent.INPUT_H + 3;
        swatchSelector.render(g, mouseX, mouseY, colorGroup, activeSlotIndex, swatchSectionTop, cx);

        
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

    

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
        int wheelImgX = wheelLayout[4], wheelImgY = wheelLayout[5];
        int grayBarX = wheelLayout[6], grayBarY = wheelLayout[7];

        
        if (mouseX >= wheelImgX && mouseX < wheelImgX + ColorWheelComponent.DRAW_SIZE
                && mouseY >= wheelImgY && mouseY < wheelImgY + ColorWheelComponent.DRAW_SIZE) {
            pickWheelColor(mouseX, mouseY, wheelImgX, wheelImgY);
            this.wheelDragging = true;
            return;
        }

        
        if (mouseX >= grayBarX && mouseX < grayBarX + GrayscaleBarComponent.BAR_W
                && mouseY >= grayBarY && mouseY < grayBarY + GrayscaleBarComponent.BAR_H) {
            pickGrayscaleColor(mouseY, grayBarY);
            this.grayscaleDragging = true;
            return;
        }

        
        if (hasSwatchSelector()) {
            int previewY = wheelLayout[1] + wheelLayout[3] + 6;
            int hexInputY = previewY + ColorPreviewComponent.PREVIEW_BAR_H + 3;
            int swatchSectionTop = hexInputY + HexInputComponent.INPUT_H + 3;
            int hitIndex = swatchSelector.hitTest(mouseX, mouseY, colorGroup, swatchSectionTop, cx);
            if (hitIndex >= 0) {
                switchToSlot(hitIndex);
                return;
            }
        }

        
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

        
        int hexInputY = previewY + ColorPreviewComponent.PREVIEW_BAR_H + 3;
        if (this.hexInput.handleClick(mouseX, mouseY, hexInputY, previewX, previewW, getCurrentColor())) {
            return;
        }

        
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

    

    @Override
    public void setOpen(boolean open) {
        if (open && !isOpen() && colorGroup != null && colorGroup.size() > 1) {
            
            int w = Math.max(getMinWindowWidth(), computeContentWidth() + 2);
            int h = Math.max(getMinWindowHeight(), computeContentHeight() + getTitleBarHeight() + 8);
            setBounds(getWindowX(), getWindowY(), w, h);
        }
        super.setOpen(open);
    }

    @Override
    protected void onClose() {
        
        if (hexInput.isEditMode()) {
            hexInput.applyEdit();
        }
    }

    

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

        
        if (this.wheelDragging && button == 0) {
            int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
            pickWheelColor(mouseX, mouseY, wheelLayout[4], wheelLayout[5]);
            return true;
        }

        
        if (this.grayscaleDragging && button == 0) {
            int[] wheelLayout = computeWheelSectionLayout(cx, cy, cw);
            pickGrayscaleColor(mouseY, wheelLayout[7]);
            return true;
        }

        
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

    
    private int computeContentWidth() {
        int wheelWidth = ColorWheelComponent.AREA_SIZE + GrayscaleBarComponent.GAP + GrayscaleBarComponent.BAR_W + 8;
        int inputLineWidth = computeInputLineWidth();
        int maxWidth = Math.max(wheelWidth, inputLineWidth);
        
        if (colorGroup != null && colorGroup.size() > 1) {
            maxWidth = Math.max(maxWidth, swatchSelector.computeMinWidth(colorGroup));
        }
        return maxWidth;
    }

    
    private int computeInputLineWidth() {
        return this.hexInput.computeInputLineWidth();
    }

    
    private int computeContentHeight() {
        int h = 4; 
        h += ColorWheelComponent.AREA_SIZE; 
        h += 6 + ColorPreviewComponent.PREVIEW_BAR_H; 
        h += 3 + HexInputComponent.INPUT_H + 3; 
        if (hasSwatchSelector()) {
            h += SwatchSelectorComponent.ROW_H; 
        }
        h += 6 + SLIDER_GAP; 
        h += SLIDER_ROW_GAP + SLIDER_TRACK_H + SLIDER_CLICK_PAD; 
        h += 10; 
        return h;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen != null) {
            setWindowX(Math.max(8, (this.screen.width - getWindowWidth()) / 2));
            setWindowY(Math.max(60, (this.screen.height - getWindowHeight()) / 2));
        }
    }

    

    
    private void updateColorFromSliders() {
        this.wheelBaseColor = ColorMath.hsvToRgb(this.hueValue, this.saturationValue, 1.0f);

        ColorWheelComponent.IndicatorPos pos = wheelComponent.calcIndicatorUVFromHS(this.hueValue, this.saturationValue);
        this.indicatorRelX = pos.relX;
        this.indicatorRelY = pos.relY;

        applyToSource();
    }
}
