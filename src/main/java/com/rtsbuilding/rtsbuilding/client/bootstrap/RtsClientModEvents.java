package com.rtsbuilding.rtsbuilding.client.bootstrap;


import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RtsClientModEvents {
    private RtsClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RtsbuildingMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        RtsbuildingMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RtsbuildingMod.RTS_CAMERA_ENTITY.get(), RtsCameraEntityRenderer::new);
    }
}
