package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RTS 相机启用时拦截原版攻击、使用和选取，避免同一次输入同时修改真实玩家世界。 */
@Mixin(Minecraft.class)
public abstract class MinecraftInteractionMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$startAttack(CallbackInfoReturnable<Boolean> callback) {
        if (RtsClientInputGate.suppressVanillaInteractions()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$continueAttack(boolean attacking, CallbackInfo callback) {
        if (RtsClientInputGate.suppressVanillaInteractions()) {
            callback.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$startUseItem(CallbackInfo callback) {
        if (RtsClientInputGate.suppressVanillaInteractions()) {
            callback.cancel();
        }
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$pickBlock(CallbackInfo callback) {
        if (RtsClientInputGate.suppressVanillaInteractions()) {
            callback.cancel();
        }
    }
}
