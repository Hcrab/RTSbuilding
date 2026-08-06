package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShapeOption;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.QuickBuildChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Quick Build 控件表面的 Minecraft 绘制适配器。
 *
 * <p>负责模式动画、形状/填充/连接控件、连锁滑杆文字和 Tooltip。控件实例、位置和输入
 * 捕获仍归 {@link QuickBuildControlSurface}；业务状态只读自 Core 快照。本类不创建控件、
 * 不提交 action，也不执行任何形状或世界副作用。</p>
 */
final class QuickBuildControlRenderer {
    private final EnumMap<QuickBuildUiMode, UiControlAnimationState> modeAnimations =
            new EnumMap<>(QuickBuildUiMode.class);
    /**
     * 右栏开关的状态块与行按钮是两个独立视觉层，因此必须各自持有动画状态。
     * 这里仅插值颜色或 Legacy 帧权重，不参与按钮尺寸、坐标或玩法状态计算。
     */
    private final EnumMap<QuickBuildUiControl.Id, UiControlAnimationState> controlAnimations =
            new EnumMap<>(QuickBuildUiControl.Id.class);

    QuickBuildControlRenderer() {
        for (QuickBuildUiMode mode : QuickBuildUiMode.values()) {
            this.modeAnimations.put(mode,
                    new UiControlAnimationState(SystemUiClock.INSTANCE));
        }
        for (QuickBuildUiControl.Id id : QuickBuildUiControl.Id.values()) {
            this.controlAnimations.put(id,
                    new UiControlAnimationState(SystemUiClock.INSTANCE));
        }
    }

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
        renderModeToggles(controls, graphics, canvas, screen, state, layout, mouseX, mouseY);

        for (int i = 0; i < 2; i++) {
            controls.catalogButton(i).render(graphics, mouseX, mouseY, partialTick);
        }
        if (state.mode == QuickBuildUiMode.SMART_FILL) {
            renderSmartFillControls(
                    controls, graphics, screen, state, layout,
                    mouseX, mouseY, partialTick);
            return;
        }

        if (state.convenienceMode()) {
            renderConvenienceTools(
                    controls, graphics, screen, state, layout,
                    mouseX, mouseY, partialTick);
            return;
        }

        renderShapes(controls, graphics, state, layout, mouseX, mouseY, partialTick);

