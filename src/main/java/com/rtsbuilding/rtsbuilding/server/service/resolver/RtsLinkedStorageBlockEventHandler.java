package com.rtsbuilding.rtsbuilding.server.service.resolver;

import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsEndpointLeaseCache;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

/**
 * 链接存储方块事件处理器——响应链接存储方块的世界事件（破坏/放置）。
 *
 * <p>此服务负责处理以下场景：
 * <ul>
 *   <li><b>方块破坏</b>：当链接存储方块被破坏时，自动从所有相关玩家的
 *   会话中移除过期引用，并刷新其储存页面。</li>
 *   <li><b>方块放置</b>：当精制背包（Sophisticated Backpacks）被破坏后
 *   重新放置时，迁移基于背包 UUID 的引用到新的坐标位置。</li>
 * </ul>
 *
 * <p>从 {@link RtsLinkedStorageResolver} 提取，以将方块事件逻辑
 * 与解析器的访问检查和摘要构建关注点分离。
 */
public final class RtsLinkedStorageBlockEventHandler {

    private RtsLinkedStorageBlockEventHandler() {
    }

    // ======================================================================
    //  Public API
    // ======================================================================

    /**
     * 当链接存储方块被破坏时调用。从所有受影响的会话中移除引用
     * 并刷新其存储页面。
     */
    public static void onLinkedStorageBlockBroken(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        for (var entry : ServiceRegistry.getInstance().session().allSessions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            if (markOrRemoveBrokenLinkedStorageRef(session, level, dimension, pos)) {
                RtsEndpointLeaseCache.INSTANCE.invalidate(player.getUUID(), dimension, pos);
                ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
            }
        }
    }

    /**
     * 当背包存储方块被放置时调用。更新所有拥有该背包的会话的新位置。
     */
    public static void onLinkedStorageBlockPlaced(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null || !RtsBackpackCompat.isAvailable()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        UUID backpackUuid = RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
        if (backpackUuid == null) {
            return;
        }
        String backpackItemId = RtsBackpackCompat.getBackpackItemId(blockEntity).orElse("");
        LinkedStorageRef newRef = new LinkedStorageRef(level.dimension(), pos.immutable());
        String displayName = RtsLinkedStorageResolver.resolveDisplayName(level, pos);
        for (var entry : ServiceRegistry.getInstance().session().allSessions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            if (moveBackpackLinkedStorageRef(session, backpackUuid, backpackItemId, newRef, displayName)) {
                RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUUID());
                ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
            }
        }
    }

    // ======================================================================
    //  Private helpers
    // ======================================================================

    private static boolean markOrRemoveBrokenLinkedStorageRef(RtsStorageSession session, ServerLevel level,
            ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorageInfo.isEmpty()) {
            return false;
        }
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.immutable());
        if (!session.linkedStorageInfo.contains(ref)) {
            return false;
        }
        UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
        if (backpackUuid != null) {
            UUID breakingUuid = level == null ? null
                    : RtsBackpackCompat.getBackpackUuid(level.getBlockEntity(pos)).orElse(null);
            if (!backpackUuid.equals(breakingUuid)) {
                return false;
            }
            return session.linkedStorageInfo.markDetached(ref);
        }
        return removeLinkedStorageRef(session, dimension, pos);
    }

    public static boolean moveBackpackLinkedStorageRef(RtsStorageSession session, UUID backpackUuid,
            String backpackItemId, LinkedStorageRef newRef, String displayName) {
        if (session == null || backpackUuid == null || newRef == null || session.linkedStorageInfo.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (LinkedStorageRef oldRef : List.copyOf(session.linkedStorageInfo.getAll())) {
            if (!backpackUuid.equals(session.linkedStorageInfo.getBackpackUuid(oldRef))) {
                continue;
            }
            if (oldRef.equals(newRef)) {
                session.linkedStorageInfo.removeDetached(oldRef);
                session.linkedStorageInfo.setName(oldRef, displayName);
                if (backpackItemId != null && !backpackItemId.isBlank()) {
                    session.linkedStorageInfo.setBackpackItemId(oldRef, backpackItemId);
                }
                changed = true;
                continue;
            }
            if (session.linkedStorageInfo.contains(newRef)
                    && !backpackUuid.equals(session.linkedStorageInfo.getBackpackUuid(newRef))) {
                continue;
            }
            byte mode = session.linkedStorageInfo.getMode(oldRef);
            int priority = session.linkedStorageInfo.getPriority(oldRef);
            int index = session.linkedStorageInfo.indexOf(oldRef);
            if (index < 0) {
                continue;
            }
            if (session.linkedStorageInfo.contains(newRef)) {
                session.linkedStorageInfo.remove(oldRef);
            } else {
                session.linkedStorageInfo.set(index, newRef);
            }
            session.linkedStorageInfo.setName(newRef, displayName);
            session.linkedStorageInfo.setMode(newRef, mode);
            session.linkedStorageInfo.setPriority(newRef, priority);
            session.linkedStorageInfo.setBackpackUuid(newRef, backpackUuid);
            if (backpackItemId != null && !backpackItemId.isBlank()) {
                session.linkedStorageInfo.setBackpackItemId(newRef, backpackItemId);
            }
            session.linkedStorageInfo.removeDetached(newRef);
            changed = true;
        }
        return changed;
    }

    private static boolean removeLinkedStorageRef(RtsStorageSession session, ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorageInfo.isEmpty()) {
            return false;
        }
        boolean removed = false;
        for (LinkedStorageRef ref : List.copyOf(session.linkedStorageInfo.getAll())) {
            if (ref != null && dimension.equals(ref.dimension()) && pos.equals(ref.pos())) {
                session.linkedStorageInfo.remove(ref);
                removed = true;
            }
        }
        if (removed) {
            session.linkedStorageInfo.cleanupOrphans();
        }
        return removed;
    }

    public static UUID readBackpackUuid(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !RtsBackpackCompat.isAvailable()) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
    }

    /**
     * 移除其 {@link LinkedStorageRef} 不再存在于
     * {@code session.linkedStorageInfo} 中的孤立元数据条目。
     * 在从列表中移除引用的任何操作后调用。
     */
    public static void cleanupOrphanRefs(RtsStorageSession session) {
        session.linkedStorageInfo.cleanupOrphans();
    }
}
