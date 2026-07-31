package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.RtsCraftTerminalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
abstract class MinecraftSetScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$onSetScreen(@Nullable Screen newScreen, CallbackInfo ci) {
        CameraModule cam = RtsClientKernel.get().module(CameraModule.class);
        if (cam == null || !cam.getState().isEnabled()) return;

        Minecraft mc = (Minecraft) (Object) this;
        Screen current = mc.screen;

        if (newScreen == null) return;
        if (newScreen instanceof BuilderScreen || newScreen instanceof RtsCraftTerminalScreen) return;
        if (!(current instanceof BuilderScreen builderScreen)) return;

        if (newScreen instanceof AbstractContainerScreen<?> containerScreen) {
            RtsbuildingMod.LOGGER.debug("RTS: Intercepting {} as overlay in BuilderScreen via mixin",
                    containerScreen.getClass().getSimpleName());
            builderScreen.showContainerScreen(containerScreen);
            ci.cancel();
        }
    }
}
