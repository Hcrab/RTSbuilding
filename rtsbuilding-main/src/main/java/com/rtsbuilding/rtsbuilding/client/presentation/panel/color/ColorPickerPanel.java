package com.rtsbuilding.rtsbuilding.client.presentation.panel.color;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.HexInputComponent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.BlendScope;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
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
            setColorGroup(ColorGroup.single("", "\u989C\u8272", source));
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
        if (colorGroup == null || index < 0 || index >= colorGroup.size() || index == activeSlotIndex) return;
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

    private final AnimFloat indicatorStateAnim = AnimFloat.hover();
    private final AnimFloat grayscaleIndicatorStateAnim = AnimFloat.hover();

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
            SdfRenderer.drawVectorFloatingPanel(g, panelX, panelY, panelW, panelH, false);
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
        TextRenderer.draw(g, "\u8272\u76F8", cx + 6, sliderSectionY - 1, textColor);
        hueSlider.render(g, mouseX, mouseY, sliderTrackX, sliderSectionY, sliderTrackW, 0.0, 1.0, hueValue);

        int satSliderY = sliderSectionY + SLIDER_ROW_GAP;
        TextRenderer.draw(g, "\u9971\u548C\u5EA6", cx + 6, satSliderY - 1, textColor);
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
            if (hexInput.isEditMode()) hexInput.cancelEdit();
            syncToColor(this.initialColor);
            applyToSource();
            return;
        }

        int hexInputY = previewY + ColorPreviewComponent.PREVIEW_BAR_H + 3;
        if (this.hexInput.handleClick(mouseX, mouseY, hexInputY, previewX, previewW, getCurrentColor())) return;

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
        if (hexInput.isEditMode()) hexInput.applyEdit();
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
            if (!groupName.isEmpty()) return Component.literal(groupName + " - " + slotName);
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
        if (hasSwatchSelector()) h += SwatchSelectorComponent.ROW_H;
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

    // ── Merged inner classes ──

    public interface ColorSource {
        int getColor();
        void setColor(int color);
    }

    public static class ColorSlot {
        private final String displayName;
        private final ColorSource source;
        public ColorSlot(String displayName, ColorSource source) {
            this.displayName = displayName;
            this.source = source;
        }
        public String displayName() { return displayName; }
        public ColorSource source() { return source; }
    }

    public static class ColorGroup {
        private final String groupDisplayName;
        private final java.util.List<ColorSlot> slots;
        public ColorGroup(String groupDisplayName, java.util.List<ColorSlot> slots) {
            if (slots == null || slots.isEmpty()) throw new IllegalArgumentException("ColorGroup needs at least one ColorSlot");
            this.groupDisplayName = groupDisplayName;
            this.slots = java.util.List.copyOf(slots);
        }
        public String groupDisplayName() { return groupDisplayName; }
        public java.util.List<ColorSlot> slots() { return slots; }
        public int size() { return slots.size(); }
        public ColorSlot slot(int index) { return slots.get(index); }
        public static ColorGroup single(String groupDisplayName, String slotDisplayName, ColorSource source) {
            return new ColorGroup(groupDisplayName, java.util.Collections.singletonList(new ColorSlot(slotDisplayName, source)));
        }
    }

    public static final class ColorMath {
        private ColorMath() {}
        public static float[] rgbToHsv(int argb) {
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
            float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf)), delta = max - min;
            float h = 0, s = (max == 0) ? 0 : delta / max, v = max;
            if (delta != 0) {
                if (max == rf) h = ((gf - bf) / delta) % 6.0f;
                else if (max == gf) h = ((bf - rf) / delta) + 2.0f;
                else h = ((rf - gf) / delta) + 4.0f;
                h *= 60.0f;
                if (h < 0) h += 360.0f;
            }
            return new float[]{h / 360.0f, s, v};
        }
        public static int hsvToRgb(float h, float s, float v) {
            float hueDeg = h * 360.0f, c = v * s, x = c * (1.0f - Math.abs((hueDeg / 60.0f) % 2.0f - 1.0f)), m = v - c;
            float rF, gF, bF;
            switch (((int) hueDeg) / 60) {
                case 0: rF = c; gF = x; bF = 0; break;
                case 1: rF = x; gF = c; bF = 0; break;
                case 2: rF = 0; gF = c; bF = x; break;
                case 3: rF = 0; gF = x; bF = c; break;
                case 4: rF = x; gF = 0; bF = c; break;
                default: rF = c; gF = 0; bF = x; break;
            }
            return 0xFF000000 | ((int) ((rF + m) * 255) << 16) | ((int) ((gF + m) * 255) << 8) | (int) ((bF + m) * 255);
        }
        public static boolean isDarkColor(int argb) {
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            return (r * 0.299 + g * 0.587 + b * 0.114) < 128;
        }
        public static int blendGrayscale(int base, float t) {
            t = 1.0f - t;
            return 0xFF000000 | ((int) (((base >> 16) & 0xFF) * t) << 16) | ((int) (((base >> 8) & 0xFF) * t) << 8) | (int) ((base & 0xFF) * t);
        }
    }

    public static final class ColorPreviewComponent {
        public static final int PREVIEW_BAR_H = 16;
        private static final int VALUE_RIGHT_MARGIN = 4;
        public void render(GuiGraphics g, int previewX, int previewY, int previewW, int initialColor, int currentColor, boolean isHexDisplay) {
            int midX = previewX + previewW / 2;
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            g.fill(previewX, previewY, midX, previewY + PREVIEW_BAR_H, initialColor);
            g.fill(midX, previewY, previewX + previewW, previewY + PREVIEW_BAR_H, currentColor);
            int borderColor = 0xFF666666;
            g.hLine(previewX, previewX + previewW, previewY, borderColor);
            g.hLine(previewX, previewX + previewW, previewY + PREVIEW_BAR_H, borderColor);
            g.vLine(previewX, previewY, previewY + PREVIEW_BAR_H, borderColor);
            g.vLine(previewX + previewW, previewY, previewY + PREVIEW_BAR_H, borderColor);
            g.vLine(midX, previewY, previewY + PREVIEW_BAR_H, borderColor);
            String currentValueStr = formatColorValue(currentColor, isHexDisplay);
            String initialValueStr = formatColorValue(initialColor, isHexDisplay);
            int newColorTextColor = ColorMath.isDarkColor(currentColor) ? 0xFFFFFFFF : 0xFF000000;
            int oldColorTextColor = ColorMath.isDarkColor(initialColor) ? 0xFFFFFFFF : 0xFF000000;
            int colorValueY = previewY + (PREVIEW_BAR_H - font.lineHeight) / 2 + 1;
            g.drawString(font, currentValueStr, midX + (previewW / 2 - font.width(currentValueStr)) / 2, colorValueY, newColorTextColor, false);
            g.drawString(font, initialValueStr, previewX + (previewW / 2 - font.width(initialValueStr)) / 2, colorValueY, oldColorTextColor, false);
        }
        public boolean isClickOnInitialColor(double mouseX, double mouseY, int previewX, int previewW, int previewY) {
            int midX = previewX + previewW / 2;
            return mouseX >= previewX && mouseX < midX && mouseY >= previewY && mouseY < previewY + PREVIEW_BAR_H;
        }
        private static String formatColorValue(int color, boolean hexDisplay) {
            return hexDisplay ? String.format("#%06X", color & 0xFFFFFF) : String.valueOf(color & 0xFFFFFF);
        }
    }

    public static final class SwatchSelectorComponent {
        public static final int ROW_H = 20;
        private static final int SWATCH_SIZE = 14, SWATCH_GAP = 12, SWATCH_TEXT_GAP = 3, LEFT_PADDING = 6;
        public void render(GuiGraphics g, int mouseX, int mouseY, ColorGroup group, int activeSlotIndex, int sectionTop, int contentX) {
            if (group == null || group.size() <= 1) return;
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            int textColor = com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager.getTextColor();
            int swatchY = sectionTop + (ROW_H - SWATCH_SIZE) / 2;
            int itemX = contentX + LEFT_PADDING;
            for (int i = 0; i < group.size(); i++) {
                String name = group.slot(i).displayName();
                int slotColor = group.slot(i).source().getColor();
                g.fill(itemX, swatchY, itemX + SWATCH_SIZE, swatchY + SWATCH_SIZE, slotColor);
                int swatchBorder = (i == activeSlotIndex) ? 0xFFFFFFFF : 0xFF444444;
                g.hLine(itemX - 1, itemX + SWATCH_SIZE, swatchY - 1, swatchBorder);
                g.hLine(itemX - 1, itemX + SWATCH_SIZE, swatchY + SWATCH_SIZE, swatchBorder);
                g.vLine(itemX - 1, swatchY - 1, swatchY + SWATCH_SIZE, swatchBorder);
                g.vLine(itemX + SWATCH_SIZE, swatchY - 1, swatchY + SWATCH_SIZE, swatchBorder);
                TextRenderer.draw(g, name, itemX + SWATCH_SIZE + SWATCH_TEXT_GAP, sectionTop + (ROW_H - font.lineHeight) / 2 + 1, textColor);
                itemX += SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(name) + SWATCH_GAP;
            }
        }
        public int hitTest(double mouseX, double mouseY, ColorGroup group, int sectionTop, int contentX) {
            if (group == null || group.size() <= 1) return -1;
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            int swatchY = sectionTop + (ROW_H - SWATCH_SIZE) / 2;
            int itemX = contentX + LEFT_PADDING;
            for (int i = 0; i < group.size(); i++) {
                String name = group.slot(i).displayName();
                int itemW = SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(name);
                if (mouseX >= itemX && mouseX < itemX + itemW && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE) return i;
                itemX += itemW + SWATCH_GAP;
            }
            return -1;
        }
        public int computeMinWidth(ColorGroup group) {
            if (group == null || group.size() <= 1) return 0;
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            int total = LEFT_PADDING + 4;
            for (int i = 0; i < group.size(); i++) {
                if (i > 0) total += SWATCH_GAP;
                total += SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(group.slot(i).displayName());
            }
            return total;
        }
    }

    public static final class ColorBlockComponent {
        public static final int DEFAULT_SIZE = 8;
        public void render(GuiGraphics g, int x, int y, int size, int color) { g.fill(x, y, x + size, y + size, color); }
        public void render(GuiGraphics g, int x, int y, int color) { render(g, x, y, DEFAULT_SIZE, color); }
    }
}
