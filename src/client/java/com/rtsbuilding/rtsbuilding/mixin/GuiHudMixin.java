package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 对齐 NeoForge 版本：RTS 模式隐藏原版准星和快捷栏图层，其余 HUD 保持可见。 */
@Mixin(Gui.class)
public abstract class GuiHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$renderCrosshair(
            GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        if (RtsClientInputGate.suppressVanillaHudElements()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$renderHotbar(
            GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        if (RtsClientInputGate.suppressVanillaHudElements()) {
            callback.cancel();
        }
    }
}
