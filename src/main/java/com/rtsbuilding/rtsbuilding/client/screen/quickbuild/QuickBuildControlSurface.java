package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.client.widget.WindowSlider;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiCatalogPage;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShapeOption;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Quick Build 控件表面：只持有 Minecraft 控件、坐标和鼠标会话。
 *
 * <p>它把两种顶栏主模式、目录、两列工具格、参数滑条与形状控件统一投影为
 * {@link QuickBuildUiAction}。业务调用、插件门禁和 Smart Fill/便捷破坏的实际执行仍由
 * {@link QuickBuildUiAdapter} 与 {@link QuickBuildPanel} 保持原有链路。本类不创建第三个
 * 顶栏模式：Build 的 Tools 目录才是 Smart Fill 的唯一 UI 入口。</p>
 */
final class QuickBuildControlSurface {
    private final Consumer<QuickBuildUiAction> dispatch;
    private final QuickBuildControlRenderer renderer = new QuickBuildControlRenderer();

    private WindowButton[] shapeButtons = new WindowButton[0];
    private WindowButton[] controlButtons = new WindowButton[0];
    private WindowButton connectToggle;
    private WindowSlider chainLimitSlider;
    private final WindowButton[] catalogButtons = new WindowButton[2];
    private final WindowButton[] convenienceToolButtons = new WindowButton[3];
    private WindowButton smartFillToolButton;
    private WindowSlider smartFillMaxBlocksSlider;
    private WindowSlider smartFillDiameterSlider;
    private final EnumMap<QuickBuildUiConvenienceParameter, WindowSlider> convenienceSliders =
            new EnumMap<QuickBuildUiConvenienceParameter, WindowSlider>(
                    QuickBuildUiConvenienceParameter.class);
    private String shapeSignature = "";
    private String controlSignature = "";
    private boolean syncingChainLimit;
    private boolean syncingConvenience;
    private boolean syncingSmartFill;
    private QuickBuildUiMode pressedMode;

    QuickBuildControlSurface(Consumer<QuickBuildUiAction> dispatch) {
        if (dispatch == null) {
            throw new IllegalArgumentException("dispatch");
        }
        this.dispatch = dispatch;
        createCatalogAndToolControls();
    }

    void refreshAll(QuickBuildUiState state) {
        refreshShapeButtons(state);
        refreshControlButtons(state);
        ensureChainLimitSlider(state);
        ensureConvenienceSliders(state);
        ensureSmartFillSliders(state);
    }

    void refreshShapeButtons(QuickBuildUiState state) {
        this.shapeSignature = shapeSignature(state);
        this.shapeButtons = new WindowButton[state.shapes.size()];
        for (int i = 0; i < this.shapeButtons.length; i++) {
            QuickBuildUiShapeOption option = state.shapes.get(i);
            int normalV = option.selected ? QuickBuildIconCatalog.SHAPE_STATE_H : 0;
            WindowButton button = new WindowButton(0, 0,
                    QuickBuildWindowLayout.SHAPE_SLOT, QuickBuildWindowLayout.SHAPE_SLOT,
                    new TextComponentString(""), QuickBuildIconCatalog.shapeTexture(option.shape),
                    0, normalV, QuickBuildIconCatalog.SHAPE_SHEET_W,
                    QuickBuildIconCatalog.SHAPE_STATE_H, QuickBuildIconCatalog.SHAPE_STATE_H,
                    QuickBuildIconCatalog.SHAPE_STATE_H, QuickBuildIconCatalog.SHAPE_SHEET_W,
                    QuickBuildIconCatalog.SHAPE_SHEET_H,
                    ignored -> this.dispatch.accept(QuickBuildUiAction.shape(option.shape)));
            button.enabled = option.enabled;
            button.setVisualRole(UiControlRole.CHOICE);
            button.setSelectedVisual(option.selected);
            this.shapeButtons[i] = button;
        }
    }

