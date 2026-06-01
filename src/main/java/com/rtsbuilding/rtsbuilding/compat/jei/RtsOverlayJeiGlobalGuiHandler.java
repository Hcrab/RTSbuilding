package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.RtsClientInputGate;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

final class RtsOverlayJeiGlobalGuiHandler implements IGlobalGuiHandler {
    private final IIngredientManager ingredientManager;

    RtsOverlayJeiGlobalGuiHandler(IIngredientManager ingredientManager) {
        this.ingredientManager = ingredientManager;
    }

    @Override
    public Collection<Rect2i> getGuiExtraAreas() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft == null ? null : minecraft.screen;
        if (screen == null) {
            return List.of();
        }
        return RtsClientInputGate.getJeiOverlayExtraAreas(screen);
    }

    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        RtsClientInputGate.JeiOverlayIngredient ingredient = RtsClientInputGate.getJeiOverlayIngredientUnderMouse(mouseX, mouseY);
        if (ingredient == null) {
            return Optional.empty();
        }
        ItemStack stack = ingredient.stack();
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        return this.ingredientManager
                .createClickableIngredient(stack.copy(), ingredient.area(), true)
                .map(clickable -> (IClickableIngredient<?>) clickable);
    }
}
