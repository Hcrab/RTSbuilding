package com.rtsbuilding.rtsbuilding.server.service.bindings;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.refinedstorage.RtsRefinedStorageCompat;
import com.rtsbuilding.rtsbuilding.common.storage.RtsBatchStorageSelectionBounds;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * 在服务端重新发现选区内已加载的储存端点。
 *
 * <p>客户端只提交两个锚点。扫描只遍历已加载区块的方块实体索引；普通端点和无法证明
 * 网络身份的第三方端点全部保留，只有双箱子和可证明同一 AE2/RS 网络的端点会去重。</p>
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

        ServerLevel level = player.getLevel();
        Map<Object, Boolean> seenAe2 = new IdentityHashMap<>();
        Map<Object, Boolean> seenRs = new IdentityHashMap<>();
        seedExistingNetworkIdentities(level, session, seenAe2, seenRs);
        Set<BlockPos> seenCanonical = new HashSet<>();
        List<BlockPos> candidates = collectLoadedBlockEntities(level, bounds);
        candidates.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getZ()));

        boolean changed = false;
        int linked = 0;
        for (BlockPos candidate : candidates) {
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, candidate)
                    || !RtsLinkedStorageBindingService.canLinkStorageTarget(player, candidate)) {
                continue;
            }
            BlockPos canonical = RtsLinkedStorageBindingService
                    .canonicalStoragePosition(level, candidate);
            if (!seenCanonical.add(canonical)) {
                continue;
            }
            Object ae2 = RtsAe2Compat.batchNetworkIdentity(level, candidate);
            if (ae2 != null && seenAe2.put(ae2, Boolean.TRUE) != null) {
                continue;
            }
            Object rs = ae2 == null
                    ? RtsRefinedStorageCompat.batchNetworkIdentity(level, candidate)
                    : null;
            if (rs != null && seenRs.put(rs, Boolean.TRUE) != null) {
                continue;
            }
            boolean added = RtsLinkedStorageBindingService.ensureStorageLinked(
                    player, session, candidate, linkMode).saveSession();
            changed |= added;
            if (added) linked++;
        }
        Component feedback = linked > 0
                ? Component.translatable(
                        "message.rtsbuilding.storage_batch.result",
                        linked, session.linkedStorageInfo.size(), Config.maxLinkedStorages())
                : Component.translatable(
                        "message.rtsbuilding.storage_batch.result_none",
                        session.linkedStorageInfo.size(), Config.maxLinkedStorages());
        player.displayClientMessage(feedback, true);
        return changed
                ? RtsStorageBindings.UpdateResult.refreshFirst(true)
                : RtsStorageBindings.UpdateResult.none();
    }

    private static void seedExistingNetworkIdentities(
            ServerLevel level, RtsStorageSession session,
            Map<Object, Boolean> ae2, Map<Object, Boolean> rs) {
        for (LinkedStorageRef ref : List.copyOf(session.linkedStorageInfo.getAll())) {
            if (ref == null || ref.pos() == null || !level.dimension().equals(ref.dimension())
                    || !level.hasChunkAt(ref.pos())) {
                continue;
            }
            Object ae2Identity = RtsAe2Compat.batchNetworkIdentity(level, ref.pos());
            if (ae2Identity != null) {
                ae2.put(ae2Identity, Boolean.TRUE);
                continue;
            }
            Object rsIdentity = RtsRefinedStorageCompat.batchNetworkIdentity(level, ref.pos());
            if (rsIdentity != null) {
                rs.put(rsIdentity, Boolean.TRUE);
            }
        }
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
}
