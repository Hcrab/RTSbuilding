package com.rtsbuilding.rtsbuilding.network.progression.handler;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateRequest;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import com.rtsbuilding.rtsbuilding.network.progression.*;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side C2S adapter for quest detect and RTS-home actions.
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

    public static void handleSetSurvivalProgression(C2SRtsSetSurvivalProgressionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && Config.canEditServerConfig(serverPlayer)) {
                int revision = Config.currentServerSettings(true).revision();
                RtsServerConfigUpdateResult result = Config.applyServerConfig(
                        new RtsServerConfigUpdateRequest(revision, java.util.List.of(
                                new RtsServerConfigChange(
                                        RtsServerConfigChange.Key.ENABLE_SURVIVAL_PROGRESSION,
                                        new RtsServerConfigChange.BooleanValue(payload.enabled())))),
                        serverPlayer);
                if (!result.succeeded()) {
                    RtsbuildingMod.LOGGER.warn("RTS 生存进度配置未保存：{}", result.message());
                    return;
                }
                serverPlayer.server.getPlayerList().getPlayers().forEach(player -> {
                    RtsPluginService.syncToPlayer(player);
                    RtsProgressionManager.syncToPlayer(player);
                });
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
