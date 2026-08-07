package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止 Flywheel 为当前隐藏范围重新创建方块实体 Visual。
 *
 * <p>范围状态改变时，已存在的 Visual 由 {@code RtsFlywheelCullingCompat} 主动同步；本 Mixin 只守住 Flywheel
 * 的统一准入点，避免方块更新、区块重载或渲染器重建让隐藏机械重新出现。 它不识别 Create 或任何方块 ID，也不维护第二份隐藏状态。
 */
@Pseudo
@Mixin(
    targets = "dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage",
    remap = false)
public abstract class FlywheelBlockEntityStorageMixin {
  @Inject(
      method = "willAccept(Lnet/minecraft/world/level/block/entity/BlockEntity;)Z",
      at = @At("HEAD"),
      cancellable = true,
      remap = false,
      require = 1)
  private void rtsbuilding$rejectCulledBlockEntity(
      BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
    if (blockEntity != null && RtsCullingClientState.shouldCull(blockEntity.getBlockPos())) {
      cir.setReturnValue(false);
    }
  }
}
