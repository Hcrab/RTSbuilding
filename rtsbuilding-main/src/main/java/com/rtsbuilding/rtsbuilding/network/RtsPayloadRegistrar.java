package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBuilderPackets;
import com.rtsbuilding.rtsbuilding.network.camera.RtsCameraPackets;
import com.rtsbuilding.rtsbuilding.network.feedback.RtsFeedbackPackets;
import com.rtsbuilding.rtsbuilding.network.handler.ServerActionHandler;
import com.rtsbuilding.rtsbuilding.network.message.C2SAction;
import com.rtsbuilding.rtsbuilding.network.message.C2SCameraPosePayload;
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

        // ── Legacy S2C-only domain registrations (server→client) ──
        RtsCameraPackets.register(registrar);
        RtsStoragePackets.register(registrar);
        RtsBuilderPackets.register(registrar);
        RtsFeedbackPackets.register(registrar);
        BlueprintPayloadRegistrar.register(registrar);
    }
}
