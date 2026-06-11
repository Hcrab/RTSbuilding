package com.rtsbuilding.rtsbuilding.mixin.compat.sophisticatedstorage;


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.rtsbuilding.rtsbuilding.compat.sophisticatedstorage.RtsSophisticatedStorageCompat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Pseudo
@Mixin(targets = {
        "net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu",
        "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase"
}, remap = false)
public abstract class StorageContainerMenuMixin {
    @Inject(method = { "stillValid", "m_6875_" }, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rtsbuilding$forceRemoteStillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (RtsSophisticatedStorageCompat.shouldForceStillValid((AbstractContainerMenu) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
