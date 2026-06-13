package com.rtsbuilding.rtsbuilding.network.camera.handler;

import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsCameraMovePayload;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsToggleCameraPayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side C2S adapter for RTS camera input.
 *
 * Keep camera authority and session rules in RtsCameraManager; this layer should
 * only unwrap payloads and enqueue work on the server thread.
 */
public final class RtsCameraNetworkHandlers {
    private RtsCameraNetworkHandlers() {
    }

    public static void handleToggle(C2SRtsToggleCameraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCameraManager.toggle(serverPlayer, payload.startAtPlayerHead());
            }
        });
    }

    public static void handleMove(C2SRtsCameraMovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCameraManager.move(
                        serverPlayer,
                        payload.forward(),
                        payload.strafe(),
                        payload.vertical(),
                        payload.panX(),
                        payload.panY(),
                        payload.rotateX(),
                        payload.rotateY(),
                        payload.scroll(),
                        payload.rotateSteps(),
                        payload.fast());
            }
        });
    }
}
