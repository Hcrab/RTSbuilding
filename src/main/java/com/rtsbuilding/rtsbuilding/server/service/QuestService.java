package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

/**
 * FTB 任务检测服???
 *
 * <p>周期性扫描玩家的 FTB Quests 完成情况??
 * 通过网络包将相位和进度推送至客户??UI??
 */
public final class QuestService {

    public static final QuestService INSTANCE = new QuestService();

    private static final long QUEST_DETECT_COOLDOWN_TICKS = 100L;

    private QuestService() {
    }

    public static void detectQuests(ServerPlayer player, byte mode) {
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        runQuestDetect(player, session, true);
    }

    /**
     * 运行任务检测扫???
     *
     * @param player  目标玩家
     * @param session 当前 RTS 会话
     * @param force   强制扫描（无视冷却）
     */
    public static void runQuestDetect(ServerPlayer player, RtsStorageSession session, boolean force) {
        if (player == null || session == null) {
            return;
        }
        if (!RtsFtbCompat.isDetectAvailable()) {
            if (force) {
                sendQuestDetectStatus(player, S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE, 0, 0, 0);
            }
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (!force && now < session.nextQuestDetectTick) {
            return;
        }
        session.nextQuestDetectTick = now + QUEST_DETECT_COOLDOWN_TICKS;
        if (force) {
            sendQuestDetectStatus(player, S2CRtsQuestDetectStatusPayload.PHASE_STARTED, 0, 0, 0);
        }
        RtsFtbCompat.QuestDetectResult result = RtsFtbCompat.detectNow(player);
        if (force) {
            byte phase = result.error()
                    ? S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                    : result.available()
                            ? S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE
                            : S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE;
            sendQuestDetectStatus(
                    player,
                    phase,
                    result.scannedTasks(),
                    result.scannedTasks(),
                    result.newlyCompletedTasks());
        }
    }

    public static void sendQuestDetectStatus(ServerPlayer player, byte phase,
            int scannedTasks, int totalTasks, int completedTasks) {
        PacketDistributor.sendToPlayer(
                player,
                new S2CRtsQuestDetectStatusPayload(
                        phase,
                        Math.max(0, scannedTasks),
                        Math.max(0, totalTasks),
                        Math.max(0, completedTasks)));
    }
}
