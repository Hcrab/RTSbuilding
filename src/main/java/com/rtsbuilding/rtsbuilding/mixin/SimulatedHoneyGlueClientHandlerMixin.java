package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.compat.RtsVanillaCursorHitBridge;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Simulated / Create Aeronautics 的蜂蜜胶预览读取 RTS 自由光标。
 *
 * <p>蜂蜜胶没有读取 Minecraft.hitResult，而是在每个客户端 tick 中重新沿玩家视线
 * 做一次方块射线。本适配只替换它自己的射线入口；离开 RTS 后完全保留上游行为。</p>
 */
@Pseudo
@Mixin(
        targets = "dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueClientHandler",
        remap = false)
public final class SimulatedHoneyGlueClientHandlerMixin {
    @Inject(
            method = "getHitResult",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void rtsbuilding$useRtsCursor(CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult hit = RtsVanillaCursorHitBridge.currentRtsBlockHit();
        if (hit != null) {
            cir.setReturnValue(hit);
        }
    }
}
