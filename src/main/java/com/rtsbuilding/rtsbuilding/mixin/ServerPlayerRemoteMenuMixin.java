package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在服务端真正因为 {@code stillValid} 失败而关窗的唯一公共闸门处保护 RTS 远程菜单。
 *
 * <p>本类不负责判断菜单类型，也不修改菜单自己的业务逻辑。只有经过生产 RTS 交互链记录、
 * 且仍是同一玩家的同一菜单对象时才放行；普通本地 GUI、换窗和复用 containerId 都保持原版行为。
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerRemoteMenuMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean rtsbuilding$keepTrackedRemoteMenuOpen(
            AbstractContainerMenu menu, Player player) {
        if (RtsRemoteMenuCompat.shouldKeepServerRemoteMenuOpen(menu, player)) {
            return true;
        }
        return menu.stillValid(player);
    }

    /**
     * 部分第三方菜单不会把距离失效留给 ServerPlayer.tick 处理，而会在自己的同步逻辑里
     * 直接调用 closeContainer。被 RTS 精确跟踪的同一个菜单对象必须拦住这种服务端主动关窗；
     * 玩家按 Esc 发来的正常关闭走 doCloseContainer，因此不会被这里吞掉。
     */
    @Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$keepTrackedRemoteMenuFromServerClose(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (RtsRemoteMenuCompat.shouldKeepServerRemoteMenuOpen(player.containerMenu, player)) {
            ci.cancel();
        }
    }
}
