package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 放宽 1.12 基类玩家更新中的远程容器存活检查。
 *
 * <p>服务端玩家除了 {@code EntityPlayerMP#onUpdate()} 外，还会经过
 * {@code EntityPlayer#onUpdate()} 的第二次相同检查。两个门禁都必须只针对
 * 已登记的 RTS windowId 放行，否则服务端会在开窗后一 tick 立即发关窗包。</p>
 */
@Mixin(EntityPlayer.class)
abstract class RemoteBasePlayerContainerMixin {
    @Redirect(
            method = "onUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/inventory/Container;canInteractWith(Lnet/minecraft/entity/player/EntityPlayer;)Z"))
    private boolean rtsbuilding$keepMarkedRemoteContainerOpen(Container container, EntityPlayer player) {
        return RtsRemoteMenuCompat.shouldForceStillValid(container, player)
                || container.canInteractWith(player);
    }
}
