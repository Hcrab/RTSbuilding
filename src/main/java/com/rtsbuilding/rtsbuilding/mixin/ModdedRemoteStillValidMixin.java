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
 * 统一维持已支持第三方远程容器的有效性。
 *
 * <p>本 Mixin 只把通过 {@link RtsRemoteMenuCompat} 标记的远程菜单视为有效；
 * 本地打开的同类菜单仍使用模组原生距离检查。原版 {@link ChestMenu} 继续由
 * {@link ChestMenuMixin} 处理。</p>
 */
@Pseudo
@Mixin(targets = {
        "ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase",
        "ironfurnaces.container.BlockWirelessEnergyHeaterContainerBase",
        "cy.jdkdigital.generatorgalore.common.container.GeneratorMenu",
        "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase",
        "net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu"
}, remap = false)
abstract class ModdedRemoteStillValidMixin {

    @Inject(
            method = { "stillValid", "m_6875_" },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void rtsbuilding$forceRemoteStillValid(
            Player player,
            CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid(
                (AbstractContainerMenu) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