    void refreshControlButtons(QuickBuildUiState state) {
        this.controlSignature = controlSignature(state);
        List<WindowButton> regular = new ArrayList<WindowButton>();
        this.connectToggle = null;
        for (QuickBuildUiControl control : state.controls) {
            WindowButton button = new WindowButton(0, 0,
                    QuickBuildWindowLayout.CONTROL_W, QuickBuildWindowLayout.CONTROL_H,
                    new TextComponentString(control.label),
                    ignored -> this.dispatch.accept(QuickBuildUiAction.control(control.id)));
            button.enabled = control.enabled;
            button.setVisualRole(UiControlRole.TOGGLE);
            button.setSelectedVisual(control.selected);
            if (control.id == QuickBuildUiControl.Id.CONNECT) {
                this.connectToggle = button;
            } else {
                regular.add(button);
            }
        }
        this.controlButtons = regular.toArray(new WindowButton[regular.size()]);
    }

    void syncChainLimit(int value) {
        if (this.chainLimitSlider == null) return;
        this.syncingChainLimit = true;
        try {
            this.chainLimitSlider.setValue(value);
        } finally {
            this.syncingChainLimit = false;
        }
    }

    void syncConvenienceSettings(QuickBuildUiConvenienceSettings settings) {
        if (settings == null || this.convenienceSliders.isEmpty()) return;
        this.syncingConvenience = true;
        try {
            for (QuickBuildUiConvenienceParameter parameter : QuickBuildUiConvenienceParameter.values()) {
                WindowSlider slider = this.convenienceSliders.get(parameter);
                if (slider != null) slider.setValue(settings.value(parameter));
            }
        } finally {
            this.syncingConvenience = false;
        }
    }

    void syncSmartFill(int maxBlocks, int diameter) {
        if (this.smartFillMaxBlocksSlider == null || this.smartFillDiameterSlider == null) return;
        this.syncingSmartFill = true;
        try {
            this.smartFillMaxBlocksSlider.setValue(maxBlocks);
            this.smartFillDiameterSlider.setValue(diameter);
        } finally {
            this.syncingSmartFill = false;
        }
    }

    void render(LegacyGuiGraphics graphics, MinecraftUiCanvas canvas, BuilderScreen screen,
            QuickBuildUiState state, QuickBuildWindowLayout.Geometry layout, int windowWidth,
            int mouseX, int mouseY, float partialTick) {
        prepare(state, layout, windowWidth);
        this.renderer.render(this, graphics, canvas, screen, state, layout, mouseX, mouseY, partialTick);
    }

    void renderTooltip(LegacyGuiGraphics graphics, BuilderScreen screen, QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout, int windowWidth, int mouseX, int mouseY) {
        prepare(state, layout, windowWidth);
        this.renderer.renderTooltip(this, graphics, screen, state, mouseX, mouseY);
    }

