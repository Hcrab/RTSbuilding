package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyFragmentPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyTracePayload;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Forge 分片请求的玩家隔离表；完成或超时后释放坐标内存。 */
public final class RtsAreaDestroyFragmentNetwork {
    public static final int MAX_TRACKED_PLAYERS = 256;
    public static final int MAX_PENDING_TARGETS = 8 * MiningLimits.MAX_VOLUME;

    private static final Map<UUID, RtsAreaDestroyFragmentReassembler> BY_PLAYER = new LinkedHashMap<>();

    private RtsAreaDestroyFragmentNetwork() {
    }

    public static synchronized RtsAreaDestroyFragmentReassembler.Result accept(
            UUID playerId, C2SRtsAreaDestroyFragmentPayload fragment, long nowTick) {
        if (playerId == null) return rejected("player identity is missing");
        int retainedTargets = BY_PLAYER.values().stream()
                .mapToInt(RtsAreaDestroyFragmentReassembler::pendingTargetCount).sum();
        int incoming = fragment == null || fragment.positions() == null ? 0 : fragment.positions().size();
        if (incoming > MAX_PENDING_TARGETS - retainedTargets) return rejected("area destroy transfer memory budget is busy");
        RtsAreaDestroyFragmentReassembler reassembler = BY_PLAYER.get(playerId);
        if (reassembler == null) {
            if (BY_PLAYER.size() >= MAX_TRACKED_PLAYERS) return rejected("too many players with area destroy network state");
            reassembler = new RtsAreaDestroyFragmentReassembler();
            BY_PLAYER.put(playerId, reassembler);
        }
        return reassembler.accept(fragment, nowTick);
    }

    public static synchronized void clear(UUID playerId) {
        if (playerId != null) BY_PLAYER.remove(playerId);
    }

    public static synchronized void clearAll() {
        BY_PLAYER.clear();
    }

    public static synchronized int trackedPlayerCount() {
        return BY_PLAYER.size();
    }

    public static synchronized int pendingRequestCount(UUID playerId) {
        RtsAreaDestroyFragmentReassembler reassembler = BY_PLAYER.get(playerId);
        return reassembler == null ? 0 : reassembler.pendingCount();
    }

    public static synchronized int expireAll(long nowTick) {
        return expireAll(nowTick, owner -> { });
    }

    public static synchronized int expireAll(long nowTick, java.util.function.Consumer<UUID> onExpired) {
        int removed = 0;
        Iterator<Map.Entry<UUID, RtsAreaDestroyFragmentReassembler>> iterator = BY_PLAYER.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            RtsAreaDestroyFragmentReassembler reassembler = entry.getValue();
            int expired = reassembler.expire(nowTick);
            removed += expired;
            if (expired > 0) onExpired.accept(entry.getKey());
            if (reassembler.isEmpty()) iterator.remove();
        }
        return removed;
    }

    private static RtsAreaDestroyFragmentReassembler.Result rejected(String reason) {
        return new RtsAreaDestroyFragmentReassembler.Result(
                RtsAreaDestroyFragmentReassembler.Status.REJECTED,
                (C2SRtsAreaDestroyTracePayload) null, reason, 0, 0);
    }
}
