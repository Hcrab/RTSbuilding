package com.rtsbuilding.rtsbuilding.network.progression.handler;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsBeginHomeSelectionPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsQuestDetectPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsRequestProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetHomePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetSurvivalProgressionPayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Forge 1.20.1 的生存进度与家园网络适配层。
 *
 * <p>这里只负责拆包、权限门槛和切回服务端线程；业务状态仍由与主线相同的
 * progression、plugin 与 camera 服务维护。</p>
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

    public static void handleSetSurvivalProgression(
            C2SRtsSetSurvivalProgressionPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
                Config.setSurvivalProgressionEnabled(payload.enabled());
                serverPlayer.server.getPlayerList().getPlayers().forEach(player -> {
                    RtsPluginService.syncToPlayer(player);
                    RtsProgressionManager.syncToPlayer(player);
                });
            }
        });
    }

    public static void handleSetHome(C2SRtsSetHomePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && RtsProgressionManager.commitHome(serverPlayer, payload.pos())) {
                RtsCameraManager.restartNormalFromHomeSelection(serverPlayer);
            }
        });
    }

    public static void handleBeginHomeSelection(
            C2SRtsBeginHomeSelectionPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCameraManager.startHomeSelectionFromPanel(serverPlayer);
            }
        });
    }

    public static void handleRequestProgressionState(
            C2SRtsRequestProgressionStatePayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsProgressionManager.syncToPlayer(serverPlayer);
            }
        });
    }
}
