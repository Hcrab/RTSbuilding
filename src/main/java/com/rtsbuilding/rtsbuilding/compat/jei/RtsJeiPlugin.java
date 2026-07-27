package com.rtsbuilding.rtsbuilding.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import net.minecraft.inventory.ContainerWorkbench;

/**
 * JEI 4（Minecraft 1.12.2）的兼容入口。
 *
 * <p>1.12 的 JEI 只提供单一的 {@link IModPlugin#register(IModRegistry)} 注册阶段，
 * GUI 扩展、全局覆盖层和配方转移都必须从这里接入。配方转移只有在成功取得 JEI
 * 已注册的原版工作台处理器时才会安装；这样普通工作台仍由 JEI 原实现负责，反射探测
 * 失败时则只关闭 RTS 特有转移，不会破坏整合包里的常规 JEI 功能。
 */
@JEIPlugin
public final class RtsJeiPlugin implements IModPlugin {
    @Override
    public void register(IModRegistry registry) {
        registry.addAdvancedGuiHandlers(new RtsCraftTerminalJeiGuiHandler());
        registry.addGlobalGuiHandlers(new RtsOverlayJeiGlobalGuiHandler());

        IRecipeTransferRegistry transferRegistry = registry.getRecipeTransferRegistry();
        IRecipeTransferHandler<ContainerWorkbench> vanillaDelegate =
                RtsCraftTerminalJeiTransferHandler.captureVanillaDelegate(transferRegistry);
        if (vanillaDelegate != null) {
            transferRegistry.addRecipeTransferHandler(
                    new RtsCraftTerminalJeiTransferHandler(
                            registry.getJeiHelpers().recipeTransferHandlerHelper(), vanillaDelegate),
                    VanillaRecipeCategoryUid.CRAFTING);
        }
    }
}
