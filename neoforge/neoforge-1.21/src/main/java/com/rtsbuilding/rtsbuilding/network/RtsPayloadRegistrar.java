package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBuilderPackets;
import com.rtsbuilding.rtsbuilding.network.camera.RtsCameraPackets;
import com.rtsbuilding.rtsbuilding.network.craft.RtsCraftPackets;
import com.rtsbuilding.rtsbuilding.network.feedback.RtsFeedbackPackets;
import com.rtsbuilding.rtsbuilding.network.handler.ServerActionHandler;
import com.rtsbuilding.rtsbuilding.network.message.C2SAction;
import com.rtsbuilding.rtsbuilding.network.message.C2SCameraPosePayload;
import com.rtsbuilding.rtsbuilding.network.message.S2CProgress;
import com.rtsbuilding.rtsbuilding.network.message.S2CStateUpdate;
import com.rtsbuilding.rtsbuilding.network.pathfinding.RtsPathfindingPackets;
import com.rtsbuilding.rtsbuilding.network.plugin.RtsPluginPackets;
import com.rtsbuilding.rtsbuilding.network.progression.RtsProgressionPackets;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStoragePackets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsPayloadRegistrar {
    private RtsPayloadRegistrar() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ── Unified C2S: single channel for all client actions ──
        registrar.playToServer(C2SAction.TYPE, C2SAction.STREAM_CODEC, ServerActionHandler::handle);

        // ── High-frequency C2S: camera pose (dedicated payload, no NBT) ──
        registrar.playToServer(C2SCameraPosePayload.TYPE, C2SCameraPosePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager.updateCameraPose(
                                sp, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
                    }
                }));

        // ── Generic S2C channels ──
        registrar.playToClient(S2CStateUpdate.TYPE, S2CStateUpdate.STREAM_CODEC,
                (p, ctx) -> com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleStateUpdate(p));
        registrar.playToClient(S2CProgress.TYPE, S2CProgress.STREAM_CODEC,
                (p, ctx) -> com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleProgress(p));

        // ── Legacy S2C-only domain registrations (server→client) ──
        RtsCameraPackets.register(registrar);
        RtsStoragePackets.register(registrar);
        RtsBuilderPackets.register(registrar);
        RtsCraftPackets.register(registrar);
        RtsProgressionPackets.register(registrar);
        RtsPluginPackets.register(registrar);
        RtsFeedbackPackets.register(registrar);
        RtsPathfindingPackets.register(registrar);
        BlueprintPayloadRegistrar.register(registrar);
    }
}
