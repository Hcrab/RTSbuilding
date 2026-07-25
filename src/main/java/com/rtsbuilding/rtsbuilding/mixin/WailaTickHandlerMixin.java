package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Jade 默认短射线没有命中时会在自定义射线回调前提前返回。
 * 用 MISS 占位，让 RTS 的远距离光标射线始终有机会接管。
 */
@Pseudo
@Mixin(targets = "snownee.jade.overlay.WailaTickHandler", remap = false)
public final class WailaTickHandlerMixin {
    @ModifyVariable(method = "tickClient", at = @At("STORE"), ordinal = 0)
    private HitResult rtsbuilding$keepRtsRayTraceCallbackReachable(HitResult target) {
        if (target != null || !(Minecraft.getInstance().screen instanceof BuilderScreen)) {
            return target;
        }
        return BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
    }
}