    boolean mouseClicked(QuickBuildUiState state, QuickBuildWindowLayout.Geometry layout,
            int windowWidth, double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        prepare(state, layout, windowWidth);
        QuickBuildUiMode mode = layout.modeAt(mouseX, mouseY);
        if (mode != null) {
            boolean enabled = mode != QuickBuildUiMode.DESTROY || state.destroyEnabled;
            this.pressedMode = enabled ? mode : null;
            if (mode == QuickBuildUiMode.BUILD && state.mode == QuickBuildUiMode.SMART_FILL) {
                return true;
            }
            if (enabled) this.dispatch.accept(QuickBuildUiAction.mode(mode));
            return true;
        }
        if (click(this.catalogButtons, mouseX, mouseY, button)) return true;
        if (state.convenienceMode()) {
            if (click(this.convenienceToolButtons, mouseX, mouseY, button)) return true;
            for (QuickBuildUiConvenienceParameter parameter : activeParameters(state.convenienceTool)) {
                WindowSlider slider = this.convenienceSliders.get(parameter);
                if (slider != null && slider.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return false;
        }
        if (state.chainMode() && this.chainLimitSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (state.mode == QuickBuildUiMode.SMART_FILL) {
            return this.smartFillToolButton.mouseClicked(mouseX, mouseY, button)
                    || this.smartFillMaxBlocksSlider.mouseClicked(mouseX, mouseY, button)
                    || this.smartFillDiameterSlider.mouseClicked(mouseX, mouseY, button);
        }
        if (click(this.shapeButtons, mouseX, mouseY, button)
                || click(this.controlButtons, mouseX, mouseY, button)) return true;
        return this.connectToggle != null && this.connectToggle.mouseClicked(mouseX, mouseY, button);
    }

    boolean mouseDragged(QuickBuildUiState state, QuickBuildWindowLayout.Geometry layout,
            int windowWidth, double mouseX, double mouseY, int button) {
        prepare(state, layout, windowWidth);
        if (state.convenienceMode()) {
            for (QuickBuildUiConvenienceParameter parameter : activeParameters(state.convenienceTool)) {
                WindowSlider slider = this.convenienceSliders.get(parameter);
                if (slider != null && slider.mouseDragged(mouseX, mouseY, button)) return true;
            }
            return false;
        }
        if (state.mode == QuickBuildUiMode.SMART_FILL) {
            return this.smartFillMaxBlocksSlider.mouseDragged(mouseX, mouseY, button)
                    || this.smartFillDiameterSlider.mouseDragged(mouseX, mouseY, button);
        }
        return state.chainMode() && this.chainLimitSlider.mouseDragged(mouseX, mouseY, button);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = button == 0 && this.pressedMode != null;
        if (button == 0) this.pressedMode = null;
        handled |= release(this.catalogButtons, mouseX, mouseY, button);
        handled |= release(this.convenienceToolButtons, mouseX, mouseY, button);
        handled |= this.smartFillToolButton != null && this.smartFillToolButton.mouseReleased(mouseX, mouseY, button);
        handled |= release(this.shapeButtons, mouseX, mouseY, button);
        handled |= release(this.controlButtons, mouseX, mouseY, button);
        if (this.connectToggle != null) handled |= this.connectToggle.mouseReleased(mouseX, mouseY, button);
        handled |= this.chainLimitSlider != null && this.chainLimitSlider.mouseReleased(mouseX, mouseY, button);
        handled |= this.smartFillMaxBlocksSlider != null
                && this.smartFillMaxBlocksSlider.mouseReleased(mouseX, mouseY, button);
        handled |= this.smartFillDiameterSlider != null
                && this.smartFillDiameterSlider.mouseReleased(mouseX, mouseY, button);
        for (WindowSlider slider : this.convenienceSliders.values()) {
            handled |= slider.mouseReleased(mouseX, mouseY, button);
        }
        return handled;
    }

    int shapeButtonCount() { return this.shapeButtons.length; }
    WindowButton shapeButton(int index) { return this.shapeButtons[index]; }
    int controlButtonCount() { return this.controlButtons.length; }
    WindowButton controlButton(int index) { return this.controlButtons[index]; }
    WindowButton connectToggle() { return this.connectToggle; }
    WindowSlider chainLimitSlider() { return this.chainLimitSlider; }
    WindowButton smartFillToolButton() { return this.smartFillToolButton; }
    WindowSlider smartFillMaxBlocksSlider() { return this.smartFillMaxBlocksSlider; }
    WindowSlider smartFillDiameterSlider() { return this.smartFillDiameterSlider; }
    WindowButton catalogButton(int index) { return this.catalogButtons[index]; }
    WindowButton convenienceToolButton(int index) { return this.convenienceToolButtons[index]; }
    int convenienceToolButtonCount() { return this.convenienceToolButtons.length; }
    WindowSlider convenienceSlider(QuickBuildUiConvenienceParameter parameter) {
        return this.convenienceSliders.get(parameter);
    }
    QuickBuildUiMode pressedMode() { return this.pressedMode; }

    private void prepare(QuickBuildUiState state, QuickBuildWindowLayout.Geometry layout, int windowWidth) {
        if (!this.shapeSignature.equals(shapeSignature(state))) refreshShapeButtons(state);
        if (!this.controlSignature.equals(controlSignature(state))) refreshControlButtons(state);
        ensureChainLimitSlider(state);
        ensureConvenienceSliders(state);
        ensureSmartFillSliders(state);
        this.chainLimitSlider.setVisible(state.chainMode() && !state.convenienceMode());
        this.smartFillMaxBlocksSlider.setVisible(state.mode == QuickBuildUiMode.SMART_FILL);
        this.smartFillDiameterSlider.setVisible(state.mode == QuickBuildUiMode.SMART_FILL);
        syncChainLimit(state.chainLimit);
        syncConvenienceSettings(state.convenienceSettings);
        syncSmartFill(state.smartFillMaxBlocks, state.smartFillDiameter);
        for (int i = 0; i < this.catalogButtons.length; i++) {
            this.catalogButtons[i].enabled = true;
            this.catalogButtons[i].setSelectedVisual(state.catalogPage == QuickBuildUiCatalogPage.values()[i]);
        }
        this.smartFillToolButton.enabled = state.mode == QuickBuildUiMode.SMART_FILL;
        this.smartFillToolButton.setSelectedVisual(state.mode == QuickBuildUiMode.SMART_FILL);
        for (int i = 0; i < this.convenienceToolButtons.length; i++) {
            this.convenienceToolButtons[i].enabled = state.convenienceMode();
            this.convenienceToolButtons[i].setSelectedVisual(
                    state.convenienceTool == QuickBuildUiConvenienceTool.values()[i]);
        }
        for (QuickBuildUiConvenienceParameter parameter : QuickBuildUiConvenienceParameter.values()) {
            WindowSlider slider = this.convenienceSliders.get(parameter);
            slider.setVisible(state.convenienceMode() && activeParameters(state.convenienceTool).contains(parameter));
        }
        position(state, layout, windowWidth);
    }

    private void position(QuickBuildUiState state, QuickBuildWindowLayout.Geometry layout, int windowWidth) {
        for (int i = 0; i < this.shapeButtons.length; i++) {
            this.shapeButtons[i].setX(layout.shapeX(i));
            this.shapeButtons[i].setY(layout.shapeY(i));
            if (i < state.shapes.size()) {
                this.shapeButtons[i].enabled = state.shapes.get(i).enabled;
                this.shapeButtons[i].setSelectedVisual(state.shapes.get(i).selected);
            }
        }
        for (int i = 0; i < this.catalogButtons.length; i++) {
            this.catalogButtons[i].setX(layout.catalogX(i));
            this.catalogButtons[i].setY(layout.catalogY);
            this.catalogButtons[i].setWidth(layout.catalogW);
        }
        for (int i = 0; i < this.convenienceToolButtons.length; i++) {
            this.convenienceToolButtons[i].setX(layout.convenienceToolX(i));
            this.convenienceToolButtons[i].setY(layout.convenienceToolY(i));
        }
        this.smartFillToolButton.setX(layout.convenienceToolX(0));
        this.smartFillToolButton.setY(layout.convenienceToolY(0));
        int sliderWidth = QuickBuildWindowLayout.chainSliderWidth(windowWidth);
        int index = 0;
        for (QuickBuildUiConvenienceParameter parameter : activeParameters(state.convenienceTool)) {
            WindowSlider slider = this.convenienceSliders.get(parameter);
            slider.setWidth(sliderWidth);
            slider.setX(layout.rightX);
            slider.setY(layout.convenienceParameterSliderY(index++));
        }
        List<QuickBuildUiControl> regular = controlsWithoutConnect(state);
        for (int i = 0; i < this.controlButtons.length; i++) {
            this.controlButtons[i].setX(layout.rightX);
            this.controlButtons[i].setY(layout.controlY(i));
            if (i < regular.size()) this.controlButtons[i].setSelectedVisual(regular.get(i).selected);
        }
        if (this.connectToggle != null) {
            this.connectToggle.setX(layout.rightX);
            this.connectToggle.setY(layout.controlY(this.controlButtons.length));
            QuickBuildUiControl connect = state.control(QuickBuildUiControl.Id.CONNECT);
            this.connectToggle.setSelectedVisual(connect != null && connect.selected);
        }
        this.chainLimitSlider.setWidth(sliderWidth);
        this.chainLimitSlider.setX(layout.rightX);
        this.chainLimitSlider.setY(layout.chainSliderY);
        this.smartFillMaxBlocksSlider.setWidth(sliderWidth);
        this.smartFillMaxBlocksSlider.setX(layout.rightX);
        this.smartFillMaxBlocksSlider.setY(layout.smartFillParameterSliderY(0));
        this.smartFillDiameterSlider.setWidth(sliderWidth);
        this.smartFillDiameterSlider.setX(layout.rightX);
        this.smartFillDiameterSlider.setY(layout.smartFillParameterSliderY(1));
    }

    private void ensureChainLimitSlider(QuickBuildUiState state) {
        if (this.chainLimitSlider == null) {
            this.chainLimitSlider = new WindowSlider(0, 0,
                    QuickBuildWindowLayout.chainSliderWidth(QuickBuildWindowLayout.WINDOW_W),
                    QuickBuildWindowLayout.CHAIN_SLIDER_H,
                    state.chainMinimum, state.chainMaximum, state.chainLimit);
            this.chainLimitSlider.onChange(value -> {
                if (!this.syncingChainLimit) this.dispatch.accept(QuickBuildUiAction.limit(value));
            });
            return;
        }
        this.syncingChainLimit = true;
        try {
            this.chainLimitSlider.setRange(state.chainMinimum, state.chainMaximum);
        } finally {
            this.syncingChainLimit = false;
        }
    }

    private void ensureSmartFillSliders(QuickBuildUiState state) {
        if (this.smartFillMaxBlocksSlider == null) {
            this.smartFillMaxBlocksSlider = new WindowSlider(0, 0,
                    QuickBuildWindowLayout.chainSliderWidth(QuickBuildWindowLayout.WINDOW_W),
                    QuickBuildWindowLayout.CHAIN_SLIDER_H, state.smartFillMinBlocks,
                    state.smartFillMaxBlocksLimit, state.smartFillMaxBlocks);
            this.smartFillMaxBlocksSlider.onChange(value -> {
                if (!this.syncingSmartFill) this.dispatch.accept(QuickBuildUiAction.smartFillMaxBlocks(value));
            });
            this.smartFillDiameterSlider = new WindowSlider(0, 0,
                    QuickBuildWindowLayout.chainSliderWidth(QuickBuildWindowLayout.WINDOW_W),
                    QuickBuildWindowLayout.CHAIN_SLIDER_H, state.smartFillMinDiameter,
                    state.smartFillMaxDiameter, state.smartFillDiameter);
            this.smartFillDiameterSlider.onChange(value -> {
                if (!this.syncingSmartFill) this.dispatch.accept(QuickBuildUiAction.smartFillDiameter(value));
            });
            return;
        }
        this.syncingSmartFill = true;
        try {
            this.smartFillMaxBlocksSlider.setRange(state.smartFillMinBlocks, state.smartFillMaxBlocksLimit);
            this.smartFillDiameterSlider.setRange(state.smartFillMinDiameter, state.smartFillMaxDiameter);
        } finally {
            this.syncingSmartFill = false;
        }
    }

    private void createCatalogAndToolControls() {
        for (int i = 0; i < this.catalogButtons.length; i++) {
            QuickBuildUiCatalogPage page = QuickBuildUiCatalogPage.values()[i];
            String key = page == QuickBuildUiCatalogPage.SHAPES
                    ? "screen.rtsbuilding.quick_build.catalog_shapes"
                    : "screen.rtsbuilding.quick_build.catalog_tools";
            this.catalogButtons[i] = new WindowButton(0, 0, 80, QuickBuildWindowLayout.CATALOG_H,
                    new TextComponentString(I18n.format(key)),
                    ignored -> this.dispatch.accept(QuickBuildUiAction.catalog(page)));
            this.catalogButtons[i].setVisualRole(UiControlRole.CHOICE);
        }
        this.smartFillToolButton = new WindowButton(0, 0,
                QuickBuildWindowLayout.CONVENIENCE_TOOL_W, QuickBuildWindowLayout.CONVENIENCE_TOOL_H,
                new TextComponentString(I18n.format("screen.rtsbuilding.quick_build.mode_smart_fill_short")),
                ignored -> this.dispatch.accept(QuickBuildUiAction.catalog(
                        QuickBuildUiCatalogPage.CONVENIENCE_TOOLS)));
        this.smartFillToolButton.setVisualRole(UiControlRole.CHOICE);
        for (int i = 0; i < this.convenienceToolButtons.length; i++) {
            final QuickBuildUiConvenienceTool tool = QuickBuildUiConvenienceTool.values()[i];
            String key = tool == QuickBuildUiConvenienceTool.REPEAT_BOX
                    ? "screen.rtsbuilding.quick_build.convenience.repeat_short"
                    : tool == QuickBuildUiConvenienceTool.CHUNK_QUARRY
                    ? "screen.rtsbuilding.quick_build.convenience.chunk_short"
                    : "screen.rtsbuilding.quick_build.convenience.tree_short";
            this.convenienceToolButtons[i] = new WindowButton(0, 0,
                    QuickBuildWindowLayout.CONVENIENCE_TOOL_W, QuickBuildWindowLayout.CONVENIENCE_TOOL_H,
                    new TextComponentString(I18n.format(key)),
                    ignored -> this.dispatch.accept(QuickBuildUiAction.convenienceTool(tool)));
            this.convenienceToolButtons[i].setVisualRole(UiControlRole.CHOICE);
        }
    }

    private void ensureConvenienceSliders(QuickBuildUiState state) {
        if (!this.convenienceSliders.isEmpty()) return;
        for (QuickBuildUiConvenienceParameter parameter : QuickBuildUiConvenienceParameter.values()) {
            int[] range = parameterRange(parameter);
            WindowSlider slider = new WindowSlider(0, 0,
                    QuickBuildWindowLayout.chainSliderWidth(QuickBuildWindowLayout.WINDOW_W),
                    QuickBuildWindowLayout.CHAIN_SLIDER_H, range[0], range[1],
                    state.convenienceSettings.value(parameter));
            slider.onChange(value -> {
                if (!this.syncingConvenience) {
                    this.dispatch.accept(QuickBuildUiAction.convenienceParameter(parameter, value));
                }
            });
            this.convenienceSliders.put(parameter, slider);
        }
    }

    static List<QuickBuildUiConvenienceParameter> activeParameters(QuickBuildUiConvenienceTool tool) {
        List<QuickBuildUiConvenienceParameter> values = new ArrayList<QuickBuildUiConvenienceParameter>();
        QuickBuildUiConvenienceTool active = tool == null
                ? QuickBuildUiConvenienceTool.REPEAT_BOX : tool;
        switch (active) {
            case CHUNK_QUARRY:
                values.add(QuickBuildUiConvenienceParameter.CHUNK_UP);
                values.add(QuickBuildUiConvenienceParameter.CHUNK_DOWN);
                break;
            case TREE_FELL:
                values.add(QuickBuildUiConvenienceParameter.TREE_MAX_BLOCKS);
                break;
            case REPEAT_BOX:
            default:
                values.add(QuickBuildUiConvenienceParameter.SIZE_X);
                values.add(QuickBuildUiConvenienceParameter.SIZE_Y);
                values.add(QuickBuildUiConvenienceParameter.SIZE_Z);
                break;
        }
        return values;
    }

    private static int[] parameterRange(QuickBuildUiConvenienceParameter parameter) {
        switch (parameter) {
            case SIZE_X:
            case SIZE_Z:
                return new int[] {QuickBuildUiConvenienceSettings.BOX_MIN, QuickBuildUiConvenienceSettings.BOX_MAX};
            case SIZE_Y:
                return new int[] {QuickBuildUiConvenienceSettings.BOX_MIN, QuickBuildUiConvenienceSettings.HEIGHT_MAX};
            case CHUNK_UP:
            case CHUNK_DOWN:
                return new int[] {0, QuickBuildUiConvenienceSettings.HEIGHT_MAX};
            case TREE_MAX_BLOCKS:
            default:
                return new int[] {QuickBuildUiConvenienceSettings.TREE_MIN, QuickBuildUiConvenienceSettings.TREE_MAX};
        }
    }

    private static boolean click(WindowButton[] buttons, double mouseX, double mouseY, int button) {
        for (WindowButton buttonControl : buttons) {
            if (buttonControl != null && buttonControl.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    private static boolean release(WindowButton[] buttons, double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (WindowButton buttonControl : buttons) {
            if (buttonControl != null) handled |= buttonControl.mouseReleased(mouseX, mouseY, button);
        }
        return handled;
    }

    private static List<QuickBuildUiControl> controlsWithoutConnect(QuickBuildUiState state) {
        List<QuickBuildUiControl> values = new ArrayList<QuickBuildUiControl>();
        for (QuickBuildUiControl control : state.controls) {
            if (control.id != QuickBuildUiControl.Id.CONNECT) values.add(control);
        }
        return values;
    }

    private static String shapeSignature(QuickBuildUiState state) {
        StringBuilder result = new StringBuilder(state.mode.name());
        for (QuickBuildUiShapeOption option : state.shapes) {
            result.append('|').append(option.shape.name()).append(':').append(option.selected)
                    .append(':').append(option.enabled);
        }
        return result.toString();
    }

    private static String controlSignature(QuickBuildUiState state) {
        StringBuilder result = new StringBuilder();
        for (QuickBuildUiControl control : state.controls) {
            result.append('|').append(control.id.name()).append(':').append(control.label)
                    .append(':').append(control.enabled);
        }
        return result.toString();
    }
}
