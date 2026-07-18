package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
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
 * 阻止 Jade 在 RTS BuilderScreen 模式下因 target 为 null 而提前返回。
 * <p>
 * Jade 的 {@code RayTracing.rayTrace()} 在 RTS 模式下（相机离地 20+ 格，
 * 默认使用 {@code player.blockInteractionRange()} ≈ 4.5 格射程）返回 null，
 * 导致 {@code tickClient()} 在调用 {@code JadeRayTraceCallback} 前就 return。
 * 此处将 null target 替换为 MISS 类型的 {@link BlockHitResult}（非 null），
 * 使流程继续进入回调链，由 {@code RtsJadeRayTraceCallback} 用 RTS 的 128 格
 * 射线替换 accessor。
 */
@Pseudo
@Mixin(targets = "snownee.jade.overlay.WailaTickHandler", remap = false)
public class WailaTickHandlerMixin {

    /**
     * 拦截 {@code HitResult target = RayTracing.INSTANCE.getTarget()} 的赋值。
     * 当 target 为 null 且 BuilderScreen 打开时，返回 MISS 类型的 BlockHitResult
     * 使后续 null 检查通过，流程继续到回调。
     */
    @ModifyVariable(method = "tickClient", at = @At("STORE"), ordinal = 0)
    private HitResult rtsbuilding$patchNullTarget(HitResult target) {
        if (target != null) {
            return target;
        }
        if (!(Minecraft.getInstance().screen instanceof BuilderScreen)) {
            return target;
        }
        // MISS 类型的 BlockHitResult：非 null，但 getType() 为 MISS，
        // 不会触发 accessor 构建，accessor 保持 null。
        // 回调链中的 RtsJadeRayTraceCallback 会替换为有效的 RTS 射线结果。
        return BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
    }
}
