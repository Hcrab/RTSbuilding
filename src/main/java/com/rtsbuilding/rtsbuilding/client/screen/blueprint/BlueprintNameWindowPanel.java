package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintWindowLayout;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Window-layer shell for blueprint save/rename naming.
 *
 * <p>The blueprint file operation state remains in {@link BlueprintPanel}; this
 * class only exposes that state through the shared RTS floating-window chrome so
 * it can stack with settings, guide, storage, and blueprint control windows.
 */
public final class BlueprintNameWindowPanel extends RtsWindowPanel {
    private static final int DEFAULT_W = BlueprintWindowLayout.NAME_W;
    private static final int DEFAULT_H = BlueprintWindowLayout.NAME_H;
    private static final int MIN_W = 300;
    private static final int MIN_H = 122;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
    }

    public void syncWithBlueprintState() {
        if (BlueprintUiStateAdapter.snapshot().nameWindowOpen) {
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
        BlueprintUiState state = BlueprintUiStateAdapter.snapshot();
        if (!state.nameWindowOpen) {
            return;
        }
        BlueprintNameDialog.renderCoreContent(g, screen.font(), contentX(), contentY(),
                contentWidth(), contentHeight(), mouseX, mouseY, state);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !BlueprintUiStateAdapter.snapshot().nameWindowOpen) {
            return;
        }
        BlueprintNameDialog.ClickResult click = BlueprintNameDialog.clickContent(
                mouseX, mouseY, contentX(), contentY(), contentWidth(), contentHeight());
        if (click == BlueprintNameDialog.ClickResult.CONFIRM) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.simple(
                    BlueprintUiAction.Type.CONFIRM_NAME), controller);
            setOpen(false);
        } else if (click == BlueprintNameDialog.ClickResult.CANCEL) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.simple(
                    BlueprintUiAction.Type.CANCEL_NAME), controller);
            setOpen(false);
        }
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        BlueprintUiState state = BlueprintUiStateAdapter.snapshot();
        if (!state.nameWindowOpen) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.simple(
                    BlueprintUiAction.Type.CONFIRM_NAME), controller);
            setOpen(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.simple(
                    BlueprintUiAction.Type.BACKSPACE_NAME), controller);
            return true;
        }
        return true;
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        if (Character.isISOControl(codePoint)) {
            return true;
        }
        return BlueprintUiStateAdapter.dispatch(BlueprintUiAction.text(
                BlueprintUiAction.Type.APPEND_NAME_CHAR, Character.toString(codePoint)), controller);
    }

    @Override
    protected void onClose() {
        if (BlueprintUiStateAdapter.snapshot().nameWindowOpen) {
            BlueprintUiStateAdapter.dispatch(BlueprintUiAction.simple(
                    BlueprintUiAction.Type.CANCEL_NAME), controller);
        }
    }

    @Override
    protected Component getTitle() {
        return Component.translatable(BlueprintUiStateAdapter.snapshot().captureNameMode
                ? "screen.rtsbuilding.blueprints.name_dialog_capture_title"
                : "screen.rtsbuilding.blueprints.name_dialog_rename_title");
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
            PersistableProperty.bounds("blueprint_name", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
