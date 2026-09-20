package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigWire;
import com.rtsbuilding.rtsbuilding.network.config.C2SRtsServerConfigQueryPayload;
import com.rtsbuilding.rtsbuilding.network.config.C2SRtsServerConfigUpdatePayload;
import com.rtsbuilding.rtsbuilding.network.config.S2CRtsServerConfigResultPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** NeoForge 配置权威链：当前 ServerPlayer -> 服务端校验/落盘 -> ACK/全服广播。 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsServerConfigNetwork {
    private static final Map<UUID, Long> SESSIONS = new ConcurrentHashMap<>();
    private RtsServerConfigNetwork() {
    }

    public static void handleQuery(C2SRtsServerConfigQueryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) handleQuery(player, payload.data()); });
    }

    public static void handleUpdate(C2SRtsServerConfigUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) handleUpdate(player, payload.data()); });
    }

    private static void handleQuery(ServerPlayer player, byte[] data) {
        try {
            RtsServerConfigWire.Request request = RtsServerConfigWire.decodeRequest(data);
            if (!request.isQuery()) return;
            long requestedSession = request.query().sessionId();
            long currentSession = SESSIONS.getOrDefault(player.getUUID(), 0L);
            // 同一 UUID 的旧连接不能用延迟查询把新连接的会话替换掉。
            if (currentSession != 0L && requestedSession != 0L && currentSession != requestedSession) {
                send(player, requestedSession, request.query().requestId(),
                        Config.currentServerSettings(canEdit(player)).readOnly(),
                        "configuration session superseded", RtsServerConfigUpdateResult.Status.INVALID);
                return;
            }
            long session = currentSession != 0L ? currentSession
                    : (requestedSession == 0L ? sessionFor(player) : requestedSession);
            SESSIONS.put(player.getUUID(), session);
            send(player, session, request.query().requestId(), Config.currentServerSettings(canEdit(player)));
        } catch (RuntimeException malformed) {
            RtsbuildingMod.LOGGER.debug("忽略无效的服务端配置查询包", malformed);
        }
    }

    private static void handleUpdate(ServerPlayer player, byte[] data) {
        RtsServerConfigWire.Update update;
        try {
            RtsServerConfigWire.Request request = RtsServerConfigWire.decodeRequest(data);
            if (request.isQuery()) return;
            update = request.update();
        } catch (RuntimeException malformed) {
            RtsbuildingMod.LOGGER.debug("忽略无效的服务端配置修改包", malformed);
            return;
        }
        long currentSession = SESSIONS.getOrDefault(player.getUUID(), 0L);
        if (currentSession == 0L || currentSession != update.sessionId()) {
            RtsServerConfigUpdateResult expired = new RtsServerConfigUpdateResult(
                    RtsServerConfigUpdateResult.Status.INVALID,
                    Config.currentServerSettings(canEdit(player)).readOnly(),
                    "configuration session expired");
            send(player, update.sessionId(), update.requestId(), expired.view(), expired.message(), expired.status());
            return;
        }
        RtsServerConfigUpdateResult result = Config.applyServerConfig(update.request(), player);
        send(player, currentSession, update.requestId(), result.view(), result.message(), result.status());
        if (result.status() == RtsServerConfigUpdateResult.Status.SAVE_FAILED) {
            RtsbuildingMod.LOGGER.warn("RTSBuilding 服务端配置保存失败：{}", result.message());
        }
        // 保存者已收到精确 ACK；其他在线玩家由下一安全 tick 的 pending 广播统一刷新。
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SESSIONS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Config.beginServerConfigRuntime();
        SESSIONS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 迁移在主线程消费，避免 ConfigWatcher 回调直接改变世界配置或递归 save。
        Config.consumePendingLegacyServerMigration();
        if (Config.pollServerConfigRuntimeChange()) broadcastCurrent(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        Config.endServerConfigRuntime();
        Config.clearRawConfigSnapshots();
    }

    public static boolean canEdit(ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.getServer();
        return Config.canEditServerConfig(player);
    }

    private static long sessionFor(ServerPlayer player) {
        long value = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits() ^ System.nanoTime();
        return value == 0L ? 1L : value;
    }

    private static void broadcastCurrent(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long session = SESSIONS.getOrDefault(player.getUUID(), 0L);
            if (session != 0L) send(player, session, 0, Config.currentServerSettings(canEdit(player)));
        }
    }

    private static void send(ServerPlayer player, long session, int requestId,
                             com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView view) {
        send(player, session, requestId, view, "", RtsServerConfigUpdateResult.Status.NO_CHANGE);
    }

    private static void send(ServerPlayer player, long session, int requestId,
                             com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView view,
                             String message, RtsServerConfigUpdateResult.Status status) {
        // 配置会话只暴露客户端确实消费的规则；服务端技术预算留在服务端文件与运行时。
        RtsServerConfigUpdateResult result = new RtsServerConfigUpdateResult(
                status, view.clientProjection(), message);
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsServerConfigResultPayload(
                RtsServerConfigWire.encodeResponse(session, requestId, result)));
    }
}
