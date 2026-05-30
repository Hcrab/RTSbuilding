package com.rtsbuilding.rtsbuilding.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.RtsFreeCamInput;

/**
 * Freezes the player's movement input during RTS mode so WASD only
 * drives the RTS camera and not the player body.
 * <p>
 * Uses {@code remap = false} with explicit SRG method name, matching
 * the pattern of existing compat mixins in this project.
 */
@Mixin(value = LocalPlayer.class, remap = false)
public class RtsFreeCamMixin {

    private final RtsFreeCamInput rtsbuilding$dummyInput =
            new RtsFreeCamInput();

    private Input rtsbuilding$savedInput;

    @Inject(method = {"tick", "m_8119_"}, at = @At("HEAD"),
            remap = false, require = 0)
    private void rtsbuilding$freezeInputPre(final CallbackInfo ci) {
        if (ClientRtsController.get().isEnabled()) {
            final LocalPlayer self = (LocalPlayer) (Object) this;
            this.rtsbuilding$savedInput = self.input;
            self.input = this.rtsbuilding$dummyInput;
        }
    }

    @Inject(method = {"tick", "m_8119_"}, at = @At("RETURN"),
            remap = false, require = 0)
    private void rtsbuilding$freezeInputPost(final CallbackInfo ci) {
        if (this.rtsbuilding$savedInput != null) {
            final LocalPlayer self = (LocalPlayer) (Object) this;
            self.input = this.rtsbuilding$savedInput;
            this.rtsbuilding$savedInput = null;
        }
    }
}
