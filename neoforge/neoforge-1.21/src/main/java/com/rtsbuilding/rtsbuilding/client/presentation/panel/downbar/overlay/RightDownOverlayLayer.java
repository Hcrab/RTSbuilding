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

    /**
     * 无条件清空当前选材（UI 选取态 + 建造启用）。退出 RTS 模式时调用。
     */
    public void cancelSelection() {
        itemGrid.cancelSelection();
    }

    /**
     * 若当前选材与放下的物品同类则取消选材（点击容器槽位放下物品时调用）。
     */
    public void cancelSelectionIf(ItemStack carried) {
        itemGrid.cancelSelectionIf(carried);
    }

    public ItemStack getHoveredSlotStack() {
        return itemGrid.getHoveredSlotStack();
    }
}
