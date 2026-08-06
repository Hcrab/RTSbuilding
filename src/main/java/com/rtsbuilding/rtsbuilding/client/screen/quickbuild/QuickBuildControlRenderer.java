package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShapeOption;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiSelectionAnimationSet;
import com.rtsbuilding.rtsbuilding.uikit.canvas.QuickBuildChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Quick Build 的 LegacyGuiGraphics 绘制适配层。
 *
 * <p>绘制只读取 Core 快照和控件坐标。顶栏只画 Build/Destroy；Smart Fill 作为 Build
 * Tools 页的内容绘制，绝不再占用第三个模式按钮。控件实例和输入归
 * {@link QuickBuildControlSurface}，实际玩法调用仍留在适配器与面板。</p>
 */
final class QuickBuildControlRenderer {
    private final UiSelectionAnimationSet<QuickBuildUiMode> modeAnimations =
            new UiSelectionAnimationSet<QuickBuildUiMode>(SystemUiClock.INSTANCE,
                    Arrays.asList(QuickBuildUiMode.values()), 100L, UiEasing.EASE_OUT_CUBIC);

    void render(QuickBuildControlSurface controls, LegacyGuiGraphics graphics,
            MinecraftUiCanvas canvas, BuilderScreen screen, QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout, int mouseX, int mouseY, float partialTick) {
        renderModeToggles(controls, graphics, canvas, screen, state, layout, mouseX, mouseY);
        for (int i = 0; i < 2; i++) controls.catalogButton(i).render(graphics, mouseX, mouseY, partialTick);

        if (state.mode == QuickBuildUiMode.SMART_FILL) {
            renderSmartFillControls(controls, graphics, screen, state, layout, mouseX, mouseY, partialTick);
            return;
        }
        if (state.convenienceMode()) {
            renderConvenienceTools(controls, graphics, screen, state, layout, mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawString(screen.font(), I18n.format("screen.rtsbuilding.quick_build.shape"),
                layout.sectionLabelX, layout.sectionTitleY, QuickBuildStyle.SECTION_TEXT.toArgb(), false);
        renderShapes(controls, graphics, state, layout, mouseX, mouseY, partialTick);
        graphics.drawString(screen.font(), I18n.format("screen.rtsbuilding.quick_build.fill"),
                layout.rightX, layout.sectionTitleY, QuickBuildStyle.SECTION_TEXT.toArgb(), false);
        if (state.chainMode()) {
            renderChainLimit(controls, graphics, screen, state, layout, mouseX, mouseY, partialTick);
        } else {
            renderControls(controls, graphics, state, layout, mouseX, mouseY, partialTick);
        }
    }

    void renderTooltip(QuickBuildControlSurface controls, LegacyGuiGraphics graphics,
            BuilderScreen screen, QuickBuildUiState state, int mouseX, int mouseY) {
        if (state.mode == QuickBuildUiMode.SMART_FILL) {
            if (isHovered(controls.smartFillToolButton(), mouseX, mouseY)) {
                QuickBuildHoverTooltipRenderer.render(graphics, screen,
                        I18n.format("screen.rtsbuilding.quick_build.mode_smart_fill"),
                        I18n.format("screen.rtsbuilding.quick_build.smart_fill_default"), mouseX, mouseY);
            }
            return;
        }
        if (state.convenienceMode()) {
            for (int i = 0; i < controls.convenienceToolButtonCount(); i++) {
                WindowButton button = controls.convenienceToolButton(i);
                if (!isHovered(button, mouseX, mouseY)) continue;
                String tooltip = i == 0 ? "screen.rtsbuilding.quick_build.convenience.repeat_tooltip"
                        : i == 1 ? "screen.rtsbuilding.quick_build.convenience.chunk_tooltip"
                        : "screen.rtsbuilding.quick_build.convenience.tree_tooltip";
                QuickBuildHoverTooltipRenderer.render(graphics, screen,
                        buttonText(i), I18n.format(tooltip), mouseX, mouseY);
                return;
            }
            return;
        }
        for (int i = 0; i < controls.shapeButtonCount() && i < state.shapes.size(); i++) {
            WindowButton button = controls.shapeButton(i);
            if (isHovered(button, mouseX, mouseY)) {
                QuickBuildHoverTooltipRenderer.render(graphics, screen,
                        I18n.format(QuickBuildIconCatalog.tooltipKey(state.shapes.get(i).shape)),
                        "", mouseX, mouseY);
                return;
            }
        }
    }

    private void renderModeToggles(QuickBuildControlSurface controls, LegacyGuiGraphics graphics,
            MinecraftUiCanvas canvas, BuilderScreen screen, QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout, int mouseX, int mouseY) {
        renderModeToggle(controls, graphics, canvas, screen, state, layout.buildMode,
                QuickBuildUiMode.BUILD, I18n.format("screen.rtsbuilding.quick_build.mode_build"), mouseX, mouseY);
        renderModeToggle(controls, graphics, canvas, screen, state, layout.destroyMode,
                QuickBuildUiMode.DESTROY, I18n.format("screen.rtsbuilding.quick_build.mode_destroy"), mouseX, mouseY);
    }

    private void renderModeToggle(QuickBuildControlSurface controls, LegacyGuiGraphics graphics,
            MinecraftUiCanvas canvas, BuilderScreen screen, QuickBuildUiState state, UiRect area,
            QuickBuildUiMode mode, String label, int mouseX, int mouseY) {
        boolean enabled = mode != QuickBuildUiMode.DESTROY || state.destroyEnabled;
        boolean active = (mode == QuickBuildUiMode.BUILD
                ? state.mode != QuickBuildUiMode.DESTROY : state.mode == QuickBuildUiMode.DESTROY) && enabled;
        boolean hovered = area.contains(mouseX, mouseY);
        boolean pressed = controls.pressedMode() == mode && hovered;
        double strength = this.modeAnimations.value(mode, active || pressed, Config.isUiAnimationsEnabled());
        QuickBuildStyle.ModeVisual visual = QuickBuildStyle.mode(enabled, active, hovered);
        QuickBuildChromeRenderer.renderMode(canvas, area, visual, strength);
        int x = (int) area.getX();
        int y = (int) area.getY();
        int width = (int) area.getWidth();
        int height = (int) area.getHeight();
        int labelX = x + Math.max(QuickBuildWindowLayout.MODE_LABEL_MIN_INSET,
                (width - screen.font().getStringWidth(label)) / 2);
        int labelY = y + (height - screen.font().FONT_HEIGHT) / 2;
        graphics.drawString(screen.font(), label, labelX, labelY, visual.text.toArgb(), false);
    }

    private static void renderSmartFillControls(QuickBuildControlSurface controls,
            LegacyGuiGraphics graphics, BuilderScreen screen, QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(screen.font(), I18n.format("screen.rtsbuilding.quick_build.mode_smart_fill"),
                layout.sectionLabelX, layout.sectionTitleY, QuickBuildStyle.SECTION_TEXT.toArgb(), false);
        controls.smartFillToolButton().render(graphics, mouseX, mouseY, partialTick);
        renderSliderLabel(graphics, screen, "screen.rtsbuilding.quick_build.smart_fill.max_blocks",
                layout.rightX, layout.smartFillParameterLabelY(0));
        controls.smartFillMaxBlocksSlider().render(graphics, mouseX, mouseY, partialTick);
        drawSliderValue(graphics, screen, controls.smartFillMaxBlocksSlider().getWidth(),
                layout, layout.smartFillParameterSliderY(0), state.smartFillMaxBlocks);
        renderSliderLabel(graphics, screen, "screen.rtsbuilding.quick_build.smart_fill.diameter",
                layout.rightX, layout.smartFillParameterLabelY(1));
        controls.smartFillDiameterSlider().render(graphics, mouseX, mouseY, partialTick);
        drawSliderValue(graphics, screen, controls.smartFillDiameterSlider().getWidth(),
                layout, layout.smartFillParameterSliderY(1), state.smartFillDiameter);
    }

    private static void renderConvenienceTools(QuickBuildControlSurface controls,
            LegacyGuiGraphics graphics, BuilderScreen screen, QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(screen.font(), I18n.format("screen.rtsbuilding.quick_build.catalog_tools"),
                layout.sectionLabelX, layout.sectionTitleY, QuickBuildStyle.SECTION_TEXT.toArgb(), false);
        for (int i = 0; i < controls.convenienceToolButtonCount(); i++) {
            controls.convenienceToolButton(i).render(graphics, mouseX, mouseY, partialTick);
        }
        List<QuickBuildUiConvenienceParameter> parameters =
                QuickBuildControlSurface.activeParameters(state.convenienceTool);
        for (int i = 0; i < parameters.size(); i++) {
            QuickBuildUiConvenienceParameter parameter = parameters.get(i);
            String key = "screen.rtsbuilding.quick_build.parameter."
                    + parameter.name().toLowerCase(java.util.Locale.ROOT);
            renderSliderLabel(graphics, screen, key, layout.rightX, layout.convenienceParameterLabelY(i));
            controls.convenienceSlider(parameter).render(graphics, mouseX, mouseY, partialTick);
            drawSliderValue(graphics, screen, controls.convenienceSlider(parameter).getWidth(), layout,
                    layout.convenienceParameterSliderY(i), state.convenienceSettings.value(parameter));
        }
    }

    private static void renderSliderLabel(LegacyGuiGraphics graphics, BuilderScreen screen,
            String translationKey, int x, int y) {
        graphics.drawString(screen.font(), I18n.format(translationKey), x, y,
                QuickBuildStyle.SECTION_TEXT.toArgb(), false);
    }

    private static void drawSliderValue(LegacyGuiGraphics graphics, BuilderScreen screen,
            int sliderWidth, QuickBuildWindowLayout.Geometry layout, int sliderY, int value) {
        graphics.drawString(screen.font(), Integer.toString(value), layout.chainValueX(sliderWidth),
                sliderY + QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET,
                QuickBuildStyle.VALUE_TEXT.toArgb(), false);
    }

    private static void renderShapes(QuickBuildControlSurface controls, LegacyGuiGraphics graphics,
            QuickBuildUiState state, QuickBuildWindowLayout.Geometry layout,
            int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < controls.shapeButtonCount(); i++) {
            int slotX = layout.shapeX(i);
            int slotY = layout.shapeY(i);
            QuickBuildUiShapeOption option = state.shapes.get(i);
            if (state.mode == QuickBuildUiMode.DESTROY && option.shape == QuickBuildUiShape.CHAIN
                    && state.chainMode()) {
                graphics.fill(slotX, slotY, slotX + QuickBuildWindowLayout.SHAPE_SLOT,
                        slotY + QuickBuildWindowLayout.SHAPE_SLOT,
                        QuickBuildStyle.CHAIN_SELECTED_BORDER.toArgb());
                int inset = QuickBuildWindowLayout.SHAPE_SELECTED_INSET;
                graphics.fill(slotX + inset, slotY + inset,
                        slotX + QuickBuildWindowLayout.SHAPE_SLOT - inset,
                        slotY + QuickBuildWindowLayout.SHAPE_SLOT - inset,
                        QuickBuildStyle.CHAIN_SELECTED_BACKGROUND.toArgb());
            }
            controls.shapeButton(i).render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static void renderChainLimit(QuickBuildControlSurface controls, LegacyGuiGraphics graphics,
            BuilderScreen screen, QuickBuildUiState state, QuickBuildWindowLayout.Geometry layout,
            int mouseX, int mouseY, float partialTick) {
        graphics.drawString(screen.font(), I18n.format("screen.rtsbuilding.quick_build.chain_limit_label"),
                layout.rightX, layout.chainLabelY, QuickBuildStyle.SECTION_TEXT.toArgb(), false);
        controls.chainLimitSlider().render(graphics, mouseX, mouseY, partialTick);
        drawSliderValue(graphics, screen, controls.chainLimitSlider().getWidth(), layout,
                layout.chainSliderY, state.chainLimit);
    }

    private static void renderControls(QuickBuildControlSurface controls, LegacyGuiGraphics graphics,
            QuickBuildUiState state, QuickBuildWindowLayout.Geometry layout,
            int mouseX, int mouseY, float partialTick) {
        List<QuickBuildUiControl> regular = controlsWithoutConnect(state);
        for (int i = 0; i < controls.controlButtonCount(); i++) {
            WindowButton button = controls.controlButton(i);
            QuickBuildUiControl control = regular.get(i);
            button.enabled = control.enabled;
            button.render(graphics, mouseX, mouseY, partialTick);
            renderControlIndicator(graphics, layout.rightX, layout.controlY(i), control.selected,
                    isHovered(button, mouseX, mouseY));
        }
        WindowButton connect = controls.connectToggle();
        if (connect == null) return;
        QuickBuildUiControl control = state.control(QuickBuildUiControl.Id.CONNECT);
        connect.enabled = control != null && control.enabled;
        connect.render(graphics, mouseX, mouseY, partialTick);
        renderControlIndicator(graphics, layout.rightX, layout.controlY(controls.controlButtonCount()),
                control != null && control.selected, isHovered(connect, mouseX, mouseY));
    }

    private static void renderControlIndicator(LegacyGuiGraphics graphics, int rowX, int rowY,
            boolean selected, boolean hovered) {
        int offset = selected ? QuickBuildIconCatalog.MODE_STATE_H * 2
                : (hovered ? QuickBuildIconCatalog.MODE_STATE_H : 0);
        RtsTextureRenderer.drawTextureHighPrecision(graphics, QuickBuildIconCatalog.SELECTION_DOT,
                rowX + QuickBuildWindowLayout.CONTROL_ICON_INSET,
                rowY + QuickBuildWindowLayout.CONTROL_ICON_INSET,
                QuickBuildWindowLayout.CONTROL_ICON_SIZE, QuickBuildWindowLayout.CONTROL_ICON_SIZE,
                0, offset, QuickBuildIconCatalog.MODE_SHEET_W, QuickBuildIconCatalog.MODE_STATE_H,
                QuickBuildIconCatalog.MODE_SHEET_W, QuickBuildIconCatalog.MODE_SHEET_H,
                0, QuickBuildStyle.ICON_TINT.toArgb());
    }

    private static String buttonText(int index) {
        if (index == 0) return I18n.format("screen.rtsbuilding.quick_build.convenience.repeat_short");
        if (index == 1) return I18n.format("screen.rtsbuilding.quick_build.convenience.chunk_short");
        return I18n.format("screen.rtsbuilding.quick_build.convenience.tree_short");
    }

    private static boolean isHovered(WindowButton button, int mouseX, int mouseY) {
        return button != null && mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    private static List<QuickBuildUiControl> controlsWithoutConnect(QuickBuildUiState state) {
        List<QuickBuildUiControl> values = new ArrayList<QuickBuildUiControl>();
        for (QuickBuildUiControl control : state.controls) {
            if (control.id != QuickBuildUiControl.Id.CONNECT) values.add(control);
        }
        return values;
    }
}
