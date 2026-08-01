package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iron Furnaces 菜单的远程 {@code stillValid} 兼容入口。
 *
 * <p>该类只负责把第三方菜单接入通用判定；是否加载由
 * {@link RtsOptionalCompatMixinConfigPlugin} 按模组安装状态决定。
 */
@Pseudo
@Mixin(targets = {
        "ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase",
        "ironfurnaces.container.BlockWirelessEnergyHeaterContainerBase"
}, remap = false)
abstract class IronFurnacesRemoteStillValidMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$forceRemoteStillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid((AbstractContainerMenu) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
