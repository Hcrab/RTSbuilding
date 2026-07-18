package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Jade 的信息面板在 RTS BuilderScreen 上层渲染。
 * <p>
 * Jade 的 {@code shouldShowAfterGui} 方法决定哪些 Screen 可以让 Jade 面板
 * 渲染在 GUI 上层（Post 阶段）。默认仅白名单 {@code ChatScreen} 和
 * {@code BaseOptionsScreen}。此处将 {@link BuilderScreen} 加入白名单，
 * 使 Jade 面板在 RTS UI 上方可见。
 * <p>
 * 使用 {@code @Pseudo} 标记：Jade 未安装时此 Mixin 静默跳过。
 */
@Pseudo
@Mixin(targets = "snownee.jade.util.ClientProxy", remap = false)
public class JadeClientProxyMixin {

    @Inject(method = "shouldShowAfterGui", at = @At("RETURN"), cancellable = true, remap = false)
    private static void rtsbuilding$builderScreenAfterGui(
            Minecraft mc, Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof BuilderScreen) {
            cir.setReturnValue(true);
        }
    }
}
