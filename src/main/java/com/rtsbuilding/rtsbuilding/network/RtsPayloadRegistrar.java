package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBuilderPackets;
import com.rtsbuilding.rtsbuilding.network.camera.RtsCameraPackets;
import com.rtsbuilding.rtsbuilding.network.craft.RtsCraftPackets;
import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingPackets;
import com.rtsbuilding.rtsbuilding.network.feedback.RtsFeedbackPackets;
import com.rtsbuilding.rtsbuilding.network.pathfinding.RtsPathfindingPackets;
import com.rtsbuilding.rtsbuilding.network.plugin.RtsPluginPackets;
import com.rtsbuilding.rtsbuilding.network.progression.RtsProgressionPackets;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStoragePackets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Main registration entry point for non-blueprint RTS packets.
 *
 * The protocol version, payload ids, codecs, and packet directions are still
 * owned by the individual payload records. The domain registrars below are only
 * a readability layer, so moving a payload between them must not change the
 * wire protocol.
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
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
        RtsCullingPackets.register(registrar);
        RtsProgressionPackets.register(registrar);
        RtsPluginPackets.register(registrar);
        RtsFeedbackPackets.register(registrar);
        RtsPathfindingPackets.register(registrar);
        BlueprintPayloadRegistrar.register(registrar);
    }
}
