package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class RtsJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "jei_plugin");

    /** JEI 运行时引用，供合成终端搜索模式同步使用 */
    private static mezz.jei.api.runtime.IJeiRuntime jeiRuntime;

    public static mezz.jei.api.runtime.IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
        RtsJeiPlugin.jeiRuntime = jeiRuntime;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var transferHelper = registration.getTransferHelper();
        // C键RTS终端 — 原版3×3合成配方（保留精确9格布局）
        registration.addRecipeTransferHandler(
                new RtsCraftTerminalJeiTransferHandler(transferHelper),
                RecipeTypes.CRAFTING);
        // C键RTS终端 — 通用fallback（粉碎轮等非合成配方，堆叠模式）
        registration.addUniversalRecipeTransferHandler(
                new RtsCraftTerminalJeiUniversalTransferHandler(transferHelper));
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
