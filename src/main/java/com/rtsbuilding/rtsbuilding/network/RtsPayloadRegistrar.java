package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.network.BlueprintPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBuilderPackets;
import com.rtsbuilding.rtsbuilding.network.camera.RtsCameraPackets;
import com.rtsbuilding.rtsbuilding.network.craft.RtsCraftPackets;
import com.rtsbuilding.rtsbuilding.network.feedback.RtsFeedbackPackets;
import com.rtsbuilding.rtsbuilding.network.progression.RtsProgressionPackets;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStoragePackets;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class RtsPayloadRegistrar {
    private RtsPayloadRegistrar() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        RtsCameraPackets.register(registrar);
        RtsStoragePackets.register(registrar);
        RtsBuilderPackets.register(registrar);
        RtsCraftPackets.register(registrar);
        RtsProgressionPackets.register(registrar);
        RtsFeedbackPackets.register(registrar);
        BlueprintPayloadRegistrar.register(registrar);
    }
}
