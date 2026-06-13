package com.rtsbuilding.rtsbuilding.server.service.resolver;

import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

/**
 * Handles block-event lifecycle for linked storage blocks.
 *
 * <p>This service is responsible for responding to linked storage block
 * break and place events: removing stale refs from player sessions,
 * refreshing storage pages, and migrating backpack UUID-based refs
 * when a Sophisticated Backpack is broken and re-placed.
 *
 * <p>Extracted from {@link RtsLinkedStorageResolver} to isolate block-event
 * logic from resolver access-check and summary-building concerns.
 */
public final class RtsLinkedStorageBlockEventHandler {

    private RtsLinkedStorageBlockEventHandler() {
    }

    // ======================================================================
    //  Public API
    // ======================================================================

    /**
     * Called when a linked storage block is broken. Removes the reference
     * from all affected sessions and refreshes their storage page.
     */
    public static void onLinkedStorageBlockBroken(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        for (var entry : RtsSessionService.allSessions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            if (markOrRemoveBrokenLinkedStorageRef(session, level, dimension, pos)) {
                RtsSessionService.saveToPlayerNbt(player, session);
                RtsStorageTickService.INSTANCE.forceRefresh(player);
                session.transfer.pageDataVersion.incrementAndGet();
                RtsPageService.requestPage(player, session.browser.page, session.browser.search,
                        session.browser.category, session.browser.sort, session.browser.ascending);
            }
        }
    }

    /**
     * Called when a backpack storage block is placed. Updates all sessions
     * that own the backpack with the new position.
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
        for (var entry : RtsSessionService.allSessions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            if (moveBackpackLinkedStorageRef(session, backpackUuid, backpackItemId, newRef, displayName)) {
                RtsSessionService.saveToPlayerNbt(player, session);
                RtsStorageTickService.INSTANCE.forceRefresh(player);
                session.transfer.pageDataVersion.incrementAndGet();
                RtsPageService.requestPage(player, session.browser.page, session.browser.search,
                        session.browser.category, session.browser.sort, session.browser.ascending);
            }
        }
    }

    // ======================================================================
    //  Private helpers
    // ======================================================================

    private static boolean markOrRemoveBrokenLinkedStorageRef(RtsStorageSession session, ServerLevel level,
            ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.immutable());
        if (!session.linkedStorages.contains(ref)) {
            return false;
        }
        UUID backpackUuid = session.linkedBackpackUuids.get(ref);
        if (backpackUuid != null) {
            UUID breakingUuid = level == null ? null
                    : RtsBackpackCompat.getBackpackUuid(level.getBlockEntity(pos)).orElse(null);
            if (!backpackUuid.equals(breakingUuid)) {
                return false;
            }
            return session.detachedBackpackRefs.add(ref);
        }
        return removeLinkedStorageRef(session, dimension, pos);
    }

    public static boolean moveBackpackLinkedStorageRef(RtsStorageSession session, UUID backpackUuid,
            String backpackItemId, LinkedStorageRef newRef, String displayName) {
        if (session == null || backpackUuid == null || newRef == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (LinkedStorageRef oldRef : List.copyOf(session.linkedStorages)) {
            if (!backpackUuid.equals(session.linkedBackpackUuids.get(oldRef))) {
                continue;
            }
            if (oldRef.equals(newRef)) {
                session.detachedBackpackRefs.remove(oldRef);
                session.linkedNames.put(oldRef, displayName);
                if (backpackItemId != null && !backpackItemId.isBlank()) {
                    session.linkedBackpackItemIds.put(oldRef, backpackItemId);
                }
                changed = true;
                continue;
            }
            if (session.linkedStorages.contains(newRef)
                    && !backpackUuid.equals(session.linkedBackpackUuids.get(newRef))) {
                continue;
            }
            byte mode = session.linkedModes.getOrDefault(oldRef,
                    RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
            int priority = session.linkedPriorities.getOrDefault(oldRef, 0);
            int index = session.linkedStorages.indexOf(oldRef);
            if (index < 0) {
                continue;
            }
            if (session.linkedStorages.contains(newRef)) {
                removeLinkedStorageRef(session, oldRef.dimension(), oldRef.pos());
            } else {
                session.linkedStorages.set(index, newRef);
                session.linkedNames.remove(oldRef);
                session.linkedModes.remove(oldRef);
                session.linkedPriorities.remove(oldRef);
                session.linkedBackpackUuids.remove(oldRef);
                session.linkedBackpackItemIds.remove(oldRef);
                session.detachedBackpackRefs.remove(oldRef);
            }
            session.linkedNames.put(newRef, displayName);
            session.linkedModes.put(newRef, mode);
            session.linkedPriorities.put(newRef,
                    RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority));
            session.linkedBackpackUuids.put(newRef, backpackUuid);
            if (backpackItemId != null && !backpackItemId.isBlank()) {
                session.linkedBackpackItemIds.put(newRef, backpackItemId);
            }
            session.detachedBackpackRefs.remove(newRef);
            changed = true;
        }
        return changed;
    }

    private static boolean removeLinkedStorageRef(RtsStorageSession session, ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        boolean removed = session.linkedStorages.removeIf(ref ->
                ref != null && dimension.equals(ref.dimension()) && pos.equals(ref.pos()));
        if (removed) {
            cleanupOrphanRefs(session);
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
     * Removes orphaned metadata entries whose {@link LinkedStorageRef} is no
     * longer present in {@code session.linkedStorages}. Called after any
     * operation that removes refs from the list.
     */
    public static void cleanupOrphanRefs(RtsStorageSession session) {
        session.linkedNames.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedModes.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedPriorities.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedBackpackUuids.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedBackpackItemIds.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.detachedBackpackRefs.removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
    }
}
