package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintWindowLayout;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Window-layer shell for blueprint material details.
 *
 * <p>The selected blueprint and material analysis remain owned by
 * {@link BlueprintPanel}; this panel only makes the detail popup behave like a
 * normal RTS floating window with z-order, persistence, and shared input
 * routing.
 */
public final class BlueprintMaterialWindowPanel extends RtsWindowPanel {
    private static final int DEFAULT_W = BlueprintWindowLayout.MATERIAL_W;
    private static final int DEFAULT_H = BlueprintWindowLayout.MATERIAL_H;
    private static final int MIN_W = 300;
    private static final int MIN_H = 208;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
    }

    public void syncWithBlueprintState() {
        if (BlueprintUiStateAdapter.snapshot().materialWindowOpen) {
            if (!isOpen()) {
                setOpen(true);
                markBroughtToFront();
            }
        } else if (isOpen()) {
            setOpen(false);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        BlueprintUiState state = BlueprintUiStateAdapter.snapshot(controller);
        if (!state.materialWindowOpen || state.materials.blueprintName.isEmpty()) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.simple(
                    BlueprintUiAction.Type.CLOSE_MATERIALS), controller);
            return;
        }
        int scroll = BlueprintMaterialDialog.renderCoreContent(g, screen.font(), state.materials,
                contentX(), contentY(), contentWidth(), contentHeight(),
                mouseX, mouseY, state.materialScroll);
        if (scroll != state.materialScroll) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.vector(
                    BlueprintUiAction.Type.SCROLL_MATERIALS, 0, scroll - state.materialScroll, 0), controller);
        }
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        // The material window is inspect-only; clicks inside simply keep focus on
        // this window and prevent the world/camera from receiving the event.
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        BlueprintUiState state = BlueprintUiStateAdapter.snapshot(controller);
        if (!state.materialWindowOpen) {
            return true;
        }
        int next = BlueprintMaterialDialog.scrolledCore(
                state.materialScroll, scrollY, state.materials, contentWidth(), contentHeight());
        BlueprintUiStateAdapter.dispatch(BlueprintUiAction.vector(
                BlueprintUiAction.Type.SCROLL_MATERIALS, 0, next - state.materialScroll, 0), controller);
        return true;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.simple(
                    BlueprintUiAction.Type.CLOSE_MATERIALS), controller);
            setOpen(false);
            return true;
        }
        return true;
    }

    @Override
    protected void onClose() {
        if (BlueprintUiStateAdapter.snapshot().materialWindowOpen) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.simple(
                    BlueprintUiAction.Type.CLOSE_MATERIALS), controller);
        }
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.blueprints.details_title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return DEFAULT_H;
    }

    @Override
    protected int getMinWindowWidth() {
        return MIN_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return MIN_H;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
        this.windowY = Mth.clamp((this.screen.height - this.windowHeight) / 2,
                24, Math.max(24, this.screen.height - this.windowHeight - 8));
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("blueprint_materials", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
