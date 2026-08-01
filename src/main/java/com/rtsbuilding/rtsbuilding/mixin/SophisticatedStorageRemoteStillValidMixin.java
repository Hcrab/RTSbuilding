package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sophisticated Storage 菜单的远程 {@code stillValid} 兼容入口。 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu", remap = false)
abstract class SophisticatedStorageRemoteStillValidMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$forceRemoteStillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid((AbstractContainerMenu) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
