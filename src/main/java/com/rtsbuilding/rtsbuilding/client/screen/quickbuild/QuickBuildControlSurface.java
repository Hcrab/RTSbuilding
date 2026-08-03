package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.client.widget.WindowSlider;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShapeOption;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Quick Build 窗口中模式、形状、填充和连锁限制的生产控件表面。
 *
 * <p>本类只拥有 Minecraft 控件实例、控件定位以及鼠标 press/drag/release。
 * 每次绘制或输入前都先按 {@link QuickBuildWindowLayout.Geometry} 定位，因此输入不依赖
 * “本帧是否已经绘制过”。它只把玩家意图提交为 Core action，不拥有模式持久化、插件
 * 解锁判断、正式图标映射、绘制、形状控制器或世界副作用；这些分别由目录、renderer、
 * {@link QuickBuildPanel} 和平台适配器负责。</p>
 */
final class QuickBuildControlSurface {
    private final Consumer<QuickBuildUiAction> dispatch;
    private final QuickBuildControlRenderer renderer = new QuickBuildControlRenderer();

    private WindowButton[] shapeButtons = new WindowButton[0];
    private WindowButton[] controlButtons = new WindowButton[0];
    private WindowButton connectToggle;
    private WindowSlider chainLimitSlider;
    private String shapeSignature = "";
    private String controlSignature = "";
    private boolean syncingChainLimit;

    QuickBuildControlSurface(Consumer<QuickBuildUiAction> dispatch) {
        if (dispatch == null) {
            throw new IllegalArgumentException("dispatch");
        }
        this.dispatch = dispatch;
    }

    void refreshAll(QuickBuildUiState state) {
        refreshShapeButtons(state);
        refreshControlButtons(state);
        ensureChainLimitSlider(state);
    }

    void refreshShapeButtons(QuickBuildUiState state) {
        this.shapeSignature = shapeSignature(state);
        this.shapeButtons = new WindowButton[state.shapes.size()];
        for (int i = 0; i < this.shapeButtons.length; i++) {
            QuickBuildUiShapeOption option = state.shapes.get(i);
            int normalV = option.selected ? QuickBuildIconCatalog.SHAPE_STATE_H : 0;
            WindowButton button = new WindowButton(
                    0, 0,
                    QuickBuildWindowLayout.SHAPE_SLOT,
                    QuickBuildWindowLayout.SHAPE_SLOT,
                    Component.empty(),
                    QuickBuildIconCatalog.shapeTexture(option.shape),
                    0, normalV,
                    QuickBuildIconCatalog.SHAPE_SHEET_W,
                    QuickBuildIconCatalog.SHAPE_STATE_H,
                    QuickBuildIconCatalog.SHAPE_STATE_H,
                    QuickBuildIconCatalog.SHAPE_STATE_H,
                    QuickBuildIconCatalog.SHAPE_SHEET_W,
                    QuickBuildIconCatalog.SHAPE_SHEET_H,
                    ignored -> this.dispatch.accept(
                            QuickBuildUiAction.shape(option.shape)));
            button.active = option.enabled;
            this.shapeButtons[i] = button;
        }
    }

    void refreshControlButtons(QuickBuildUiState state) {
        this.controlSignature = controlSignature(state);
        List<WindowButton> regular = new ArrayList<>();
        this.connectToggle = null;
        for (QuickBuildUiControl control : state.controls) {
            WindowButton button = new WindowButton(
                    0, 0,
                    QuickBuildWindowLayout.CONTROL_W,
                    QuickBuildWindowLayout.CONTROL_H,
                    Component.literal(control.label),
                    ignored -> this.dispatch.accept(
                            QuickBuildUiAction.control(control.id)));
            button.active = control.enabled;
            if (control.id == QuickBuildUiControl.Id.CONNECT) {
                this.connectToggle = button;
            } else {
                regular.add(button);
            }
        }
        this.controlButtons = regular.toArray(new WindowButton[0]);
    }

    void syncChainLimit(int value) {
        if (this.chainLimitSlider == null) {
            return;
        }
        this.syncingChainLimit = true;
        try {
            this.chainLimitSlider.setValue(value);
        } finally {
            this.syncingChainLimit = false;
        }
    }

    void render(
            RtsGuiContext graphics,
            MinecraftUiCanvas canvas,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int windowWidth,
            int mouseX,
            int mouseY,
            float partialTick) {
        prepare(state, layout, windowWidth);
        this.renderer.render(
                this, graphics, canvas, screen, state, layout,
                mouseX, mouseY, partialTick);
    }

    void renderTooltip(
            RtsGuiContext graphics,
            BuilderScreen screen,
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int windowWidth,
            int mouseX,
            int mouseY) {
        prepare(state, layout, windowWidth);
        this.renderer.renderTooltip(this, graphics, screen, state, mouseX, mouseY);
    }

