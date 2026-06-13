package com.rtsbuilding.rtsbuilding.network.progression.handler;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.progression.*;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side C2S adapter for progression config, unlock, quest, and home
 * selection actions.
 *
 * <p>Keep unlock persistence and shared progression behavior in
 * RtsProgressionManager; this layer should only unwrap payloads, enforce the
 * small permission gates, and enqueue work on the server thread.
 */
public final class RtsProgressionNetworkHandlers {
    private RtsProgressionNetworkHandlers() {
    }

    public static void handleQuestDetect(C2SRtsQuestDetectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                QuestService.detectQuests(serverPlayer, payload.mode());
            }
        });
    }

    public static void handleUnlockProgressionNode(C2SRtsUnlockProgressionNodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsProgressionManager.unlockNode(serverPlayer, payload.nodeId()).notifyPlayer(serverPlayer);
            }
        });
    }

    public static void handleSetSurvivalProgression(C2SRtsSetSurvivalProgressionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
                Config.setSurvivalProgressionEnabled(payload.enabled());
                serverPlayer.server.getPlayerList().getPlayers().forEach(RtsProgressionManager::syncToPlayer);
            }
        });
    }

    public static void handleSetProgressionCost(C2SRtsSetProgressionCostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.hasPermissions(2)
                    && RtsProgressionNodes.contains(payload.nodeId())) {
                Config.setProgressionCostOverride(payload.nodeId().getPath(), payload.costsText());
                serverPlayer.server.getPlayerList().getPlayers().forEach(RtsProgressionManager::syncToPlayer);
            }
        });
    }

    public static void handleSetHome(C2SRtsSetHomePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (RtsProgressionManager.commitHome(serverPlayer, payload.pos())) {
                    RtsCameraManager.restartNormalFromHomeSelection(serverPlayer);
                }
            }
        });
    }

    public static void handleBeginHomeSelection(C2SRtsBeginHomeSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCameraManager.startHomeSelectionFromPanel(serverPlayer);
            }
        });
    }

    public static void handleRequestProgressionState(C2SRtsRequestProgressionStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsProgressionManager.syncToPlayer(serverPlayer);
            }
        });
    }
}
