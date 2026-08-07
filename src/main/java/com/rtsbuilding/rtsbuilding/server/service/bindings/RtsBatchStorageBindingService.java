package com.rtsbuilding.rtsbuilding.server.service.bindings;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.refinedstorage.RtsRefinedStorageCompat;
import com.rtsbuilding.rtsbuilding.common.storage.RtsBatchStorageSelectionBounds;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsEndpointLeaseCache;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在服务端重新发现并链接选区内已加载的储存端点。
 *
 * <p>协议只接收两个角点。扫描只遍历已加载区块的方块实体索引，不主动加载区块；每个候选仍须通过
 * 领地交互与能力检查。AE2、RS 按网络对象引用去重，原版双箱按规范化端点去重。</p>
 */
public final class RtsBatchStorageBindingService {
    private RtsBatchStorageBindingService() {
    }

    public static RtsStorageBindings.UpdateResult linkLoadedStorages(
            ServerPlayer player, RtsStorageSession session,
            BlockPos first, BlockPos second, byte linkMode) {
        if (player == null || session == null) {
            return RtsStorageBindings.UpdateResult.none();
        }
        RtsBatchStorageSelectionBounds.Bounds bounds =
                RtsBatchStorageSelectionBounds.normalize(first, second);
        if (bounds == null) {
            return RtsStorageBindings.UpdateResult.none();
        }

        ServerLevel level = player.level();
        List<BlockPos> candidates = collectLoadedBlockEntities(level, bounds);
        candidates.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));

        List<BlockPos> standaloneCandidates = new ArrayList<>();
        Map<NetworkKey, NetworkCandidate> networkCandidates = new LinkedHashMap<>();
        Set<BlockPos> canonicalEndpoints = new HashSet<>();
        for (BlockPos candidate : candidates) {
            // collectLoadedBlockEntities 已保证区块已加载；这里不增加近距离或客户端会话闸门。
            if (!RtsLinkedStorageBindingService.canLinkStorageTarget(player, candidate)) {
                continue;
            }
            NetworkCandidate networkCandidate = probeNetwork(level, candidate);
            if (networkCandidate != null) {
                networkCandidates.merge(
                        networkCandidate.key(), networkCandidate,
                        RtsBatchStorageBindingService::preferTerminal);
                continue;
            }
            BlockPos canonical = RtsLinkedStorageBindingService
                    .canonicalStoragePosition(level, candidate);
            if (canonicalEndpoints.add(canonical)) {
                standaloneCandidates.add(candidate);
            }
        }

        Map<NetworkKey, List<ExistingNetworkRef>> existingNetworks =
                collectExistingNetworks(level, session);
        List<SelectedEndpoint> selected = new ArrayList<>(standaloneCandidates.size() + networkCandidates.size());
        for (BlockPos pos : standaloneCandidates) {
            selected.add(new SelectedEndpoint(pos, null));
        }
        for (NetworkCandidate candidate : networkCandidates.values()) {
            selected.add(new SelectedEndpoint(candidate.pos(), candidate.key()));
        }
        selected.sort(Comparator
                .comparingInt((SelectedEndpoint endpoint) -> endpoint.pos().getY())
                .thenComparingInt(endpoint -> endpoint.pos().getX())
                .thenComparingInt(endpoint -> endpoint.pos().getZ()));

        boolean changed = false;
        for (SelectedEndpoint endpoint : selected) {
            List<ExistingNetworkRef> existing = endpoint.networkKey() == null
                    ? List.of()
                    : existingNetworks.getOrDefault(endpoint.networkKey(), List.of());
            if (!existing.isEmpty()) {
                ExistingNetworkRef representative = chooseExistingRepresentative(existing);
                NetworkCandidate selectedNetwork = networkCandidates.get(endpoint.networkKey());
                if (selectedNetwork != null && selectedNetwork.preferredTerminal()
                        && !representative.preferredTerminal()) {
                    RtsStorageBindings.UpdateResult replacement =
                            RtsLinkedStorageBindingService.replaceNetworkRepresentative(
                                    player, session, representative.ref(), selectedNetwork.pos());
                    if (replacement.saveSession()) {
                        changed = true;
                        representative = new ExistingNetworkRef(
                                new LinkedStorageRef(level.dimension(), selectedNetwork.pos()), true);
                    }
                }
                changed |= removeDuplicateNetworkRefs(session, existing, representative.ref());
                continue;
            }

            RtsStorageBindings.UpdateResult update =
                    RtsLinkedStorageBindingService.ensureStorageLinked(
                            player, session, endpoint.pos(), linkMode);
            changed |= update.saveSession();
            if (endpoint.networkKey() != null && update.saveSession()) {
                existingNetworks.put(endpoint.networkKey(), List.of(new ExistingNetworkRef(
                        new LinkedStorageRef(level.dimension(), endpoint.pos()),
                        networkCandidates.get(endpoint.networkKey()).preferredTerminal())));
            }
        }
        if (changed) {
            session.bdCache.handlerStale = true;
            session.bdCache.fluidHandlerStale = true;
            RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUUID());
        }
        return changed
                ? RtsStorageBindings.UpdateResult.refreshFirst(true)
                : RtsStorageBindings.UpdateResult.none();
    }

    private static NetworkCandidate probeNetwork(ServerLevel level, BlockPos pos) {
        RtsAe2Compat.BatchNetworkProbe ae2 = RtsAe2Compat.probeBatchNetwork(level, pos);
        if (ae2 != null && ae2.identity() != null) {
            return new NetworkCandidate(
                    new NetworkKey(NetworkKind.AE2, ae2.identity()), pos, ae2.preferredTerminal());
        }
        RtsRefinedStorageCompat.BatchNetworkProbe refinedStorage =
                RtsRefinedStorageCompat.probeBatchNetwork(level, pos);
        if (refinedStorage != null && refinedStorage.identity() != null) {
            return new NetworkCandidate(
                    new NetworkKey(NetworkKind.REFINED_STORAGE, refinedStorage.identity()),
                    pos, refinedStorage.preferredTerminal());
        }
        return null;
    }

    private static NetworkCandidate preferTerminal(NetworkCandidate current, NetworkCandidate incoming) {
        return incoming.preferredTerminal() && !current.preferredTerminal() ? incoming : current;
    }

    private static Map<NetworkKey, List<ExistingNetworkRef>> collectExistingNetworks(
            ServerLevel level, RtsStorageSession session) {
        Map<NetworkKey, List<ExistingNetworkRef>> result = new LinkedHashMap<>();
        for (LinkedStorageRef ref : List.copyOf(session.linkedStorageInfo.getAll())) {
            if (!level.dimension().equals(ref.dimension()) || !level.hasChunkAt(ref.pos())) {
                continue;
            }
            NetworkCandidate candidate = probeNetwork(level, ref.pos());
            if (candidate == null) {
                continue;
            }
            result.computeIfAbsent(candidate.key(), ignored -> new ArrayList<>())
                    .add(new ExistingNetworkRef(ref, candidate.preferredTerminal()));
        }
        return result;
    }

    private static ExistingNetworkRef chooseExistingRepresentative(List<ExistingNetworkRef> existing) {
        ExistingNetworkRef representative = existing.getFirst();
        for (ExistingNetworkRef candidate : existing) {
            if (candidate.preferredTerminal() && !representative.preferredTerminal()) {
                representative = candidate;
            }
        }
        return representative;
    }

    private static boolean removeDuplicateNetworkRefs(
            RtsStorageSession session, List<ExistingNetworkRef> existing, LinkedStorageRef keep) {
        boolean changed = false;
        for (ExistingNetworkRef candidate : existing) {
            if (!candidate.ref().equals(keep)) {
                changed |= session.linkedStorageInfo.remove(candidate.ref());
            }
        }
        return changed;
    }

    private static List<BlockPos> collectLoadedBlockEntities(
            ServerLevel level, RtsBatchStorageSelectionBounds.Bounds bounds) {
        List<BlockPos> result = new ArrayList<>();
        int minChunkX = bounds.min().getX() >> 4;
        int maxChunkX = bounds.max().getX() >> 4;
        int minChunkZ = bounds.min().getZ() >> 4;
        int maxChunkZ = bounds.max().getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (bounds.contains(pos)) {
                        result.add(pos.immutable());
                    }
                }
            }
        }
        return result;
    }

    private enum NetworkKind {
        AE2,
        REFINED_STORAGE
    }

    /** 使用第三方网络对象的引用身份，避免 equals 实现变化影响本次扫描去重。 */
    private static final class NetworkKey {
        private final NetworkKind kind;
        private final Object identity;

        private NetworkKey(NetworkKind kind, Object identity) {
            this.kind = kind;
            this.identity = identity;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NetworkKey key
                    && this.kind == key.kind
                    && this.identity == key.identity;
        }

        @Override
        public int hashCode() {
            return 31 * this.kind.hashCode() + System.identityHashCode(this.identity);
        }
    }

    private record NetworkCandidate(NetworkKey key, BlockPos pos, boolean preferredTerminal) {
    }

    private record ExistingNetworkRef(LinkedStorageRef ref, boolean preferredTerminal) {
    }

    private record SelectedEndpoint(BlockPos pos, NetworkKey networkKey) {
    }
}
