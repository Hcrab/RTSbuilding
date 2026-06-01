package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.DummyMovementInput;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class RtsPlayerInputMixin {
    @Unique
    private static final DummyMovementInput rtsbuilding$dummyInput = new DummyMovementInput();

    @Unique
    private Input rtsbuilding$originalInput;

    @Inject(method = "tick", at = @At("HEAD"))
    private void rtsbuilding$freezeInputBeforeTick(CallbackInfo ci) {
        if (!ClientRtsController.get().isEnabled() || this.rtsbuilding$originalInput != null) {
            return;
        }
        LocalPlayer self = (LocalPlayer) (Object) this;
        this.rtsbuilding$originalInput = self.input;
        self.input = rtsbuilding$dummyInput;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void rtsbuilding$restoreInputAfterTick(CallbackInfo ci) {
        if (this.rtsbuilding$originalInput == null) {
            return;
        }
        LocalPlayer self = (LocalPlayer) (Object) this;
        self.input = this.rtsbuilding$originalInput;
        this.rtsbuilding$originalInput = null;
    }
}
