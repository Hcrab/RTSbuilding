package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB 任务（Quests）检测服务，与 FTB Quests 模组集成。
 *
 * <p>此服务周期性扫描玩家的 FTB Quests 完成情况，
 * 通过网络包将检测相位和进度数据推送至客户端 UI。
 * 所有方法均为 {@code static}，类本身为不可实例化的工具类。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #detectQuests(ServerPlayer, byte)} — 外部触发的任务检测入口，
 *       获取或创建会话后调用 {@link #runQuestDetect}</li>
 *   <li>{@link #runQuestDetect(ServerPlayer, RtsStorageSession, boolean)} —
 *       运行实际检测扫描，受冷却时间 {@value #QUEST_DETECT_COOLDOWN_TICKS} tick 限制；
 *       {@code force=true} 时无视冷却并推送完整检测状态</li>
 *   <li>{@link #sendQuestDetectStatus(ServerPlayer, byte, int, int, int)} —
 *       向客户端发送检测状态数据包，包含相位、已扫描数、总数和新增完成数</li>
 * </ul>
 *
 * <p><b>检测相位：</b>通过 {@link S2CRtsQuestDetectStatusPayload} 的 phase 字段表示：
 * <ul>
 *   <li>PHASE_STARTED — 检测开始</li>
 *   <li>PHASE_COMPLETE — 检测完成且有可用任务</li>
 *   <li>PHASE_UNAVAILABLE — 不可用（模组未加载或无任务）</li>
 *   <li>PHASE_ERROR — 检测过程出错</li>
 * </ul>
 */
public final class QuestService {

    private static final long QUEST_DETECT_COOLDOWN_TICKS = 100L;

    private QuestService() {
    }

    public static void detectQuests(ServerPlayer player, byte mode) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getOrCreate(player);
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        runQuestDetect(player, session, true);
    }

    /**
     * 运行任务检测扫描。
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
        if (!force && now < session.transfer.nextQuestDetectTick) {
            return;
        }
        session.transfer.nextQuestDetectTick = now + QUEST_DETECT_COOLDOWN_TICKS;
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
        RtsClientboundPackets.sendToPlayer(
                player,
                new S2CRtsQuestDetectStatusPayload(
                        phase,
                        Math.max(0, scannedTasks),
                        Math.max(0, totalTasks),
                        Math.max(0, completedTasks)));
    }
}
