package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.overlay;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.ContainerBinding;
import net.minecraft.client.gui.GuiGraphics;

public final class LeftDownOverlayLayer extends DownOverlayLayer {

    private final ContainerBinding containerBinding;

    public LeftDownOverlayLayer() {
        this.containerBinding = new ContainerBinding(this);
    }

    @Override
    public void renderContent(GuiGraphics g) {
        containerBinding.renderContent(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return containerBinding.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return containerBinding.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return containerBinding.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return containerBinding.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return containerBinding.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return containerBinding.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
