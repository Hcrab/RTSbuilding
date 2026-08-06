package com.rtsbuilding.rtsbuilding.server.service.bindings;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.refinedstorage.RtsRefinedStorageCompat;
import com.rtsbuilding.rtsbuilding.common.storage.RtsBatchStorageSelectionBounds;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsEndpointLeaseCache;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务端批量存储链接。
 *
 * <p>客户端只给出框选的两个角。服务端只读取已加载 Chunk 的方块实体索引，并对每个候选
 * 重新检查 RTS 操作范围、已加载状态、claim、处理器类型和链接上限。AE2/RS 使用网络对象
 * 的引用身份去重，不依赖不稳定的 {@code equals()} 或临时 IItemHandler 包装器。</p>
 */
public final class RtsBatchStorageBindingService {
    private RtsBatchStorageBindingService() {
    }

    public static RtsStorageBindings.UpdateResult linkLoadedStorages(EntityPlayerMP player,
            RtsStorageSession session, BlockPos first, BlockPos second, byte linkMode) {
        if (player == null || session == null) return RtsStorageBindings.UpdateResult.none();
        RtsBatchStorageSelectionBounds.Bounds bounds =
                RtsBatchStorageSelectionBounds.normalize(first, second);
        if (bounds == null) return RtsStorageBindings.UpdateResult.none();

        WorldServer level = player.getServerWorld();
        List<BlockPos> candidates = collectLoadedBlockEntities(level, bounds);
        Collections.sort(candidates, POSITION_ORDER);

        Map<NetworkKey, BlockPos> networks = new LinkedHashMap<NetworkKey, BlockPos>();
        Set<BlockPos> standalone = new HashSet<BlockPos>();
        for (BlockPos candidate : candidates) {
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, candidate)
                    || !RtsLinkedStorageBindingService.canLinkStorageTarget(player, candidate)) {
                continue;
            }
            NetworkKey network = networkKey(level, candidate);
            if (network != null) {
                if (!networks.containsKey(network)) networks.put(network, candidate.toImmutable());
            } else {
                standalone.add(RtsLinkedStorageBindingService.canonicalStoragePosition(level, candidate));
            }
        }

        Map<NetworkKey, List<LinkedStorageRef>> existingNetworks = collectExistingNetworks(level, session);
        List<Candidate> selected = new ArrayList<Candidate>(standalone.size() + networks.size());
        for (BlockPos pos : standalone) selected.add(new Candidate(pos, null));
        for (Map.Entry<NetworkKey, BlockPos> entry : networks.entrySet()) {
            selected.add(new Candidate(entry.getValue(), entry.getKey()));
        }
        Collections.sort(selected, new Comparator<Candidate>() {
            @Override public int compare(Candidate left, Candidate right) {
                return POSITION_ORDER.compare(left.pos, right.pos);
            }
        });

        boolean changed = false;
        for (Candidate candidate : selected) {
            if (candidate.network != null) {
                List<LinkedStorageRef> existing = existingNetworks.get(candidate.network);
                if (existing != null && !existing.isEmpty()) {
                    // 同一网络已经有代表终端。顺手收敛旧版留下的重复引用，但绝不点击式 toggle。
                    changed |= removeDuplicateNetworkRefs(session, existing, existing.get(0));
                    continue;
                }
            }
            if (session.linkedStorageInfo.size() >= RtsStorageBindings.MAX_LINKED_STORAGES) {
                break;
            }
            RtsStorageBindings.UpdateResult update =
                    RtsLinkedStorageBindingService.ensureStorageLinked(player, session, candidate.pos, linkMode);
            if (!update.saveSession()) continue;
            changed = true;
            if (candidate.network != null) {
                existingNetworks.put(candidate.network, Collections.singletonList(
                        new LinkedStorageRef(player.dimension, candidate.pos)));
            }
        }
        if (!changed) return RtsStorageBindings.UpdateResult.none();
        session.bdCache.handlerStale = true;
        session.bdCache.fluidHandlerStale = true;
        RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUniqueID());
        return RtsStorageBindings.UpdateResult.refreshFirst(true);
    }

    private static final Comparator<BlockPos> POSITION_ORDER = new Comparator<BlockPos>() {
        @Override public int compare(BlockPos left, BlockPos right) {
            int y = Integer.compare(left.getY(), right.getY());
            if (y != 0) return y;
            int x = Integer.compare(left.getX(), right.getX());
            return x != 0 ? x : Integer.compare(left.getZ(), right.getZ());
        }
    };

    private static List<BlockPos> collectLoadedBlockEntities(WorldServer level,
            RtsBatchStorageSelectionBounds.Bounds bounds) {
        if (level == null || bounds == null) return Collections.emptyList();
        List<BlockPos> result = new ArrayList<BlockPos>();
        int minChunkX = bounds.min().getX() >> 4;
        int maxChunkX = bounds.max().getX() >> 4;
        int minChunkZ = bounds.min().getZ() >> 4;
        int maxChunkZ = bounds.max().getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = level.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getTileEntityMap().keySet()) {
                    if (bounds.contains(pos)) result.add(pos.toImmutable());
                }
            }
        }
        return result;
    }

    private static Map<NetworkKey, List<LinkedStorageRef>> collectExistingNetworks(
            WorldServer level, RtsStorageSession session) {
        Map<NetworkKey, List<LinkedStorageRef>> result =
                new HashMap<NetworkKey, List<LinkedStorageRef>>();
        if (level == null || session == null) return result;
        for (LinkedStorageRef ref : new ArrayList<LinkedStorageRef>(session.linkedStorageInfo.getAll())) {
            if (ref.dimension() != level.provider.getDimension() || !level.isBlockLoaded(ref.pos())) continue;
            NetworkKey key = networkKey(level, ref.pos());
            if (key == null) continue;
            List<LinkedStorageRef> values = result.get(key);
            if (values == null) {
                values = new ArrayList<LinkedStorageRef>();
                result.put(key, values);
            }
            values.add(ref);
        }
        return result;
    }

    private static boolean removeDuplicateNetworkRefs(RtsStorageSession session,
            List<LinkedStorageRef> refs, LinkedStorageRef keep) {
        boolean changed = false;
        for (LinkedStorageRef ref : refs) {
            if (!ref.equals(keep)) changed |= session.linkedStorageInfo.remove(ref);
        }
        return changed;
    }

    private static NetworkKey networkKey(WorldServer level, BlockPos pos) {
        RtsAe2Compat.BatchNetworkProbe ae2 = RtsAe2Compat.probeBatchNetwork(level, pos);
        if (ae2 != null && ae2.identity() != null) {
            return new NetworkKey((byte) 1, ae2.identity());
        }
        RtsRefinedStorageCompat.BatchNetworkProbe refined =
                RtsRefinedStorageCompat.probeBatchNetwork(level, pos);
        return refined == null || refined.identity() == null ? null
                : new NetworkKey((byte) 2, refined.identity());
    }

    private static final class Candidate {
        private final BlockPos pos;
        private final NetworkKey network;
        private Candidate(BlockPos pos, NetworkKey network) {
            this.pos = pos.toImmutable();
            this.network = network;
        }
    }

    /** 对象身份是协议语义的一部分：绝不改为 Object.equals。 */
    private static final class NetworkKey {
        private final byte kind;
        private final Object identity;
        private NetworkKey(byte kind, Object identity) {
            this.kind = kind;
            this.identity = identity;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof NetworkKey)) return false;
            NetworkKey that = (NetworkKey) other;
            return this.kind == that.kind && this.identity == that.identity;
        }
        @Override public int hashCode() {
            return 31 * this.kind + System.identityHashCode(this.identity);
        }
    }
}