        if (state.chainMode()) {
            renderChainLimit(controls, graphics, screen, state, layout,
                    mouseX, mouseY, partialTick);
        } else {
            renderControls(controls, graphics, canvas, state, layout,
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
        if (state.mode == QuickBuildUiMode.SMART_FILL) {
            WindowButton button = controls.smartFillToolButton();
            if (isHovered(button, mouseX, mouseY)) {
                QuickBuildHoverTooltipRenderer.render(
                        graphics, screen,
                        Component.translatable("screen.rtsbuilding.quick_build.mode_smart_fill"),
                        Component.translatable("screen.rtsbuilding.quick_build.smart_fill.detail"),
                        mouseX, mouseY);
            }
            return;
        }
        if (state.convenienceMode()) {
            for (int i = 0; i < controls.convenienceToolButtonCount(); i++) {
                WindowButton button = controls.convenienceToolButton(i);
                if (!isHovered(button, mouseX, mouseY)) continue;
                QuickBuildUiConvenienceTool tool = QuickBuildUiConvenienceTool.values()[i];
                String key = "screen.rtsbuilding.quick_build.tool."
                        + tool.name().toLowerCase(java.util.Locale.ROOT);
                QuickBuildHoverTooltipRenderer.render(
                        graphics, screen,
                        Component.translatable(key),
                        Component.translatable(key + ".detail"),
                        mouseX, mouseY);
                return;
            }
            return;
        }
        for (int i = 0; i < controls.shapeButtonCount() && i < state.shapes.size(); i++) {
            WindowButton button = controls.shapeButton(i);
            if (isHovered(button, mouseX, mouseY)) {
                String key = QuickBuildIconCatalog.tooltipKey(state.shapes.get(i).shape);
                QuickBuildHoverTooltipRenderer.render(
                        graphics, screen,
                        Component.translatable(key),
                        Component.translatable(key + ".detail"),
                        mouseX, mouseY);
                return;
            }
        }
    }

    private static boolean isHovered(WindowButton button, int mouseX, int mouseY) {
        return button != null
                && mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    private void renderModeToggles(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            MinecraftUiCanvas canvas,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX,
            int mouseY) {
        renderModeToggle(controls, graphics, canvas, screen, state,
                layout.buildMode, QuickBuildUiMode.BUILD,
                Component.translatable("screen.rtsbuilding.quick_build.mode_build"),
                mouseX, mouseY);
        renderModeToggle(controls, graphics, canvas, screen, state,
                layout.destroyMode, QuickBuildUiMode.DESTROY,
                Component.translatable("screen.rtsbuilding.quick_build.mode_destroy"),
                mouseX, mouseY);
    }

    private void renderModeToggle(
            QuickBuildControlSurface controls,
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
        boolean active = (mode == QuickBuildUiMode.BUILD
                ? state.mode != QuickBuildUiMode.DESTROY
                : state.mode == mode) && enabled;
        boolean hovered = area.contains(mouseX, mouseY);
        boolean pressed = hovered && controls.pressedMode() == mode;
        UiControlState controlState = new UiControlState(
                true, enabled, enabled && hovered, false, enabled && pressed,
                active, false, false, enabled ? "" : "disabled");
        UiControlAnimationState.Snapshot animation =
                this.modeAnimations.get(mode).update(
                        controlState, Config.isUiAnimationsEnabled());
        QuickBuildStyle.ModeVisual visual = QuickBuildStyle.animatedMode(animation);
        QuickBuildChromeRenderer.renderMode(canvas, area, visual, 0.0D);
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

    private static void renderSmartFillControls(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int mouseX,
            int mouseY,
            float partialTick) {
        controls.smartFillToolButton().render(
                graphics, mouseX, mouseY, partialTick);
        renderToolIdentity(graphics,
                QuickBuildIconCatalog.smartFillTexture(UiTextureState.ACTIVE),
                layout.convenienceToolX(0), layout.convenienceToolY(0));
        int sliderWidth = controls.smartFillMaxBlocksSlider().getWidth();
        graphics.drawString(
                screen.font(),
                Component.translatable("screen.rtsbuilding.quick_build.smart_fill.max_blocks"),
                layout.rightX,
                layout.smartFillParameterLabelY(0),
                QuickBuildStyle.SECTION_TEXT.toArgb(),
                false);
        controls.smartFillMaxBlocksSlider().render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(
                screen.font(),
                Integer.toString(state.smartFillMaxBlocks),
                layout.chainValueX(sliderWidth),
                layout.smartFillParameterSliderY(0) + QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET,
                QuickBuildStyle.VALUE_TEXT.toArgb(),
                false);

        graphics.drawString(
                screen.font(),
                Component.translatable("screen.rtsbuilding.quick_build.smart_fill.diameter"),
                layout.rightX,
                layout.smartFillParameterLabelY(1),
                QuickBuildStyle.SECTION_TEXT.toArgb(),
                false);
        controls.smartFillDiameterSlider().render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(
                screen.font(),
                Integer.toString(state.smartFillDiameter),
                layout.chainValueX(controls.smartFillDiameterSlider().getWidth()),
                layout.smartFillParameterSliderY(1) + QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET,
                QuickBuildStyle.VALUE_TEXT.toArgb(),
                false);
    }

    private void renderControls(
            QuickBuildControlSurface controls,
            GuiGraphics graphics,
            MinecraftUiCanvas canvas,
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
            renderControlIndicator(graphics, canvas, control.id,
                    layout.rightX, layout.controlY(i), control.enabled,
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
                graphics, canvas, QuickBuildUiControl.Id.CONNECT,
                layout.rightX, layout.controlY(controls.controlButtonCount()),
                enabled, selected, connectButton.isHoveredOrFocused());
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
            WindowButton button = controls.convenienceToolButton(i);
            button.render(
                    graphics, mouseX, mouseY, partialTick);
            QuickBuildUiConvenienceTool tool = QuickBuildUiConvenienceTool.values()[i];
            UiTextureState iconState = state.convenienceTool == tool
                    ? UiTextureState.ACTIVE
                    : button.isHoveredOrFocused() ? UiTextureState.HOVER : UiTextureState.INACTIVE;
            renderToolIdentity(graphics,
                    QuickBuildIconCatalog.convenienceTexture(tool, iconState),
                    layout.convenienceToolX(i), layout.convenienceToolY(i));
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

    /** Tools 与 Shapes 共用大图标按钮，名称和完整说明只在悬停层显示。 */
    private static void renderToolIdentity(GuiGraphics graphics,
                                           net.minecraft.resources.ResourceLocation texture,
                                           int x, int y) {
        int iconSize = QuickBuildWindowLayout.CONVENIENCE_TOOL_ICON_SIZE;
        int iconX = x + QuickBuildWindowLayout.CONVENIENCE_TOOL_ICON_X;
        int iconY = y + (QuickBuildWindowLayout.CONVENIENCE_TOOL_H - iconSize) / 2;
        RtsTextureRenderer.drawTextureHighPrecision(
                graphics, texture,
                iconX, iconY, iconSize, iconSize,
                0, 0,
                QuickBuildIconCatalog.PR133_ICON_SIZE,
                QuickBuildIconCatalog.PR133_ICON_SIZE,
                QuickBuildIconCatalog.PR133_ICON_SIZE,
                QuickBuildIconCatalog.PR133_ICON_SIZE,
                0, RtsTextureRenderer.NO_TINT);
    }

    private void renderControlIndicator(
            GuiGraphics graphics,
            MinecraftUiCanvas canvas,
            QuickBuildUiControl.Id id,
            int rowX,
            int rowY,
            boolean enabled,
            boolean selected,
            boolean hovered) {
        int iconX = rowX + QuickBuildWindowLayout.CONTROL_ICON_INSET;
        int iconY = rowY + QuickBuildWindowLayout.CONTROL_ICON_INSET;
        UiControlAnimationState.Snapshot animation = this.controlAnimations.get(id).update(
                new UiControlState(
                        true, enabled, enabled && hovered, false, false,
                        selected, false, false, enabled ? "" : "disabled"),
                Config.isUiAnimationsEnabled());
        double selectedWeight = animation.selection();
        double hoverWeight = (1.0D - selectedWeight) * animation.hover();
        double idleWeight = Math.max(0.0D, 1.0D - selectedWeight - hoverWeight);
        renderIndicatorFrame(graphics, iconX, iconY,
                UiTextureState.INACTIVE, 0, idleWeight);
        renderIndicatorFrame(graphics, iconX, iconY,
                UiTextureState.HOVER, QuickBuildIconCatalog.MODE_STATE_H, hoverWeight);
        renderIndicatorFrame(graphics, iconX, iconY,
                UiTextureState.ACTIVE, QuickBuildIconCatalog.MODE_STATE_H * 2, selectedWeight);
    }

    /** Legacy 三帧贴图只做交叉淡化；源纹理尺寸与屏幕命中矩形始终保持不变。 */
    private static void renderIndicatorFrame(
            GuiGraphics graphics,
            int iconX,
            int iconY,
            UiTextureState state,
            int vOffset,
            double weight) {
        if (weight <= 0.001D) {
            return;
        }
        int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D, weight)) * 255.0D);
        int tint = alpha << 24 | RtsTextureRenderer.NO_TINT >>> Byte.SIZE;
        RtsTextureRenderer.drawTextureHighPrecision(
                graphics, QuickBuildIconCatalog.controlIndicatorTexture(state),
                iconX, iconY,
                QuickBuildWindowLayout.CONTROL_ICON_SIZE,
                QuickBuildWindowLayout.CONTROL_ICON_SIZE,
                0, vOffset,
                QuickBuildIconCatalog.MODE_SHEET_W,
                QuickBuildIconCatalog.MODE_STATE_H,
                QuickBuildIconCatalog.MODE_SHEET_W,
                QuickBuildIconCatalog.MODE_SHEET_H,
                0, tint);
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
