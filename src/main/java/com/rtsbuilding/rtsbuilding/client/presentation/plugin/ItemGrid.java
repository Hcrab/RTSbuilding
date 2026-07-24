package com.rtsbuilding.rtsbuilding.client.presentation.plugin;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid.ContainerModePopup;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid.GridInputHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid.GridRenderer;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid.GridState;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid.TypeFilterPopup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class ItemGrid {

    private final OverlayContext context;
    private final ScrollBar scrollBar = new ScrollBar();
    private final ScrollBar recentScrollBar = new ScrollBar();
    private final GridState state = new GridState();
    private final TypeFilterPopup typeFilterPopup;
    private final ContainerModePopup containerModePopup;
    private final GridRenderer renderer;
    private final GridInputHandler inputHandler;

    public ItemGrid(OverlayContext context) {
        this.context = context;
        this.typeFilterPopup = new TypeFilterPopup(state.showItems, state.showFluids, (items, fluids) -> onTypeFilterChanged(items, fluids));
        this.containerModePopup = new ContainerModePopup(state.showBidirectional, state.showExtractOnly, (bidirectional, extractOnly) -> {
            boolean changed = state.showBidirectional != bidirectional || state.showExtractOnly != extractOnly;
            state.showBidirectional = bidirectional;
            state.showExtractOnly = extractOnly;
            if (changed) {
                state.slotEntriesDirty = true;
            }
        });
        this.renderer = new GridRenderer(context, scrollBar, recentScrollBar, state, typeFilterPopup, containerModePopup);
        this.inputHandler = new GridInputHandler(context, scrollBar, recentScrollBar, state, typeFilterPopup, containerModePopup, renderer);
    }

    private void onTypeFilterChanged(boolean showItems, boolean showFluids) {
        boolean stateChanged = state.showItems != showItems || state.showFluids != showFluids;
        state.showItems = showItems;
        state.showFluids = showFluids;
        if (stateChanged) {
            state.slotEntriesDirty = true;
        }
    }

    public ItemStack getCurrentSelectedItem() {
        return state.currentSelectedItem;
    }

    public ItemStack getHoveredSlotStack() {
        if (state.tooltipSlotIndex == -2) {
            return state.currentSelectedItem;
        }
        if (state.tooltipSlotIndex < 0 || state.tooltipSlotIndex >= state.slotEntries.size()) return ItemStack.EMPTY;
        return state.slotEntries.get(state.tooltipSlotIndex).stack();
    }

    public void renderContent(GuiGraphics g) {
        renderer.renderContent(g);
    }

    public void postRenderContent(GuiGraphics g) {
        renderer.postRenderContent(g);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return inputHandler.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return inputHandler.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return inputHandler.charTyped(codePoint, modifiers);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputHandler.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return inputHandler.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean isMouseOverPopup(int mx, int my) {
        return (typeFilterPopup.isOpen() && typeFilterPopup.contains(mx, my))
                || (containerModePopup.isOpen() && containerModePopup.contains(mx, my));
    }
}