    boolean mouseClicked(
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int windowWidth,
            double mouseX,
            double mouseY,
            int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        prepare(state, layout, windowWidth);
        if (state.chainMode()
                && this.chainLimitSlider.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        QuickBuildUiMode mode = layout.modeAt(mouseX, mouseY);
        if (mode != null) {
            if (mode != QuickBuildUiMode.DESTROY || state.destroyEnabled) {
                this.dispatch.accept(QuickBuildUiAction.mode(mode));
            }
            return true;
        }
        if (click(this.shapeButtons, mouseX, mouseY, button)
                || click(this.controlButtons, mouseX, mouseY, button)) {
            return true;
        }
        return this.connectToggle != null
                && this.connectToggle.mouseClicked(mouseX, mouseY, button);
    }

    boolean mouseDragged(
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int windowWidth,
            double mouseX,
            double mouseY,
            int button) {
        prepare(state, layout, windowWidth);
        return state.chainMode()
                && this.chainLimitSlider.mouseDragged(mouseX, mouseY, button);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.chainLimitSlider != null
                && this.chainLimitSlider.mouseReleased(mouseX, mouseY, button);
    }

    int shapeButtonCount() {
        return this.shapeButtons.length;
    }

    WindowButton shapeButton(int index) {
        return this.shapeButtons[index];
    }

    int controlButtonCount() {
        return this.controlButtons.length;
    }

    WindowButton controlButton(int index) {
        return this.controlButtons[index];
    }

    WindowButton connectToggle() {
        return this.connectToggle;
    }

    WindowSlider chainLimitSlider() {
        return this.chainLimitSlider;
    }

    private void prepare(
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int windowWidth) {
        if (!this.shapeSignature.equals(shapeSignature(state))) {
            refreshShapeButtons(state);
        }
        if (!this.controlSignature.equals(controlSignature(state))) {
            refreshControlButtons(state);
        }
        ensureChainLimitSlider(state);
        this.chainLimitSlider.setVisible(state.chainMode());
        syncChainLimit(state.chainLimit);
        position(state, layout, windowWidth);
    }

    private void position(
            QuickBuildUiState state,
            QuickBuildWindowLayout.Geometry layout,
            int windowWidth) {
        for (int i = 0; i < this.shapeButtons.length; i++) {
            this.shapeButtons[i].setX(layout.shapeX(i));
            this.shapeButtons[i].setY(layout.shapeY(i));
            if (i < state.shapes.size()) {
                this.shapeButtons[i].active = state.shapes.get(i).enabled;
            }
        }
        for (int i = 0; i < this.controlButtons.length; i++) {
            this.controlButtons[i].setX(layout.rightX);
            this.controlButtons[i].setY(layout.controlY(i));
        }
        if (this.connectToggle != null) {
            this.connectToggle.setX(layout.rightX);
            this.connectToggle.setY(layout.controlY(this.controlButtons.length));
        }
        this.chainLimitSlider.setWidth(
                QuickBuildWindowLayout.chainSliderWidth(windowWidth));
        this.chainLimitSlider.setX(layout.rightX);
        this.chainLimitSlider.setY(layout.chainSliderY);
    }

    private void ensureChainLimitSlider(QuickBuildUiState state) {
        if (this.chainLimitSlider == null) {
            this.chainLimitSlider = new WindowSlider(
                    0, 0,
                    QuickBuildWindowLayout.chainSliderWidth(
                            QuickBuildWindowLayout.WINDOW_W),
                    QuickBuildWindowLayout.CHAIN_SLIDER_H,
                    state.chainMinimum, state.chainMaximum, state.chainLimit);
            this.chainLimitSlider.onChange(value -> {
                if (!this.syncingChainLimit) {
                    this.dispatch.accept(QuickBuildUiAction.limit(value));
                }
            });
            return;
        }
        this.syncingChainLimit = true;
        try {
            this.chainLimitSlider.setRange(
                    state.chainMinimum, state.chainMaximum);
        } finally {
            this.syncingChainLimit = false;
        }
    }

    private static boolean click(
            WindowButton[] buttons,
            double mouseX,
            double mouseY,
            int button) {
        for (WindowButton candidate : buttons) {
            if (candidate.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    private static String shapeSignature(QuickBuildUiState state) {
        StringBuilder result = new StringBuilder(state.mode.name());
        for (QuickBuildUiShapeOption option : state.shapes) {
            result.append('|').append(option.shape.name())
                    .append(':').append(option.selected)
                    .append(':').append(option.enabled);
        }
        return result.toString();
    }

    private static String controlSignature(QuickBuildUiState state) {
        StringBuilder result = new StringBuilder();
        for (QuickBuildUiControl control : state.controls) {
            result.append('|').append(control.id.name())
                    .append(':').append(control.label)
                    .append(':').append(control.enabled);
        }
        return result.toString();
    }
}
