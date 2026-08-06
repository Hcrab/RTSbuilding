package com.rtsbuilding.rtsbuilding.server.service.bindings;

import com.rtsbuilding.rtsbuilding.common.storage.RtsBatchStorageSelectionBounds;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在服务端重新发现并链接选区内的储存端点。
 *
 * <p>它明确不接受客户端枚举的坐标，也不会为了完成批量操作加载新区块。扫描对象是已加载区块
 * 的方块实体索引，而不是选区中的每一个方块，因此普通仓库区不会随选区体积线性产生大量查询。</p>
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

        ServerLevel level = player.serverLevel();
        List<BlockPos> candidates = collectLoadedBlockEntities(level, bounds);
        candidates.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getZ()));

        boolean changed = false;
        Set<BlockPos> canonicalEndpoints = new HashSet<>();
        for (BlockPos candidate : candidates) {
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, candidate)) {
                continue;
            }
            BlockPos canonical = RtsLinkedStorageBindingService
                    .canonicalStoragePosition(level, candidate);
            if (!canonicalEndpoints.add(canonical)) {
                continue;
            }
            RtsStorageBindings.UpdateResult update =
                    RtsLinkedStorageBindingService.ensureStorageLinked(
                            player, session, candidate, linkMode);
            changed |= update.saveSession();
            if (session.linkedStorageInfo.size() >= RtsStorageBindings.MAX_LINKED_STORAGES) {
                break;
            }
        }
        return changed
                ? RtsStorageBindings.UpdateResult.refreshFirst(true)
                : RtsStorageBindings.UpdateResult.none();
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
