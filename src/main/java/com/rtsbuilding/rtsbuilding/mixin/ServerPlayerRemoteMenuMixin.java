package com.rtsbuilding.rtsbuilding.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在服务端真正因为 {@code stillValid} 失败而关窗的公共闸门处保护 RTS 远程菜单。
 *
 * <p>这里只认生产交互链记录的同一玩家、同一菜单对象；普通本地菜单、换窗和容器编号复用
 * 仍保持原版行为。表达式修改允许其他模组在同一调用点继续叠加，并在目标消失时安全跳过。</p>
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerRemoteMenuMixin {
    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z",
                    remap = false),
            require = 0,
            remap = false)
    private boolean rtsbuilding$keepTrackedRemoteMenuOpenNamed(boolean original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        return original || RtsRemoteMenuCompat.shouldKeepServerRemoteMenuOpen(player.containerMenu, player);
    }

    /** Forge 生产环境使用 SRG 名；与上面的开发映射入口保持完全相同的判定。 */
    @ModifyExpressionValue(
            method = "m_8119_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;m_6875_(Lnet/minecraft/world/entity/player/Player;)Z",
                    remap = false),
            require = 0,
            remap = false)
    private boolean rtsbuilding$keepTrackedRemoteMenuOpenSrg(boolean original) {
        return rtsbuilding$keepTrackedRemoteMenuOpenNamed(original);
    }

    /**
     * 部分第三方菜单会从自己的同步逻辑直接调用 closeContainer；RTS 精确跟踪的菜单需要
     * 拦住这种服务端主动关窗。玩家按 Esc 走 doCloseContainer，不会被这里吞掉。
     */
    @Inject(
            method = { "closeContainer", "m_6915_" },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$keepTrackedRemoteMenuFromServerClose(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (RtsRemoteMenuCompat.shouldKeepServerRemoteMenuOpen(player.containerMenu, player)) {
            ci.cancel();
        }
    }
}
