package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.overlay;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.ItemGrid;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class RightDownOverlayLayer extends DownOverlayLayer {

    private final ItemGrid itemGrid;

    public RightDownOverlayLayer() {
        this.itemGrid = new ItemGrid(this);
    }

    @Override
    public void renderContent(GuiGraphics g) {
        itemGrid.renderContent(g);
    }

    @Override
    public void postRenderContent(GuiGraphics g) {
        itemGrid.postRenderContent(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return itemGrid.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return itemGrid.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return itemGrid.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return itemGrid.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return itemGrid.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return itemGrid.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public void unfocusSearch() {
        itemGrid.unfocusSearch();
    }

    public boolean isMouseOverPopup(int mx, int my) {
        return itemGrid.isMouseOverPopup(mx, my);
    }

    public ItemStack getCurrentSelectedItem() {
        return itemGrid.getCurrentSelectedItem();
    }

    public ItemStack getHoveredSlotStack() {
        return itemGrid.getHoveredSlotStack();
    }
}
