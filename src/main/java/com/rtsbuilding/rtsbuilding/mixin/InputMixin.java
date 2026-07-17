package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 操作模式：跳过原版 {@link KeyboardInput#tick} 基于 KeyMapping 的重置。
 * <p>
 * BuilderScreen 开启时 Minecraft 不会用 GLFW 更新 KeyMapping，
 * 原版 {@code tick} 会把 {@code forwardImpulse/leftImpulse/jumping/shiftKeyDown}
 * 均设为 false，导致 {@code ClientRtsController.tick}（Post 阶段）写入的
 * 操作模式输入被下一 tick 清零，玩家既无法用 WASD 移动也无法用空格跳跃。
 * <p>
 * 注意：不能 target {@link net.minecraft.client.player.Input} 基类，
 * 因为 {@code KeyboardInput} 覆盖了 {@code tick()}，Mixin 注入只对基类生效，
 * 子类的重写方法体在字节码层面是独立的，Mixin 无法拦截。
 * {@code forwardImpulse} / {@code leftImpulse} 是继承自 {@code Input} 的公有字段，
 * 不能在 {@code KeyboardInput} 上用 {@code @Shadow}（Mixin 不遍历父类），
 * 需要通过 {@code ((Input)(Object)this).forwardImpulse} 上溯造型访问。
 * <p>
 * 操作模式下仅保留原版的蹲下减速手感（×0.3），input 字段由
 * {@code ClientRtsController} 维护，原版 aiStep 据此驱动玩家移动/跳跃。
 */
@Mixin(KeyboardInput.class)
abstract class InputMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$skipKeyMappingResetInOperationMode(boolean slowDown, float f, CallbackInfo ci) {
        if (ClientRtsController.get().isOperationMode()) {
            Input self = (Input) (Object) this;
            float factor = slowDown ? 0.3F : 1.0F;
            self.forwardImpulse *= factor;
            self.leftImpulse *= factor;
            ci.cancel();
        }
    }
}