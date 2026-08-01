package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningDropCapture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 在原版准备生成掉落实体时，让当前 RTS 挖掘作用域先接收可容纳的部分。
 * 返回余量继续走原版路径，因此缓存满、第三方修改栈或异常大堆栈都不会被吞掉。
 */
@Mixin(Block.class)
public abstract class BlockDropCaptureMixin {
    @ModifyVariable(method = "popResource", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ItemStack rtsbuilding$captureDropBeforeSpawn(ItemStack stack) {
        return RtsMiningDropCapture.captureDrop(stack);
    }
}
