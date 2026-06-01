package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsCraftTerminalScreen;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

final class RtsCraftTerminalJeiGuiHandler implements IGuiContainerHandler<RtsCraftTerminalScreen> {
    private final IIngredientManager ingredientManager;

    RtsCraftTerminalJeiGuiHandler(IIngredientManager ingredientManager) {
        this.ingredientManager = ingredientManager;
    }

    @Override
    public List<Rect2i> getGuiExtraAreas(RtsCraftTerminalScreen screen) {
        return List.of(screen.getLinkedPanelArea());
    }

    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(
            RtsCraftTerminalScreen screen,
            double mouseX,
            double mouseY) {
        ClientRtsController.StorageEntry entry = screen.getLinkedEntryAt(mouseX, mouseY);
        if (entry == null) {
            return Optional.empty();
        }
        ItemStack stack = entry.stack();
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        Rect2i area = screen.getLinkedSlotAreaAt(mouseX, mouseY);
        if (area == null) {
            return Optional.empty();
        }

        return this.ingredientManager
                .createClickableIngredient(stack.copy(), area, true)
                .map(clickable -> (IClickableIngredient<?>) clickable);
    }
}

