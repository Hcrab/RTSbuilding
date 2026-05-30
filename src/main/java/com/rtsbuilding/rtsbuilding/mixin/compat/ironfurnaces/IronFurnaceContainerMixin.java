package com.rtsbuilding.rtsbuilding.mixin.compat.ironfurnaces;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Pseudo
@Mixin(targets = "ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase", remap = false)
abstract class IronFurnaceContainerMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$forceRemoteStillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid((AbstractContainerMenu) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
