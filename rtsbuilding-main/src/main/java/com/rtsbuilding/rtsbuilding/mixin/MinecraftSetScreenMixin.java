package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
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
        Minecraft mc = (Minecraft) (Object) this;
        Screen current = mc.screen;

        if (newScreen == null && current instanceof BuilderScreen) {
            RtsbuildingMod.LOGGER.debug("RTS: BuilderScreen closing via setScreen(null)");
        }
        if (newScreen == null) return;
        if (newScreen instanceof BuilderScreen) return;
        if (newScreen instanceof AbstractContainerScreen<?>) {
            RtsbuildingMod.LOGGER.debug("RTS: setScreen({}) while current={}; intercepted={}",
                    newScreen.getClass().getSimpleName(),
                    current == null ? "null" : current.getClass().getSimpleName(),
                    current instanceof BuilderScreen);
        }
        if (!(current instanceof BuilderScreen builderScreen)) {
            CameraModule cam = RtsClientKernel.get().module(CameraModule.class);
            if (cam == null || !cam.getState().isEnabled()) return;
            if (!(newScreen instanceof AbstractContainerScreen<?> orphanContainer)) return;
            try {
                RtsbuildingMod.LOGGER.info("RTS: Reopening BuilderScreen for container {} (current screen was {})",
                        orphanContainer.getClass().getSimpleName(),
                        current == null ? "null" : current.getClass().getSimpleName());
                mc.setScreen(new BuilderScreen());
                if (mc.screen instanceof BuilderScreen reopened) {
                    reopened.showContainerScreen(orphanContainer);
                }
            } catch (Throwable throwable) {
                RtsbuildingMod.LOGGER.error("RTS: Failed to reopen BuilderScreen for container overlay; cancelling vanilla screen switch.",
                        throwable);
            } finally {
                ci.cancel();
            }
            return;
        }
        if (!(newScreen instanceof AbstractContainerScreen<?> containerScreen)) return;

        try {
            builderScreen.showContainerScreen(containerScreen);
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.error("RTS: Failed to show container overlay {} in BuilderScreen; cancelling vanilla screen switch.",
                    containerScreen.getClass().getSimpleName(), throwable);
        } finally {
            ci.cancel();
        }
    }
}
