package com.rtsbuilding.rtsbuilding.network.builder.handler;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 服务端分片坐标批次的有界重组器。
 *
 * <p>它只重组值，不执行世界操作。每位玩家最多保留少量未完成提交，过期、冲突、
 * 重复越界或总数不符的提交都会被丢弃，避免恶意客户端用残缺分片长期占用内存。</p>
 */
public final class RtsPositionBatchAssembler1122 {
    private static final long EXPIRE_NANOS = 30_000_000_000L;
    private static final int MAX_PENDING_PER_PLAYER = 8;
    private static final Map<Key, Assembly> PENDING = new HashMap<Key, Assembly>();

    private RtsPositionBatchAssembler1122() {
    }

    public static synchronized List<BlockPos> accept(UUID playerId, String kind, int submissionId,
            int chunkIndex, int chunkCount, int totalPositions, int maxTotal,
            String metadataSignature, List<BlockPos> positions) {
        long now = System.nanoTime();
        expire(now);
        if (playerId == null || kind == null || metadataSignature == null || positions == null
                || positions.isEmpty() || totalPositions <= 0 || totalPositions > maxTotal
                || chunkIndex < 0 || chunkCount <= 0 || chunkIndex >= chunkCount) return null;

        Key key = new Key(playerId, kind, submissionId);
        Assembly assembly = PENDING.get(key);
        if (assembly == null) {
            evictOldestIfFull(playerId);
            assembly = new Assembly(chunkCount, totalPositions, metadataSignature, now);
            PENDING.put(key, assembly);
        } else if (assembly.chunkCount != chunkCount
                || assembly.totalPositions != totalPositions
                || !assembly.metadataSignature.equals(metadataSignature)) {
            PENDING.remove(key);
            return null;
        }

        List<BlockPos> copy = Collections.unmodifiableList(new ArrayList<BlockPos>(positions));
        List<BlockPos> previous = assembly.chunks.get(chunkIndex);
        if (previous != null && !previous.equals(copy)) {
            PENDING.remove(key);
            return null;
        }
        if (previous == null) {
            assembly.chunks.put(chunkIndex, copy);
            assembly.receivedPositions += copy.size();
        }
        assembly.lastTouchedNanos = now;
        if (assembly.chunks.size() != chunkCount) return null;

        List<BlockPos> merged = new ArrayList<BlockPos>(totalPositions);
        for (int index = 0; index < chunkCount; index++) {
            List<BlockPos> chunk = assembly.chunks.get(index);
            if (chunk == null) return null;
            merged.addAll(chunk);
        }
        PENDING.remove(key);
        return merged.size() == totalPositions
                ? Collections.unmodifiableList(merged) : null;
    }

    public static synchronized void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        PENDING.keySet().removeIf(key -> playerId.equals(key.playerId));
    }

    public static synchronized void clearAll() {
        PENDING.clear();
    }

    private static void expire(long now) {
        Iterator<Map.Entry<Key, Assembly>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().lastTouchedNanos > EXPIRE_NANOS) iterator.remove();
        }
    }

    private static void evictOldestIfFull(UUID playerId) {
        Key oldestKey = null;
        long oldest = Long.MAX_VALUE;
        int count = 0;
        for (Map.Entry<Key, Assembly> entry : PENDING.entrySet()) {
            if (!playerId.equals(entry.getKey().playerId)) continue;
            count++;
            if (entry.getValue().lastTouchedNanos < oldest) {
                oldest = entry.getValue().lastTouchedNanos;
                oldestKey = entry.getKey();
            }
        }
        if (count >= MAX_PENDING_PER_PLAYER && oldestKey != null) PENDING.remove(oldestKey);
    }

    private static final class Assembly {
        private final int chunkCount;
        private final int totalPositions;
        private final String metadataSignature;
        private final Map<Integer, List<BlockPos>> chunks = new HashMap<Integer, List<BlockPos>>();
        private int receivedPositions;
        private long lastTouchedNanos;

        private Assembly(int chunkCount, int totalPositions, String metadataSignature, long now) {
            this.chunkCount = chunkCount;
            this.totalPositions = totalPositions;
            this.metadataSignature = metadataSignature;
            this.lastTouchedNanos = now;
        }
    }

    private static final class Key {
        private final UUID playerId;
        private final String kind;
        private final int submissionId;

        private Key(UUID playerId, String kind, int submissionId) {
            this.playerId = playerId;
            this.kind = kind;
            this.submissionId = submissionId;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return submissionId == key.submissionId
                    && playerId.equals(key.playerId) && kind.equals(key.kind);
        }

        @Override public int hashCode() {
            return Objects.hash(playerId, kind, submissionId);
        }
    }
}
