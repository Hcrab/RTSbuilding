package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsFlywheelCullingPolicy;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止 Flywheel 0.6.8 为当前隐藏位置重新创建方块实体实例。
 *
 * <p>Flywheel 的原生 {@code LevelChunk#setBlockEntity} mixin 会调用实例管理器的
 * {@code add}，所有新增最终都经过 {@code canCreateInstance}。这里守住这个统一 admission
 * 判定，因此区块更新、重载和实例世界重建都不会让隐藏机械重新出现；它不取消方块实体加入世界，
 * 不识别方块 ID，也不保存第二份剔除状态。</p>
 */
@Pseudo
@Mixin(
        targets = "com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstanceManager",
        remap = false)
public abstract class FlywheelBlockEntityInstanceManagerMixin {
    @Inject(
            method = "canCreateInstance(Lnet/minecraft/world/level/block/entity/BlockEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void rtsbuilding$rejectCulledBlockEntity(
            BlockEntity blockEntity,
            CallbackInfoReturnable<Boolean> cir) {
        if (blockEntity != null
                && !RtsFlywheelCullingPolicy.shouldAdmit(
                        RtsCullingClientState.shouldCull(blockEntity.getBlockPos()))) {
            cir.setReturnValue(false);
        }
    }
}
