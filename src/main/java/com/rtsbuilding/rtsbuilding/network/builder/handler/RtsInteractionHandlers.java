package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.service.*;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.IWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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

    public static void handleInteract(C2SRtsInteractPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                ServiceRegistry.getInstance().interaction().interactTarget(
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

    public static void handleQuickDrop(C2SRtsQuickDropPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().transfer().quickDropLinkedItem(
                        serverPlayer,
                        payload.itemId(),
                        payload.amount(),
                        payload.dropX(),
                        payload.dropY(),
                        payload.dropZ());
            }
        });
    }

    public static void handleBreak(C2SRtsBreakPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsPlacedRecoveryService.breakPlaced(serverPlayer, payload.pos(), face, payload.allowAdjacentFallback());
            }
        });
    }

    public static void handleUndo(C2SRtsUndoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // Non-RTS mode undo requests are ignored
                if (!RtsCameraManager.isActive(serverPlayer)) return;
                ServerHistoryManager.executeUndo(serverPlayer);
            }
        });
    }

    public static void handleSubmitPending(C2SRtsSubmitPendingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().placement().submitPendingPlacement(serverPlayer);
            }
        });
    }

    public static void handleDeleteWorkflow(C2SRtsDeleteWorkflowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsTaskEngine.INSTANCE.cancelWorkflowTask(serverPlayer, payload.workflowEntryId());
                IWorkflowEngine engine = RtsWorkflowEngine.getInstance();
                engine.deleteWorkflow(serverPlayer, payload.workflowEntryId());
            }
        });
    }

    public static void handleSetWorkflowProtected(C2SRtsSetWorkflowProtectedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                IWorkflowEngine engine = RtsWorkflowEngine.getInstance();
                engine.setWorkflowProtected(
                        serverPlayer,
                        payload.workflowEntryId(),
                        payload.protectedWorkflow());
            }
        });
    }

    public static void handleScanResumePlacement(C2SRtsScanResumePlacementPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(serverPlayer);
                if (session != null) {
                    RtsResumeScanResult result = RtsPendingPlacementService.scanPendingJob(serverPlayer, session, payload.workflowEntryId());
                    if (result != null) {
                        RtsClientboundPackets.sendToPlayer(serverPlayer, new S2CRtsResumePlacementScanPayload(
                                result.itemId(), result.itemLabel(),
                                result.totalRemaining(), result.alreadyPlacedCount(),
                                result.conflictCount(), result.availableItems(),
                                result.neededItems(), result.missingItems(),
                                result.workflowEntryId()));
                    }
                }
            }
        });
    }

    public static void handleResumePlacementAction(C2SRtsResumePlacementActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                int entryId = payload.workflowEntryId();
                // 检查是否是蓝图工作流——蓝图没有 PlaceBatchJob，需走独立恢复路径
                RtsWorkflowStatus status = RtsWorkflowEngine.getInstance().getProgress(serverPlayer, entryId);
                if (status.isActive() && status.type() == RtsWorkflowType.BLUEPRINT_BUILD) {
                    RtsBlueprintJobService.resumeBlueprintWorkflow(serverPlayer, entryId);
                    return;
                }
                // 范围放置：使用 session 中的 PlaceBatchJob 恢复
                RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(serverPlayer);
                if (session != null) {
                    RtsPendingPlacementService.resumeWithStrategy(serverPlayer, session, payload.strategy(), entryId);
                }
            }
        });
    }

    public static void handlePauseWorkflow(C2SRtsPauseWorkflowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                int entryId = payload.entryId();
                RtsWorkflowEngine engine = RtsWorkflowEngine.getInstance();
                RtsWorkflowStatus status = engine.getProgress(serverPlayer, entryId);
                if (!status.isActive()) return;

                engine.from(serverPlayer, entryId).ifPresent(token -> {
                    if (status.suspended()) {
                        // 挂起任务必须先恢复真实 TaskStore revision；工作流由 tick 末单向投影。
                        RtsTaskEngine.INSTANCE.setWorkflowPaused(serverPlayer, entryId, false);
                        serverPlayer.displayClientMessage(
                                Component.translatable("message.rtsbuilding.workflow.resumed"),
                                true);
                    } else if (token.isPaused()) {
                        RtsTaskEngine.INSTANCE.setWorkflowPaused(serverPlayer, entryId, false);
                        serverPlayer.displayClientMessage(
                                Component.translatable("message.rtsbuilding.workflow.thread_resumed"),
                                true);
                    } else {
                        RtsTaskEngine.INSTANCE.setWorkflowPaused(serverPlayer, entryId, true);
                        serverPlayer.displayClientMessage(
                                Component.translatable("message.rtsbuilding.workflow.paused"),
                                true);
                    }
                });
            }
        });
    }

    public static void handleScanBlueprintResume(C2SRtsScanBlueprintResumePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                int entryId = payload.workflowEntryId();
                var scan = RtsBlueprintJobService.scanBlueprintMaterials(serverPlayer, entryId);
                if (scan != null) {
                    RtsClientboundPackets.sendToPlayer(serverPlayer, new S2CRtsBlueprintResumeScanPayload(
                            scan.itemIds(), scan.itemLabels(),
                            scan.required(), scan.available(),
                            entryId, scan.completedCount(), scan.totalCount()));
                }
            }
        });
    }
}
