package com.rtsbuilding.rtsbuilding.blueprint.client;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Window-layer shell for blueprint save/rename naming.
 *
 * <p>The blueprint file operation state remains in {@link BlueprintPanel}; this
 * class only exposes that state through the shared RTS floating-window chrome so
 * it can stack with settings, guide, storage, and blueprint control windows.
 */
public final class BlueprintNameWindowPanel extends RtsWindowPanel {
    private static final int DEFAULT_W = 420;
    private static final int DEFAULT_H = 146;
    private static final int MIN_W = 300;
    private static final int MIN_H = 122;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
    }

    public void syncWithBlueprintState() {
        if (BlueprintPanel.isNameDialogOpen()) {
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
        if (!BlueprintPanel.isNameDialogOpen()) {
            return;
        }
        BlueprintNameDialog.renderContent(g, screen.font(), contentX(), contentY(), contentWidth(), contentHeight(),
                mouseX, mouseY,
                BlueprintPanel.isNameDialogCaptureMode(),
                BlueprintPanel.nameDialogValue(),
                BlueprintPanel.nameDialogEntry(),
                BlueprintPanel.nameDialogCapturePointA(),
                BlueprintPanel.nameDialogCapturePointB(),
                BlueprintPanel.nameDialogCaptureBlockCount());
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !BlueprintPanel.isNameDialogOpen()) {
            return;
        }
        BlueprintNameDialog.ClickResult click = BlueprintNameDialog.clickContent(
                mouseX, mouseY, contentX(), contentY(), contentWidth(), contentHeight());
        if (click == BlueprintNameDialog.ClickResult.CONFIRM) {
            BlueprintPanel.confirmActiveNameDialog();
            setOpen(false);
        } else if (click == BlueprintNameDialog.ClickResult.CANCEL) {
            BlueprintPanel.cancelActiveNameDialog();
            setOpen(false);
        }
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!BlueprintPanel.isNameDialogOpen()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            BlueprintPanel.confirmActiveNameDialog();
            setOpen(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            BlueprintPanel.keyPressedNameDialog(keyCode);
            return true;
        }
        return true;
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return BlueprintPanel.charTypedNameDialog(codePoint);
    }

    @Override
    protected void onClose() {
        if (BlueprintPanel.isNameDialogOpen()) {
            BlueprintPanel.cancelActiveNameDialog();
        }
    }

    @Override
    protected Component getTitle() {
        return Component.translatable(BlueprintPanel.isNameDialogCaptureMode()
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
}
