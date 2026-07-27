package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.rtsbuilding.rtsbuilding.server.RtsServer;

/**
 * FTB Quest detection service, integrated with FTB Quests mod.
 *
 * <p>This service periodically scans the player's FTB Quest completion,
 * pushing detection phase and progress data to the client UI via network packets.
 * All methods are {@code static}, the class itself is a non-instantiable utility class.
 *
 * <p><b>Core methods:</b>
 * <ul>
 *   <li>{@link #detectQuests(ServerPlayer, byte)} — Externally triggered quest detection entry point,
 *       calls {@link #runQuestDetect} after getting or creating a session</li>
 *   <li>{@link #runQuestDetect(ServerPlayer, RtsStorageSession, boolean)} —
 *       Runs the actual detection scan, throttled by cooldown {@value #QUEST_DETECT_COOLDOWN_TICKS} ticks;
 *       {@code force=true} ignores cooldown and pushes full detection status</li>
 *   <li>{@link #sendQuestDetectStatus(ServerPlayer, byte, int, int, int)} —
 *       Sends detection status packet to client, containing phase, scanned count, total count, and newly completed count</li>
 * </ul>
 *
 * <p><b>Detection phases:</b> Represented by the phase field of {@link S2CRtsQuestDetectStatusPayload}:
 * <ul>
 *   <li>PHASE_STARTED — Detection started</li>
 *   <li>PHASE_COMPLETE — Detection complete with available quests</li>
 *   <li>PHASE_UNAVAILABLE — Unavailable (mod not loaded or no quests)</li>
 *   <li>PHASE_ERROR — Error during detection</li>
 * </ul>
 */
public final class QuestService {

    private static final long QUEST_DETECT_COOLDOWN_TICKS = 100L;

    private QuestService() {
    }

    public static void detectQuests(ServerPlayer player, byte mode) {
        RtsStorageSession session = RtsServer.get().session().getOrCreate(player);
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        runQuestDetect(player, session, true);
    }

    /**
     * Runs the quest detection scan.
     *
     * @param player  Target player
     * @param session Current RTS session
     * @param force   Force scan (ignores cooldown)
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
        PacketDistributor.sendToPlayer(
                player,
                new S2CRtsQuestDetectStatusPayload(
                        phase,
                        Math.max(0, scannedTasks),
                        Math.max(0, totalTasks),
                        Math.max(0, completedTasks)));
    }
}
