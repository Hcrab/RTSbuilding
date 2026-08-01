package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Quick Build 控件表面的 Minecraft 绘制适配器。
 *
 * <p>负责模式动画、形状/填充/连接控件、连锁滑杆文字和 Tooltip。控件实例、位置和输入
 * 捕获仍归 {@link QuickBuildControlSurface}；业务状态只读自 Core 快照。本类不创建控件、
 * 不提交 action，也不执行任何形状或世界副作用。</p>
 */
final class QuickBuildControlRenderer {
    private final UiSelectionAnimationSet<QuickBuildUiMode> modeAnimations =
            new UiSelectionAnimationSet<>(SystemUiClock.INSTANCE,
                    Arrays.asList(QuickBuildUiMode.values()), 100L, UiEasing.EASE_OUT_CUBIC);

    void render(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            MinecraftUiCanvas canvas,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX,
            int mouseY,
            float partialTick) {
        renderModeToggles(graphics, canvas, screen, state, layout, mouseX, mouseY);

        if (state.mode == QuickBuildUiMode.DESTROY) {
            for (int i = 0; i < 2; i++) {
                controls.catalogButton(i).render(graphics, mouseX, mouseY, partialTick);
            }
        }
        if (state.convenienceMode()) {
            renderConvenienceTools(
                    controls, graphics, screen, state, layout,
                    mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawString(screen.font(),
                Component.translatable("screen.rtsbuilding.quick_build.shape"),
                layout.sectionLabelX, layout.sectionTitleY,
                QuickBuildStyle.SECTION_TEXT.toArgb(), false);
        renderShapes(controls, graphics, state, layout, mouseX, mouseY, partialTick);

        graphics.drawString(screen.font(),
                Component.translatable("screen.rtsbuilding.quick_build.fill"),
                layout.rightX, layout.sectionTitleY,
                QuickBuildStyle.SECTION_TEXT.toArgb(), false);
        if (state.chainMode()) {
            renderChainLimit(controls, graphics, screen, state, layout,
                    mouseX, mouseY, partialTick);
        } else {
            renderControls(controls, graphics, state, layout,
                    mouseX, mouseY, partialTick);
        }
    }

    void renderTooltip(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            BuilderScreen screen,
            QuickBuildUiState state,
            int mouseX,
            int mouseY) {
        for (int i = 0; i < controls.shapeButtonCount() && i < state.shapes.size(); i++) {
            WindowButton button = controls.shapeButton(i);
            if (mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                    && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
                graphics.renderTooltip(screen.font(),
                        Component.translatable(QuickBuildIconCatalog.tooltipKey(
                                state.shapes.get(i).shape)),
                        mouseX, mouseY);
                return;
            }
        }
    }

    private void renderModeToggles(
            GuiGraphics graphics,
            MinecraftUiCanvas canvas,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX,
            int mouseY) {
        renderModeToggle(graphics, canvas, screen, state,
                layout.buildMode, QuickBuildUiMode.BUILD,
                Component.translatable("screen.rtsbuilding.quick_build.mode_build"),
                mouseX, mouseY);
        renderModeToggle(graphics, canvas, screen, state,
                layout.destroyMode, QuickBuildUiMode.DESTROY,
                Component.translatable("screen.rtsbuilding.quick_build.mode_destroy"),
                mouseX, mouseY);
    }

    private void renderModeToggle(
            GuiGraphics graphics,
            MinecraftUiCanvas canvas,
            BuilderScreen screen,
            QuickBuildUiState state,
            UiRect area,
            QuickBuildUiMode mode,
            Component label,
            int mouseX,
            int mouseY) {
        boolean enabled = mode != QuickBuildUiMode.DESTROY || state.destroyEnabled;
        boolean active = state.mode == mode && enabled;
        boolean hovered = area.contains(mouseX, mouseY);
        double strength = this.modeAnimations.value(
                mode, active, Config.isUiAnimationsEnabled());
        QuickBuildStyle.ModeVisual visual =
                QuickBuildStyle.mode(enabled, active, hovered);
        QuickBuildChromeRenderer.renderMode(canvas, area, visual, strength);
        int x = (int) area.getX();
        int y = (int) area.getY();
        int width = (int) area.getWidth();
        int height = (int) area.getHeight();
        int labelX = x + Math.max(
                QuickBuildWindowLayout.MODE_LABEL_MIN_INSET,
                (width - screen.font().width(label)) / 2);
        int labelY = y + (height - screen.font().lineHeight) / 2;
        graphics.drawString(screen.font(), label, labelX, labelY,
                visual.text.toArgb(), false);
    }

    private static void renderShapes(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX,
            int mouseY,
            float partialTick) {
        for (int i = 0; i < controls.shapeButtonCount(); i++) {
            int slotX = layout.shapeX(i);
            int slotY = layout.shapeY(i);
            QuickBuildUiShapeOption option = state.shapes.get(i);
            if (state.mode == QuickBuildUiMode.DESTROY
                    && option.shape == QuickBuildUiShape.CHAIN
                    && state.chainMode()) {
                graphics.fill(
                        slotX, slotY,
                        slotX + QuickBuildWindowLayout.SHAPE_SLOT,
                        slotY + QuickBuildWindowLayout.SHAPE_SLOT,
                        QuickBuildStyle.CHAIN_SELECTED_BORDER.toArgb());
                int inset = QuickBuildWindowLayout.SHAPE_SELECTED_INSET;
                graphics.fill(
                        slotX + inset, slotY + inset,
                        slotX + QuickBuildWindowLayout.SHAPE_SLOT - inset,
                        slotY + QuickBuildWindowLayout.SHAPE_SLOT - inset,
                        QuickBuildStyle.CHAIN_SELECTED_BACKGROUND.toArgb());
            }
            controls.shapeButton(i).render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static void renderChainLimit(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.drawString(screen.font(),
                Component.translatable("screen.rtsbuilding.quick_build.chain_limit_label"),
                layout.rightX, layout.chainLabelY,
                QuickBuildStyle.SECTION_TEXT.toArgb(), false);
        controls.chainLimitSlider().render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(screen.font(), Integer.toString(state.chainLimit),
                layout.chainValueX(controls.chainLimitSlider().getWidth()),
                layout.chainSliderY + QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET,
                QuickBuildStyle.VALUE_TEXT.toArgb(), false);
    }

    private static void renderControls(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX,
            int mouseY,
            float partialTick) {
        List<QuickBuildUiControl> regular = controlsWithoutConnect(state);
        for (int i = 0; i < controls.controlButtonCount(); i++) {
            WindowButton button = controls.controlButton(i);
            QuickBuildUiControl control = regular.get(i);
            button.active = control.enabled;
            button.render(graphics, mouseX, mouseY, partialTick);
            renderControlIndicator(graphics, layout.rightX, layout.controlY(i),
                    control.selected, button.isHoveredOrFocused());
        }
        WindowButton connectButton = controls.connectToggle();
        if (connectButton == null) {
            return;
        }
        QuickBuildUiControl connect = state.control(QuickBuildUiControl.Id.CONNECT);
        boolean selected = connect != null && connect.selected;
        boolean enabled = connect != null && connect.enabled;
        connectButton.active = enabled;
        connectButton.render(graphics, mouseX, mouseY, partialTick);
        renderControlIndicator(
                graphics, layout.rightX, layout.controlY(controls.controlButtonCount()),
                selected, connectButton.isHoveredOrFocused());
    }

    private static void renderConvenienceTools(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX,
            int mouseY,
            float partialTick) {
        for (int i = 0; i < 3; i++) {
            controls.convenienceToolButton(i).render(
                    graphics, mouseX, mouseY, partialTick);
        }

        List<QuickBuildUiConvenienceParameter> parameters =
                QuickBuildControlSurface.activeParameters(state.convenienceTool);
        for (int i = 0; i < parameters.size(); i++) {
            QuickBuildUiConvenienceParameter parameter = parameters.get(i);
            graphics.drawString(screen.font(),
                    Component.translatable("screen.rtsbuilding.quick_build.parameter."
                            + parameter.name().toLowerCase(java.util.Locale.ROOT)),
                    layout.rightX, layout.convenienceParameterLabelY(i),
                    QuickBuildStyle.SECTION_TEXT.toArgb(), false);
            controls.convenienceSlider(parameter).render(
                    graphics, mouseX, mouseY, partialTick);
            String value = Integer.toString(state.convenienceSettings.value(parameter));
            graphics.drawString(screen.font(), value,
                    layout.chainValueX(controls.convenienceSlider(parameter).getWidth()),
                    layout.convenienceParameterSliderY(i)
                            + QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET,
                    QuickBuildStyle.VALUE_TEXT.toArgb(), false);
        }
    }

    private static void renderControlIndicator(
            GuiGraphics graphics,
            int rowX,
            int rowY,
            boolean selected,
            boolean hovered) {
        int vOffset = selected
                ? QuickBuildIconCatalog.MODE_STATE_H * 2
                : (hovered ? QuickBuildIconCatalog.MODE_STATE_H : 0);
        RtsTextureRenderer.drawTextureHighPrecision(
                graphics, QuickBuildIconCatalog.SELECTION_DOT,
                rowX + QuickBuildWindowLayout.CONTROL_ICON_INSET,
                rowY + QuickBuildWindowLayout.CONTROL_ICON_INSET,
                QuickBuildWindowLayout.CONTROL_ICON_SIZE,
                QuickBuildWindowLayout.CONTROL_ICON_SIZE,
                0, vOffset,
                QuickBuildIconCatalog.MODE_SHEET_W,
                QuickBuildIconCatalog.MODE_STATE_H,
                QuickBuildIconCatalog.MODE_SHEET_W,
                QuickBuildIconCatalog.MODE_SHEET_H,
                0, QuickBuildStyle.ICON_TINT.toArgb());
    }

    private static List<QuickBuildUiControl> controlsWithoutConnect(
            QuickBuildUiState state) {
        List<QuickBuildUiControl> result = new ArrayList<>();
        for (QuickBuildUiControl control : state.controls) {
            if (control.id != QuickBuildUiControl.Id.CONNECT) {
                result.add(control);
            }
        }
        return result;
    }
}
