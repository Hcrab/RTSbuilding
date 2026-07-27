package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestMenu.class)
abstract class ChestMenuMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$forceRemoteStillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid((AbstractContainerMenu) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
