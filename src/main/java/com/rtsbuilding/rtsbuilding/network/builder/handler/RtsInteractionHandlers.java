package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsInteractionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.service.RtsTransferService;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side C2S adapter for RTS interaction, break, quick-drop, and undo
 * actions.
 *
 * <p>Keep interaction behavior, block recovery, and undo orchestration in
 * their respective services; this layer should only unwrap payloads and enqueue
 * work on the server thread.
 */
public final class RtsInteractionHandlers {
    private RtsInteractionHandlers() {
    }

    public static void handleInteract(com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsInteractionService.interactTarget(
                        serverPlayer,
                        payload.entityId(),
                        payload.clickedPos(),
                        face,
                        payload.hitX(),
                        payload.hitY(),
                        payload.hitZ(),
                        payload.sourceType(),
                        payload.toolSlot(),
                        payload.itemId(),
                        payload.rayOriginX(),
                        payload.rayOriginY(),
                        payload.rayOriginZ(),
                        payload.rayDirX(),
                        payload.rayDirY(),
                        payload.rayDirZ());
            }
        });
    }

    public static void handleQuickDrop(com.rtsbuilding.rtsbuilding.network.builder.C2SRtsQuickDropPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsTransferService.quickDropLinkedItem(
                        serverPlayer,
                        payload.itemId(),
                        payload.amount(),
                        payload.dropX(),
                        payload.dropY(),
                        payload.dropZ());
            }
        });
    }

    public static void handleBreak(com.rtsbuilding.rtsbuilding.network.builder.C2SRtsBreakPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsPlacedRecoveryService.breakPlaced(serverPlayer, payload.pos(), face, payload.allowAdjacentFallback());
            }
        });
    }

    public static void handleUndo(com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUndoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // Non-RTS mode undo requests are ignored
                if (!RtsCameraManager.isActive(serverPlayer)) return;
                ServerHistoryManager.executeUndo(serverPlayer);
            }
        });
    }
}
