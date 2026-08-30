package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.diagnostic.RtsCameraOwnershipDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端摄像机实体 setter 的最薄观察点。
 *
 * <p>Mixin 只把入口参数交给客户端诊断组件。它不取消调用、不解析调用栈、
 * 不识别第三方类名，也不替换目标，因此不会改变任何模组的摄像机业务逻辑。
 * 该类只被客户端 mixin 配置加载，专用服务端不会解析 Minecraft 客户端或诊断类。</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftCameraEntityMixin {
    @Inject(method = "setCameraEntity", at = @At("HEAD"))
    private void rtsbuilding$observeCameraEntitySetter(Entity target, CallbackInfo callback) {
        RtsCameraOwnershipDiagnostics.observeExternalCameraSetter(target);
    }
}
