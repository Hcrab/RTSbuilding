package com.rtsbuilding.rtsbuilding.server.service.resolver;

import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
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
        for (var entry : ServiceRegistry.getInstance().session().allSessions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            if (markOrRemoveBrokenLinkedStorageRef(session, level, dimension, pos)) {
                ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
                RtsStorageTickService.INSTANCE.forceRefresh(player);
                session.transfer.pageDataVersion.incrementAndGet();
                ServiceRegistry.getInstance().page().requestPage(player, session.browser.page, session.browser.search,
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
        for (var entry : ServiceRegistry.getInstance().session().allSessions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            if (moveBackpackLinkedStorageRef(session, backpackUuid, backpackItemId, newRef, displayName)) {
                ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
                RtsStorageTickService.INSTANCE.forceRefresh(player);
                session.transfer.pageDataVersion.incrementAndGet();
                ServiceRegistry.getInstance().page().requestPage(player, session.browser.page, session.browser.search,
                        session.browser.category, session.browser.sort, session.browser.ascending);
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
     * Removes orphaned metadata entries whose {@link LinkedStorageRef} is no
     * longer present in {@code session.linkedStorages}. Called after any
     * operation that removes refs from the list.
     */
    public static void cleanupOrphanRefs(RtsStorageSession session) {
        session.linkedStorageInfo.cleanupOrphans();
    }
}
