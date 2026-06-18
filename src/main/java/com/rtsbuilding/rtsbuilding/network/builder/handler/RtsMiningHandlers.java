package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltiminePayload;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side C2S adapter for RTS mining, ultimine, area mining, and area
 * destroy actions.
 *
 * <p>Keep tool leasing, item extraction, and undo recording in
 * RtsMiningService; this layer should only unwrap payloads and enqueue work on
 * the server thread.
 */
public final class RtsMiningHandlers {
    private RtsMiningHandlers() {
    }

    public static void handleMine(C2SRtsMinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                ServiceRegistry.getInstance().mining().mine(
                        serverPlayer,
                        payload.pos(),
                        face,
                        payload.start(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.allowPlacedBlockRecovery(),
                        payload.toolProtectionEnabled());
            }
        });
    }

    public static void handleUltimine(C2SRtsUltiminePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                ServiceRegistry.getInstance().mining().startUltimine(
                        serverPlayer,
                        payload.pos(),
                        face,
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.limit(),
                        payload.mode(),
                        payload.toolProtectionEnabled());
            }
        });
    }

    public static void handleAreaMine(C2SRtsAreaMinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().mining().areaMine(
                        serverPlayer,
                        payload.minX(), payload.maxX(),
                        payload.minY(), payload.maxY(),
                        payload.minZ(), payload.maxZ(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.shapeType(),
                        payload.fillType(),
                        payload.toolProtectionEnabled());
            }
        });
    }

    public static void handleAreaDestroy(C2SRtsAreaDestroyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().mining().areaDestroy(
                        serverPlayer,
                        payload.positions(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.toolProtectionEnabled());
            }
        });
    }
}
