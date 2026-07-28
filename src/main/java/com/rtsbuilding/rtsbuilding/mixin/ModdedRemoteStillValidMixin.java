package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
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
 * 原版箱子由 {@link ChestMenuMixin} 单独处理。
 */
@Pseudo
@Mixin(targets = {
        "ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase",
        "ironfurnaces.container.BlockWirelessEnergyHeaterContainerBase",
        "ironfurnaces.container.ContainerIronFurnaceBase",
        "cy.jdkdigital.generatorgalore.common.container.GeneratorMenu",
        "cy.jdkdigital.generatorgalore.common.container.GeneratorContainer",
        "net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer",
        "net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu"
}, remap = false)
abstract class ModdedRemoteStillValidMixin {

    @Inject(method = {"canInteractWith", "stillValid"}, at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void rtsbuilding$forceRemoteStillValid(EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid((Container) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
