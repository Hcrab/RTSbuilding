package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;

@JeiPlugin
public final class RtsJeiPlugin implements IModPlugin {
    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new RtsCraftTerminalJeiTransferHandler(registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        var ingredientManager = registration.getJeiHelpers().getIngredientManager();
        registration.addGuiContainerHandler(
                RtsCraftTerminalScreen.class,
                new RtsCraftTerminalJeiGuiHandler(ingredientManager));
        registration.addGlobalGuiHandler(new RtsOverlayJeiGlobalGuiHandler(ingredientManager));
    }
}
