package com.rtsbuilding.rtsbuilding.client.presentation.panel.gear;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorPickerPanel.ColorBlockComponent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorPickerPanel.ColorGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorPickerPanel.ColorSlot;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorPickerPanel.ColorSource;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ColorPickerButton;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ResetButton;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.ToggleSwitch;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoundaryPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelectionPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.InteractionTargetPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.LinkedStoragePass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenderingSection extends SettingsSection {

    private static final double ALPHA_MIN = 0.02;
    private static final double ALPHA_MAX = 0.80;
    private static final int COLOR_BLOCK_GAP = 4;
    private static final int RESET_BTN_GAP = 4;

    private static final int ROW_FLOW = 0;
    private static final int ROW_SMOOTH = 1;
    private static final int ROW_UI_SMOOTH = 2;
    private static final int ROW_DEPTH = 3;
    private static final int ROW_BARRIER_COLOR = 5;
    private static final int ROW_TARGET_COLOR = 6;
    private static final int ROW_SELECTION_COLOR = 7;
    private static final int ROW_LINKED_STORAGE_COLOR = 8;
    private static final int ALWAYS_VISIBLE_ROW_COUNT = 4;
    private static final int MIN_CONTENT_H = 167;
    private static final int EXTRA_ROWS_H = 20;

    private final ToggleSwitch depthToggle = new ToggleSwitch();
    private final ToggleSwitch flowToggle = new ToggleSwitch();
    private final ToggleSwitch smoothToggle = new ToggleSwitch();
    private final ToggleSwitch uiSmoothToggle = new ToggleSwitch();
    private final ScaleSliderComponent alphaSlider = new ScaleSliderComponent();
    private final SliderTrack alphaTrack = new SliderTrack();

    private final ColorPickerButton colorPickerButton = new ColorPickerButton();
    private final ColorGroup barrierColorGroup = ColorGroup.single("渲染设置", "屏障颜色", new ColorSource() {
        @Override public int getColor() { return BoundaryPass.barrierColor; }
        @Override public void setColor(int color) { BoundaryPass.barrierColor = color; }
    });
    private final ColorBlockComponent colorBlock = new ColorBlockComponent();
    private int barrierBlockX, barrierBlockY;
    private final TooltipController barrierTooltip = TooltipController.builder().build();

    private final ColorPickerButton targetColorPickerButton = new ColorPickerButton();
    private final ColorGroup targetColorGroup = new ColorGroup("渲染设置", List.of(
            new ColorSlot("方块目标颜色", new ColorSource() {
                @Override public int getColor() { return InteractionTargetPass.blockTargetColor; }
                @Override public void setColor(int color) { InteractionTargetPass.blockTargetColor = color; }
            }),
            new ColorSlot("实体目标颜色", new ColorSource() {
                @Override public int getColor() { return InteractionTargetPass.entityTargetColor; }
                @Override public void setColor(int color) { InteractionTargetPass.entityTargetColor = color; }
            })
    ));
    private final ColorBlockComponent targetColorBlock = new ColorBlockComponent();
    private int targetBlockX, entityBlockX, targetBlockY;
    private final TooltipController blockTargetTooltip = TooltipController.builder().build();
    private final TooltipController entityTargetTooltip = TooltipController.builder().build();

    private final ColorPickerButton selectionColorPickerButton = new ColorPickerButton();

    private final ResetButton flowResetBtn = new ResetButton();
    private final ResetButton smoothResetBtn = new ResetButton();
    private final ResetButton uiSmoothResetBtn = new ResetButton();
    private final ResetButton depthResetBtn = new ResetButton();
    private final ResetButton alphaResetBtn = new ResetButton();
    private final ResetButton barrierResetBtn = new ResetButton();
    private final ResetButton targetResetBtn = new ResetButton();
    private final ResetButton selectionResetBtn = new ResetButton();
    private final ResetButton linkedResetBtn = new ResetButton();

    private final ColorGroup selectionColorGroup = new ColorGroup("渲染设置", List.of(
            new ColorSlot("框选线框颜色", new ColorSource() {
                @Override public int getColor() { return BoxSelectionPass.selectionColor; }
                @Override public void setColor(int color) { BoxSelectionPass.selectionColor = color; }
            }),
            new ColorSlot("线框间隙颜色", new ColorSource() {
                @Override public int getColor() { return BoxSelectionPass.selectionGapColor; }
                @Override public void setColor(int color) { BoxSelectionPass.selectionGapColor = color; }
            }),
            new ColorSlot("覆盖层颜色", new ColorSource() {
                @Override public int getColor() { return BoxSelectionPass.previewOverlayColor; }
                @Override public void setColor(int color) { BoxSelectionPass.previewOverlayColor = color; }
            }),
            new ColorSlot("框选实体颜色", new ColorSource() {
                @Override public int getColor() { return BoxSelectionPass.entitySelectionColor; }
                @Override public void setColor(int color) { BoxSelectionPass.entitySelectionColor = color; }
            })
    ));
    private final ColorBlockComponent selectionColorBlock = new ColorBlockComponent();
    private int selBlockX, selGapBlockX, selOverlayBlockX, selEntityBlockX, selBlockY;
    private final TooltipController selWireframeTooltip = TooltipController.builder().build();
    private final TooltipController selGapTooltip = TooltipController.builder().build();
    private final TooltipController selOverlayTooltip = TooltipController.builder().build();
    private final TooltipController selEntityTooltip = TooltipController.builder().build();

    private final ColorPickerButton linkedColorPickerButton = new ColorPickerButton();
    private final ColorGroup linkedColorGroup = new ColorGroup("渲染设置", List.of(
            new ColorSlot("绑定容器线框颜色（双向）", new ColorSource() {
                @Override public int getColor() { return LinkedStoragePass.bidirectionalColor; }
                @Override public void setColor(int color) { LinkedStoragePass.bidirectionalColor = color; }
            }),
            new ColorSlot("绑定容器线框颜色（仅提取）", new ColorSource() {
                @Override public int getColor() { return LinkedStoragePass.extractOnlyColor; }
                @Override public void setColor(int color) { LinkedStoragePass.extractOnlyColor = color; }
            })
    ));
    private final ColorBlockComponent linkedColorBlock = new ColorBlockComponent();
    private int linkedBiBlockX, linkedExtBlockX, linkedBlockY;
    private final TooltipController linkedBiTooltip = TooltipController.builder().build();
    private final TooltipController linkedExtTooltip = TooltipController.builder().build();

    private final Map<String, String> translationCache = new HashMap<>();
    private final AnimFloat heightAnim = AnimFloat.expand();
    private boolean lastDepthEnabled;

    public RenderingSection() {
        super("screen.rtsbuilding.settings.category.rendering");
        setExpanded(false);
        lastDepthEnabled = BoxSelectionPass.depthTestEnabled;
        heightAnim.snapTo(lastDepthEnabled ? 1.0f : 0.0f);

        flowResetBtn.setResetAction(() -> BoxSelectionPass.flowAnimationEnabled = true);
        smoothResetBtn.setResetAction(() -> CornerBracketRenderer.SmoothTarget.enabled = true);
        uiSmoothResetBtn.setResetAction(() -> AnimFloat.setEnabled(true));
        depthResetBtn.setResetAction(() -> BoxSelectionPass.depthTestEnabled = true);
        alphaResetBtn.setResetAction(() -> CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = 0.10f);
        barrierResetBtn.setResetAction(() -> BoundaryPass.barrierColor = 0xFFFFCC00);
        targetResetBtn.setResetAction(() -> {
            InteractionTargetPass.blockTargetColor = 0xFFF69C31;
            InteractionTargetPass.entityTargetColor = 0xFF4D99FF;
        });
        selectionResetBtn.setResetAction(() -> {
            BoxSelectionPass.selectionColor = 0xFFFFFFFF;
            BoxSelectionPass.selectionGapColor = 0xFF000000;
            BoxSelectionPass.previewOverlayColor = 0xFF4D80FF;
            BoxSelectionPass.entitySelectionColor = 0xFF4CAF50;
        });
        linkedResetBtn.setResetAction(() -> {
            LinkedStoragePass.bidirectionalColor = 0xFF4CAF50;
            LinkedStoragePass.extractOnlyColor = 0xFFFF4CD1;
        });
    }

    @Override
    protected int getContentRowCount() { return ALWAYS_VISIBLE_ROW_COUNT + 5; }

    @Override
    protected int getEffectiveContentHeight() {
        return MIN_CONTENT_H + Math.round(EXTRA_ROWS_H * heightAnim.get());
    }

    private String t(String key) {
        return translationCache.computeIfAbsent(key, k -> Component.translatable(k).getString());
    }

    private void renderRowLabel(GuiGraphics g, String text, int x, int lineY) {
        TextRenderer.draw(g, text, x + LEFT_PAD, lineY + 2, getTextColor());
    }

    private void renderToggleRow(GuiGraphics g, int mx, int my, int x, int w, int lineY,
                                  String label, ToggleSwitch toggle, boolean state, ResetButton resetBtn) {
        renderRowLabel(g, label, x, lineY);
        int textCenterY = lineY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int toggleX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE - RESET_BTN_GAP - 28;
        toggle.render(g, toggleX, textCenterY - 7, state);
        resetBtn.render(g, mx, my, x + w - RIGHT_PAD - ResetButton.BTN_SIZE, textCenterY - ResetButton.BTN_SIZE / 2);
    }

    private int renderColorRow(GuiGraphics g, int mx, int my, int x, int w, int cursorY,
                                String label, ColorBlockComponent block, int[] colorValues, int[] colorPosX,
                                ColorPickerButton picker, ResetButton resetBtn) {
        renderRowLabel(g, label, x, cursorY);
        int labelW = Minecraft.getInstance().font.width(label);
        int textCenterY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int blockX = x + LEFT_PAD + labelW + COLOR_BLOCK_GAP;
        for (int i = 0; i < colorValues.length; i++) {
            int bx = blockX + i * (ColorBlockComponent.DEFAULT_SIZE + 2);
            if (colorPosX != null && i < colorPosX.length) colorPosX[i] = bx;
            block.render(g, bx, textCenterY - ColorBlockComponent.DEFAULT_SIZE / 2, colorValues[i]);
        }
        picker.render(g, mx, my, x + w - RIGHT_PAD - ResetButton.BTN_SIZE - RESET_BTN_GAP - ColorPickerButton.BTN_SIZE,
                textCenterY - ColorPickerButton.BTN_SIZE / 2);
        resetBtn.render(g, mx, my, x + w - RIGHT_PAD - ResetButton.BTN_SIZE,
                textCenterY - ResetButton.BTN_SIZE / 2);
        return cursorY + getLineHeight();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, int x, int y, int w, int lineCount) {
        boolean depthOn = BoxSelectionPass.depthTestEnabled;
        if (depthOn != lastDepthEnabled) { lastDepthEnabled = depthOn; heightAnim.target(depthOn ? 1.0f : 0.0f); }

        int cursorY = y + 4, lh = getLineHeight();

        renderToggleRow(g, mx, my, x, w, cursorY, t("screen.rtsbuilding.settings.flow_animation"), flowToggle, BoxSelectionPass.flowAnimationEnabled, flowResetBtn);
        cursorY += lh;
        renderToggleRow(g, mx, my, x, w, cursorY, t("screen.rtsbuilding.settings.smooth_animation"), smoothToggle, CornerBracketRenderer.SmoothTarget.enabled, smoothResetBtn);
        cursorY += lh;
        renderToggleRow(g, mx, my, x, w, cursorY, t("screen.rtsbuilding.settings.ui_smooth_animation"), uiSmoothToggle, AnimFloat.isEnabled(), uiSmoothResetBtn);
        cursorY += lh;
        renderToggleRow(g, mx, my, x, w, cursorY, t("screen.rtsbuilding.settings.depth_test"), depthToggle, BoxSelectionPass.depthTestEnabled, depthResetBtn);
        cursorY += lh;

        if (depthOn) {
            String alphaLabel = t("screen.rtsbuilding.settings.overlay_alpha")
                    + String.format(java.util.Locale.ROOT, "：%.0f%%", CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA * 100);
            renderRowSlider(g, mx, my, x, w, cursorY, alphaLabel, alphaSlider, alphaTrack,
                    ALPHA_MIN, ALPHA_MAX, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, alphaResetBtn);
            cursorY += lh;
        }

        this.barrierBlockX = x + LEFT_PAD + Minecraft.getInstance().font.width(t("screen.rtsbuilding.settings.barrier_color")) + COLOR_BLOCK_GAP;
        this.barrierBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - ColorBlockComponent.DEFAULT_SIZE / 2;
        cursorY = renderColorRow(g, mx, my, x, w, cursorY, t("screen.rtsbuilding.settings.barrier_color"),
                colorBlock, new int[]{BoundaryPass.barrierColor}, null, colorPickerButton, barrierResetBtn);

        int targetLabelW = Minecraft.getInstance().font.width(t("screen.rtsbuilding.settings.target_color"));
        this.targetBlockX = x + LEFT_PAD + targetLabelW + COLOR_BLOCK_GAP;
        this.targetBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - ColorBlockComponent.DEFAULT_SIZE / 2;
        int[] targetColors = {InteractionTargetPass.blockTargetColor, InteractionTargetPass.entityTargetColor};
        int[] targetPosX = new int[2];
        cursorY = renderColorRow(g, mx, my, x, w, cursorY, t("screen.rtsbuilding.settings.target_color"),
                targetColorBlock, targetColors, targetPosX, targetColorPickerButton, targetResetBtn);
        this.entityBlockX = targetPosX.length > 1 ? targetPosX[1] : this.targetBlockX;

        int selLabelW = Minecraft.getInstance().font.width(t("screen.rtsbuilding.settings.selection_color"));
        this.selBlockX = x + LEFT_PAD + selLabelW + COLOR_BLOCK_GAP;
        this.selBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - ColorBlockComponent.DEFAULT_SIZE / 2;
        int[] selColors = {BoxSelectionPass.selectionColor, BoxSelectionPass.selectionGapColor,
                BoxSelectionPass.previewOverlayColor, BoxSelectionPass.entitySelectionColor};
        int[] selPosX = new int[4];
        cursorY = renderColorRow(g, mx, my, x, w, cursorY, t("screen.rtsbuilding.settings.selection_color"),
                selectionColorBlock, selColors, selPosX, selectionColorPickerButton, selectionResetBtn);
        this.selGapBlockX = selPosX.length > 1 ? selPosX[1] : this.selBlockX;
        this.selOverlayBlockX = selPosX.length > 2 ? selPosX[2] : this.selBlockX;
        this.selEntityBlockX = selPosX.length > 3 ? selPosX[3] : this.selBlockX;

        int linkedLabelW = Minecraft.getInstance().font.width(t("screen.rtsbuilding.settings.linked_storage_color"));
        this.linkedBiBlockX = x + LEFT_PAD + linkedLabelW + COLOR_BLOCK_GAP;
        this.linkedBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - ColorBlockComponent.DEFAULT_SIZE / 2;
        int[] linkedColors = {LinkedStoragePass.bidirectionalColor, LinkedStoragePass.extractOnlyColor};
        int[] linkedPosX = new int[2];
        cursorY = renderColorRow(g, mx, my, x, w, cursorY, t("screen.rtsbuilding.settings.linked_storage_color"),
                linkedColorBlock, linkedColors, linkedPosX, linkedColorPickerButton, linkedResetBtn);
        this.linkedExtBlockX = linkedPosX.length > 1 ? linkedPosX[1] : this.linkedBiBlockX;
    }

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                          int contentX, int contentY, int contentW) {
        if (smoothToggle.handleClick(mouseX, mouseY)) { CornerBracketRenderer.SmoothTarget.enabled = !CornerBracketRenderer.SmoothTarget.enabled; return true; }
        if (flowToggle.handleClick(mouseX, mouseY)) { BoxSelectionPass.flowAnimationEnabled = !BoxSelectionPass.flowAnimationEnabled; return true; }
        if (uiSmoothToggle.handleClick(mouseX, mouseY)) { AnimFloat.setEnabled(!AnimFloat.isEnabled()); return true; }
        if (depthToggle.handleClick(mouseX, mouseY)) { BoxSelectionPass.depthTestEnabled = !BoxSelectionPass.depthTestEnabled; return true; }

        if (flowResetBtn.handleClick(mouseX, mouseY) || smoothResetBtn.handleClick(mouseX, mouseY)
                || uiSmoothResetBtn.handleClick(mouseX, mouseY) || depthResetBtn.handleClick(mouseX, mouseY)
                || alphaResetBtn.handleClick(mouseX, mouseY) || barrierResetBtn.handleClick(mouseX, mouseY)
                || targetResetBtn.handleClick(mouseX, mouseY) || selectionResetBtn.handleClick(mouseX, mouseY)
                || linkedResetBtn.handleClick(mouseX, mouseY)) return true;

        if (lineIndex == ROW_BARRIER_COLOR && colorPickerButton.handleClick(mouseX, mouseY)) return true;
        if (lineIndex == ROW_TARGET_COLOR && targetColorPickerButton.handleClick(mouseX, mouseY)) return true;
        if (lineIndex == ROW_SELECTION_COLOR && selectionColorPickerButton.handleClick(mouseX, mouseY)) return true;
        if (lineIndex == ROW_LINKED_STORAGE_COLOR && linkedColorPickerButton.handleClick(mouseX, mouseY)) return true;

        if (BoxSelectionPass.depthTestEnabled) {
            Double newVal = alphaSlider.handleClick(mouseX, mouseY,
                    alphaTrack.trackX, alphaTrack.trackY, alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
            if (newVal != null) { CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = newVal.floatValue(); return true; }
        }
        return false;
    }

    private void renderRowSlider(GuiGraphics g, int mx, int my, int x, int w, int lineY,
                                  String label, ScaleSliderComponent slider, SliderTrack trackPos,
                                  double min, double max, double value, ResetButton resetBtn) {
        TextRenderer.draw(g, label, x + LEFT_PAD, lineY + 2, getTextColor());
        int centerY = lineY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int controlStart = midControlX(x, w);
        trackPos.trackX = controlStart;
        trackPos.trackY = centerY - 2;
        trackPos.trackW = Mth.clamp((x + w - RIGHT_PAD - ResetButton.BTN_SIZE - RESET_BTN_GAP) - controlStart, 20, Integer.MAX_VALUE);
        trackPos.slider = slider;
        slider.render(g, mx, my, trackPos.trackX, trackPos.trackY, trackPos.trackW, min, max, value);
        resetBtn.render(g, mx, my, x + w - RIGHT_PAD - ResetButton.BTN_SIZE, centerY - ResetButton.BTN_SIZE / 2);
    }

    public void renderColorTooltips(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;
        int textColor = getTextColor();
        int shortcutColor = 0xFF999999;
        int bs = ColorBlockComponent.DEFAULT_SIZE;

        renderTooltipAt(g, mouseX, mouseY, barrierBlockX, barrierBlockY, bs, barrierTooltip,
                "屏障颜色\n用于标记区块边界的屏障线框颜色", textColor, shortcutColor, screen);
        renderTooltipAt(g, mouseX, mouseY, targetBlockX, targetBlockY, bs, blockTargetTooltip,
                "方块线框颜色\n点击模式下悬停方块的角支架线框颜色", textColor, shortcutColor, screen);
        renderTooltipAt(g, mouseX, mouseY, entityBlockX, targetBlockY, bs, entityTargetTooltip,
                "实体线框颜色\n点击模式下悬停实体的角支架线框颜色", textColor, shortcutColor, screen);
        renderTooltipAt(g, mouseX, mouseY, selBlockX, selBlockY, bs, selWireframeTooltip,
                "框选线框颜色\n选择模式下虚线框的主色段颜色", textColor, shortcutColor, screen);
        renderTooltipAt(g, mouseX, mouseY, selGapBlockX, selBlockY, bs, selGapTooltip,
                "线框间隙颜色\n选择模式下虚线框的间隙段颜色", textColor, shortcutColor, screen);
        renderTooltipAt(g, mouseX, mouseY, selOverlayBlockX, selBlockY, bs, selOverlayTooltip,
                "覆盖层颜色\n选择模式下预览阶段的半透明填充颜色", textColor, shortcutColor, screen);
        renderTooltipAt(g, mouseX, mouseY, selEntityBlockX, selBlockY, bs, selEntityTooltip,
                "框选实体颜色\n框选完成时选区内实体的角支架线框颜色", textColor, shortcutColor, screen);
        renderTooltipAt(g, mouseX, mouseY, linkedBiBlockX, linkedBlockY, bs, linkedBiTooltip,
                "绑定容器线框（双向）\n已绑定容器的双向模式（可存可取）角支架线框颜色", textColor, shortcutColor, screen);
        renderTooltipAt(g, mouseX, mouseY, linkedExtBlockX, linkedBlockY, bs, linkedExtTooltip,
                "绑定容器线框（仅提取）\n已绑定容器的仅提取模式角支架线框颜色", textColor, shortcutColor, screen);
    }

    private void renderTooltipAt(GuiGraphics g, int mx, int my, int bx, int by, int bs,
                                  TooltipController tooltip, String text, int textColor, int shortcutColor, Screen screen) {
        boolean hovered = mx >= bx && mx < bx + bs && my >= by && my < by + bs;
        tooltip.update(hovered, false);
        if (tooltip.shouldRender()) tooltip.render(g, bx, by, bs, bs, text, textColor, shortcutColor, screen.width, screen.height);
    }

    private static class SliderTrack {
        int trackX, trackY, trackW;
        ScaleSliderComponent slider;
    }

    public void setColorPickerPanel(ColorPickerPanel panel) {
        this.colorPickerButton.setColorPickerPanel(panel);
        this.colorPickerButton.setColorGroup(barrierColorGroup);
        this.targetColorPickerButton.setColorPickerPanel(panel);
        this.targetColorPickerButton.setColorGroup(targetColorGroup);
        this.selectionColorPickerButton.setColorPickerPanel(panel);
        this.selectionColorPickerButton.setColorGroup(selectionColorGroup);
        this.linkedColorPickerButton.setColorPickerPanel(panel);
        this.linkedColorPickerButton.setColorGroup(linkedColorGroup);
    }

    public void setColorPickerButtonParent(RtsPanel parent) {
        this.colorPickerButton.setParentPanel(parent);
        this.targetColorPickerButton.setParentPanel(parent);
        this.selectionColorPickerButton.setParentPanel(parent);
        this.linkedColorPickerButton.setParentPanel(parent);
    }

    public boolean isSliderDragging() { return BoxSelectionPass.depthTestEnabled && alphaSlider.isDragging(); }

    public void handleSliderDrag(double mouseX) {
        if (alphaSlider.isDragging() && alphaTrack.trackW > 0) {
            CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = (float) alphaSlider.handleDrag(mouseX, alphaTrack.trackX, alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
        }
    }

    public void endSliderDrag() { alphaSlider.endDrag(); }

    public boolean handleSliderScroll(double mouseX, double mouseY, double scrollY) {
        Double newVal = alphaSlider.handleScroll(mouseX, mouseY, scrollY, alphaTrack.trackX, alphaTrack.trackY, alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
        if (newVal != null) { CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = newVal.floatValue(); return true; }
        return false;
    }
}
