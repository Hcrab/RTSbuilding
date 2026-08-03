package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 在服务端统一的容器存活检查边界放宽 RTS 远程菜单。
 *
 * <p>这里不猜测第三方 Container 类名，也不修改模组容器内部字段。只有已经由
 * RTS 远程交互登记、且 windowId 与玩家当前窗口精确相同的容器会跳过距离检查。</p>
 */
@Mixin(EntityPlayerMP.class)
abstract class RemoteContainerPlayerMixin {
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
