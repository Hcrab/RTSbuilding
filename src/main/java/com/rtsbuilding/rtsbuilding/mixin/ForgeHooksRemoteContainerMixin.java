package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 1.7.10 Forge 统一的容器存活检查入口放行 RTS 已登记的远程窗口。
 *
 * <p>1.7.10 的 EntityPlayer 与 EntityPlayerMP 都先调用
 * {@link ForgeHooks#canInteractWith(EntityPlayer, Container)}，而不是直接调用
 * {@link Container#canInteractWith(EntityPlayer)}。挂在这里既能覆盖原版容器，也能覆盖
 * GTNH 机器容器，并且仍以精确的玩家/windowId 登记为边界，不会全局放宽普通 GUI。</p>
 */
@Mixin(value = ForgeHooks.class, remap = false)
public abstract class ForgeHooksRemoteContainerMixin {
    @Inject(method = "canInteractWith", at = @At("HEAD"), cancellable = true, remap = false)
    private static void rtsbuilding$keepMarkedRemoteContainerOpen(
            EntityPlayer player, Container container, CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid(container, player)) {
            cir.setReturnValue(true);
        }
    }
}
