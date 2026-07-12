package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsInteractionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.service.RtsResumeScanResult;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsTransferService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanResumePlacementPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSetWorkflowProtectedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

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

    public static void handlePauseWorkflow(com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPauseWorkflowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            RtsWorkflowEngine engine = RtsWorkflowEngine.getInstance();
            if (payload.entryId() < 0) {
                engine.pauseAllActive(serverPlayer.getUUID(), true);
                return;
            }
            var status = engine.getProgress(serverPlayer, payload.entryId());
            if (status.suspended()) {
                var session = RtsSessionService.getIfPresent(serverPlayer);
                sendResumePlacementScan(serverPlayer, session, payload.entryId());
                return;
            }
            engine.from(serverPlayer, payload.entryId()).ifPresent(token -> {
                if (token.isPaused()) {
                    if (token.unpause()) {
                        serverPlayer.displayClientMessage(
                                Component.translatable("message.rtsbuilding.workflow.resumed"), true);
                    }
                } else {
                    token.pause();
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.rtsbuilding.workflow.paused"), true);
                }
            });
        });
    }

    public static void handleSetWorkflowProtected(C2SRtsSetWorkflowProtectedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsWorkflowEngine.getInstance().setWorkflowProtected(
                        serverPlayer,
                        payload.workflowEntryId(),
                        payload.protectedWorkflow());
            }
        });
    }

    public static void handleScanResumePlacement(C2SRtsScanResumePlacementPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                sendResumePlacementScan(serverPlayer, RtsSessionService.getIfPresent(serverPlayer), payload.workflowEntryId());
            }
        });
    }

    public static void handleResumePlacementAction(C2SRtsResumePlacementActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            var session = RtsSessionService.getIfPresent(serverPlayer);
            if (RtsPendingPlacementService.resumeWithStrategy(
                    serverPlayer, session, payload.strategy(), payload.workflowEntryId())) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.rtsbuilding.workflow.resume_placement_success"), true);
            } else {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.rtsbuilding.workflow.no_pending_placement"), true);
            }
        });
    }

    public static void handleDeleteWorkflow(com.rtsbuilding.rtsbuilding.network.builder.C2SRtsDeleteWorkflowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsPlacementBatch.removePendingJob(RtsSessionService.getIfPresent(serverPlayer), payload.workflowEntryId());
                RtsWorkflowEngine.getInstance().deleteWorkflow(serverPlayer, payload.workflowEntryId());
            }
        });
    }

    private static void sendResumePlacementScan(ServerPlayer player, com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession session,
            int workflowEntryId) {
        RtsResumeScanResult result = RtsPendingPlacementService.scanPendingJob(player, session, workflowEntryId);
        if (result == null) {
            player.displayClientMessage(
                    Component.translatable("message.rtsbuilding.workflow.no_pending_placement"), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsResumePlacementScanPayload(
                result.itemId(),
                result.itemLabel(),
                result.totalRemaining(),
                result.alreadyPlacedCount(),
                result.conflictCount(),
                result.availableItems(),
                result.neededItems(),
                result.missingItems(),
                result.workflowEntryId()));
    }

}
