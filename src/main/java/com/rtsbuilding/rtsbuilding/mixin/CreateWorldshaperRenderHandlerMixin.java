package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.compat.RtsVanillaCursorHitBridge;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让机械动力“创造模式世界塑形器”的方块簇预览跟随 RTS 自由光标。
 *
 * <p>只重定向 WorldshaperRenderHandler 生成预览时的那一次方块射线，
 * 不修改工具执行、方块集合算法或其他 Create 工具，避免把兼容范围无意放大。</p>
 */
@Pseudo
@Mixin(
        targets = "com.simibubi.create.content.equipment.zapper.terrainzapper.WorldshaperRenderHandler",
        remap = false)
public final class CreateWorldshaperRenderHandlerMixin {
    @Redirect(
            method = "createBrushOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",
                    remap = false),
            require = 0,
            remap = false)
    private static BlockHitResult rtsbuilding$useRtsCursor(Level level, ClipContext context) {
        BlockHitResult hit = RtsVanillaCursorHitBridge.currentRtsBlockHit();
        return hit != null ? hit : level.clip(context);
    }
}
