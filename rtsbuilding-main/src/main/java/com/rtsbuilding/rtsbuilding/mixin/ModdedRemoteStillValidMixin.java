package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 统一 Mixin：强制远程容器的 stillValid 通过。
 * <p>
 * 覆盖所有已支持的第三方 Mod 容器类（Iron Furnaces、Generator Galore、
 * Sophisticated Storage），使其在 RTS 远程操作模式下仍保持有效。
 * 原版 {@link ChestMenu} 由 {@link ChestMenuMixin} 单独处理。
 */
@Pseudo
@Mixin(targets = {
        "ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase",
        "ironfurnaces.container.BlockWirelessEnergyHeaterContainerBase",
        "cy.jdkdigital.generatorgalore.common.container.GeneratorMenu",
        "net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu"
}, remap = false)
abstract class ModdedRemoteStillValidMixin {

    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$forceRemoteStillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid((AbstractContainerMenu) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
