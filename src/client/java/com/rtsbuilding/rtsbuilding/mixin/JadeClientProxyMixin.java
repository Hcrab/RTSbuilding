package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 允许 Jade 在 RTS Screen 的 GUI 后置阶段绘制，从而显示在 RTS 面板上方。
 */
@Pseudo
@Mixin(targets = "snownee.jade.util.ClientProxy", remap = false)
public final class JadeClientProxyMixin {
    @Inject(method = "shouldShowAfterGui", at = @At("RETURN"), cancellable = true, remap = false)
    private static void rtsbuilding$showAfterBuilderScreen(
            Minecraft minecraft, Screen screen, CallbackInfoReturnable<Boolean> callback) {
        if (screen instanceof BuilderScreen) {
            callback.setReturnValue(true);
        }
    }
}
