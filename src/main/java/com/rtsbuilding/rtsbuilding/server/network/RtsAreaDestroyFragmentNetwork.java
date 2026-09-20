package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyFragmentPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyTracePayload;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 区域破坏分片的玩家隔离入口。
 *
 * <p>每个玩家拥有独立重组器，因此相同 requestId 也不会把两名玩家的坐标合并。
 * 表和每个重组器都有硬上限；登出/停服由 {@link RtsAreaDestroyFragmentEvents} 清理，
 * 不把临时网络状态塞进其他玩家生命周期类。</p>
 */
public final class RtsAreaDestroyFragmentNetwork {
    /** 防止异常连接数令玩家隔离表无界增长。 */
    public static final int MAX_TRACKED_PLAYERS = 256;

    /** 同时在途坐标的全服内存预算；收齐即释放，不限制已接纳的世界任务数量。 */
    public static final int MAX_PENDING_TARGETS =
            8 * com.rtsbuilding.rtsbuilding.common.mining.MiningLimits.MAX_VOLUME;

    private static final Map<UUID, RtsAreaDestroyFragmentReassembler> BY_PLAYER =
            new LinkedHashMap<>();

    private RtsAreaDestroyFragmentNetwork() {
    }

    public static synchronized RtsAreaDestroyFragmentReassembler.Result accept(
            UUID playerId, C2SRtsAreaDestroyFragmentPayload fragment, long nowTick) {
        if (playerId == null) {
            return rejected("player identity is missing");
        }
        int retainedTargets = BY_PLAYER.values().stream()
                .mapToInt(RtsAreaDestroyFragmentReassembler::pendingTargetCount).sum();
        if (fragment != null && retainedTargets > MAX_PENDING_TARGETS - fragment.positions().size()) {
            return rejected("area destroy transfer memory budget is busy");
        }
        RtsAreaDestroyFragmentReassembler reassembler = BY_PLAYER.get(playerId);
        if (reassembler == null) {
            if (BY_PLAYER.size() >= MAX_TRACKED_PLAYERS) {
                return rejected("too many players with area destroy network state");
            }
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

    /** 供专用事件或离线检查使用，不暴露内部可变表。 */
    public static synchronized int trackedPlayerCount() {
        return BY_PLAYER.size();
    }

    public static synchronized int pendingRequestCount(UUID playerId) {
        RtsAreaDestroyFragmentReassembler reassembler = BY_PLAYER.get(playerId);
        return reassembler == null ? 0 : reassembler.pendingCount();
    }

    /** 在没有新分片到达时也推进超时清理，避免临时坐标一直占用内存。 */
    public static synchronized int expireAll(long nowTick) {
        return expireAll(nowTick, owner -> { });
    }

    public static synchronized int expireAll(long nowTick, java.util.function.Consumer<UUID> onExpired) {
        int removed = 0;
        Iterator<Map.Entry<UUID, RtsAreaDestroyFragmentReassembler>> iterator =
                BY_PLAYER.entrySet().iterator();
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
