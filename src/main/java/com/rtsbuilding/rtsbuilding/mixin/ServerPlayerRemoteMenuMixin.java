package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 将 RTS 精确追踪的远程菜单接入服务端通用的有效性与关闭闸门。
 *
 * <p>本类不识别菜单类型，也不扩大客户端权限；仅当同一玩家、同一菜单对象处于远程交互链中时，才保留打开状态。 这样未知第三方菜单可持续工作，而普通本地菜单、显式关闭和 containerId
 * 重用仍保持原版行为。
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerRemoteMenuMixin {
  @Redirect(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
  private boolean rtsbuilding$keepTrackedRemoteMenuOpen(AbstractContainerMenu menu, Player player) {
    return RtsRemoteMenuCompat.shouldKeepServerRemoteMenuOpen(menu, player)
        || menu.stillValid(player);
  }

  /** 第三方菜单也可能在自己的同步逻辑中直接请求服务端关闭，统一闸门需要覆盖该路径。 */
  @Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
  private void rtsbuilding$keepTrackedRemoteMenuFromServerClose(CallbackInfo ci) {
    ServerPlayer player = (ServerPlayer) (Object) this;
    if (RtsRemoteMenuCompat.shouldKeepServerRemoteMenuOpen(player.containerMenu, player)) {
      ci.cancel();
    }
  }

  /** 接住交互返回后才打开的第三方菜单，消费一次性远程目标期望。 */
  @Inject(method = "openMenu", at = @At("RETURN"))
  private void rtsbuilding$adoptDeferredRemoteMenu(
      MenuProvider provider, CallbackInfoReturnable<OptionalInt> callback) {
    RtsRemoteMenuService.onServerMenuOpened((ServerPlayer) (Object) this);
  }
}
