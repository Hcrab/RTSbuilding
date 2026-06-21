package com.rtsbuilding.rtsbuilding.blueprint.client;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * Window-layer shell for blueprint material details.
 *
 * <p>The selected blueprint and material analysis remain owned by
 * {@link BlueprintPanel}; this panel only makes the detail popup behave like a
 * normal RTS floating window with z-order, persistence, and shared input
 * routing.
 */
public final class BlueprintMaterialWindowPanel extends RtsWindowPanel {
    private static final int DEFAULT_W = 560;
    private static final int DEFAULT_H = 340;
    private static final int MIN_W = 300;
    private static final int MIN_H = 208;

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
    }

    public void syncWithBlueprintState() {
        if (BlueprintPanel.isMaterialDialogOpen()) {
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
        BlueprintEntry entry = BlueprintPanel.materialDialogEntry();
        if (entry == null) {
            BlueprintPanel.closeMaterialDialog();
            return;
        }
        int scroll = BlueprintMaterialDialog.renderContent(g, screen.font(), entry, controller,
                contentX(), contentY(), contentWidth(), contentHeight(),
                mouseX, mouseY, BlueprintPanel.materialDialogScroll());
        BlueprintPanel.setMaterialDialogScroll(scroll);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        // The material window is inspect-only; clicks inside simply keep focus on
        // this window and prevent the world/camera from receiving the event.
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        BlueprintEntry entry = BlueprintPanel.materialDialogEntry();
        if (entry == null) {
            BlueprintPanel.closeMaterialDialog();
            return true;
        }
        BlueprintPanel.setMaterialDialogScroll(BlueprintMaterialDialog.scrolledContent(
                BlueprintPanel.materialDialogScroll(), scrollY, entry, controller, contentWidth(), contentHeight()));
        return true;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            BlueprintPanel.closeMaterialDialog();
            setOpen(false);
            return true;
        }
        return true;
    }

    @Override
    protected void onClose() {
        if (BlueprintPanel.isMaterialDialogOpen()) {
            BlueprintPanel.closeMaterialDialog();
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
}
