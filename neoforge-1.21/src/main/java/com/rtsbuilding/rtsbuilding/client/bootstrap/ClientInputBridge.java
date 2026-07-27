package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.RtsCraftTerminalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientInputBridge {

    private ClientInputBridge() {}

    private static RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }

    
    private static boolean shouldSkip() {
        return Minecraft.getInstance().screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onMouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onMouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onMouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onMouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (shouldSkip()) return;
        if (kernel().inputPipeline().onCharTyped((char) event.getCodePoint(), 0)) {
            event.setCanceled(true);
        }
    }

    
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        CameraModule cam = kernel().module(CameraModule.class);
        if (cam == null || !cam.getState().isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (!(current instanceof BuilderScreen builderScreen)) return;

        Screen newScreen = event.getScreen();
        
        if (newScreen instanceof BuilderScreen || newScreen instanceof RtsCraftTerminalScreen) return;

        
        if (newScreen instanceof AbstractContainerScreen<?> containerScreen) {
            RtsbuildingMod.LOGGER.debug("RTS: Intercepting {} as overlay in BuilderScreen",
                    containerScreen.getClass().getSimpleName());
            builderScreen.showContainerScreen(containerScreen);
            event.setCanceled(true);
        }
        
        
        
    }
}
