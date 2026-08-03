package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.jei.RtsOverlayAwareJeiTransferHandler;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 JEI/HEI 完成精确容器处理器查找后，按需叠加 RTS overlay 材料源。
 *
 * <p>{@link Pseudo} 与独立的非必需 Mixin 配置确保未安装 JEI 时完全跳过；注入只替换返回的
 * 处理器对象，不修改注册表，也不影响插件注册顺序。</p>
 */
@Pseudo
@Mixin(targets = "mezz.jei.recipes.RecipeRegistry", remap = false)
public abstract class RecipeRegistryOverlayTransferMixin {
    @Inject(method = "getRecipeTransferHandler", at = @At("RETURN"),
            cancellable = true, remap = false, require = 0)
    private void rtsbuilding$wrapOverlayTransferHandler(
            Container container,
            IRecipeCategory<?> category,
            CallbackInfoReturnable<IRecipeTransferHandler> cir) {
        IRecipeTransferHandler handler = cir.getReturnValue();
        IRecipeTransferHandler wrapped = RtsOverlayAwareJeiTransferHandler.wrap(handler);
        if (wrapped != handler) {
            cir.setReturnValue(wrapped);
        }
    }
}
