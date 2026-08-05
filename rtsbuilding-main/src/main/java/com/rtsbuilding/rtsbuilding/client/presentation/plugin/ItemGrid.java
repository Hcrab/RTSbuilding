package com.rtsbuilding.rtsbuilding.client.presentation.plugin;

import com.rtsbuilding.rtsbuilding.client.domain.state.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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

    /**
     * 无条件清空当前选材（UI 选取态 + 建造启用）。退出 RTS 模式时调用。
     */
    public void cancelSelection() {
        inputHandler.cancelSelection();
    }

    /**
     * 若当前选材与放下的物品同类则取消选材（点击容器槽位放下物品时调用）。
     *
     * @param carried 放下前的 carried 物品
     */
    public void cancelSelectionIf(ItemStack carried) {
        if (ItemStack.isSameItemSameComponents(carried, state.currentSelectedItem)) {
            inputHandler.cancelSelection();
        }
    }

    public ItemStack getHoveredSlotStack() {
        if (state.tooltipSlotIndex < 0 || state.tooltipSlotIndex >= state.slotEntries.size()) return ItemStack.EMPTY;
        SlotEntry entry = state.slotEntries.get(state.tooltipSlotIndex);
        if (entry.isFluid() && entry.originalEntry() instanceof FluidEntry fe) {
            ItemStack stack = entry.stack().copy();
            stack.set(DataComponents.ITEM_NAME, Component.literal(fe.label()));
            return stack;
        }
        return entry.stack();
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

    public void unfocusSearch() {
        if (state.searchFocused) {
            state.searchFocused = false;
            StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
            if (sm != null) {
                sm.setSearch(state.searchBuffer.toString());
            }
        }
        state.recentSearchFocused = false;
    }

    public boolean isMouseOverPopup(int mx, int my) {
        return (typeFilterPopup.isOpen() && typeFilterPopup.contains(mx, my))
                || (containerModePopup.isOpen() && containerModePopup.contains(mx, my));
    }
}
