package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper.JeiOverlayIngredient;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsClientInputGate {
    private RtsClientInputGate() {}

    private static boolean isRtsEnabled() {
        CameraModule cam = RtsClientKernel.get().module(CameraModule.class);
        return cam != null && cam.getState().isEnabled();
    }

    
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!isRtsEnabled()) return;

        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)
                || event.getName().equals(VanillaGuiLayers.HOTBAR)
                || event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)) {
            event.setCanceled(true);
        }
    }

    
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (isRtsEnabled()) {
            event.setCanceled(true);
        }
    }

    
    
    

    public static List<Rect2i> getJeiOverlayExtraAreas(Screen screen) {
        return List.of();
    }

    public static JeiOverlayIngredient getJeiOverlayIngredientUnderMouse(double mouseX, double mouseY) {
        return null;
    }
}
