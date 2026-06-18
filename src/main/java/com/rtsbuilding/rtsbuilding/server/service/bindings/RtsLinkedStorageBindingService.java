package com.rtsbuilding.rtsbuilding.server.service.bindings;

import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.handler.RtsLinkedCapabilities;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.UUID;

/**
 * Manages linked storage ref lifecycle (add, toggle, update settings, remove).
 *
 * <p>Extracted from {@link RtsStorageBindings} to isolate linked-storage binding
 * logic from quick-slot and GUI-binding concerns. This class handles the session
 * mutation side of linking, including ref creation, double-chest detection,
 * backpack metadata tracking, and extract-only mode toggles.
 *
 * <p>Only the pure binding logic lives here; capability probing for chunk/block
 * existence and progression gates still come from {@link RtsLinkedCapabilities}
 * and {@link RtsLinkedStorageResolver}.
 */
public final class RtsLinkedStorageBindingService {

    private RtsLinkedStorageBindingService() {
    }

    // ======================================================================
    //  Link / unlink
    // ======================================================================

    /**
     * Toggles or retargets a linked storage ref while preserving the existing
     * extract-only mode behavior. A target with no item or fluid endpoint still
     * asks the UI to return to page zero without saving session data.
     */
    public static RtsStorageBindings.UpdateResult linkStorage(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode) {
        if (player == null || session == null || pos == null) {
            return RtsStorageBindings.UpdateResult.none();
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        Object itemHandler = RtsLinkedCapabilities.findLinkedItemHandler(player, pos);
        Object fluidHandler = RtsLinkedCapabilities.findFluidHandler(player, pos);
        if (itemHandler == null && fluidHandler == null) {
            return RtsStorageBindings.UpdateResult.refreshFirst(false);
        }

        UUID backpackUuid = readBackpackUuid(player.serverLevel(), pos);
        String backpackItemId = readBackpackItemId(player.serverLevel(), pos);
        byte normalizedMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);

        if (session.linkedStorageInfo.contains(ref)) {
            byte existingMode = session.linkedStorageInfo.getMode(ref);
            if (existingMode == normalizedMode) {
                session.linkedStorageInfo.remove(ref);
            } else {
                session.linkedStorageInfo.setMode(ref, normalizedMode);
                session.linkedStorageInfo.setName(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
                applyBackpackMetadata(session, ref, backpackUuid, backpackItemId);
            }
        } else {
            // 大箱子检查：如果点击的是双箱子中未链接的一半，且另一半已链接，则执行解绑
            LinkedStorageRef existingRef = findDoubleChestLinkedRef(player, session, pos);
            if (existingRef != null) {
                session.linkedStorageInfo.remove(existingRef);
            } else {
                if (session.linkedStorageInfo.size() >= RtsStorageBindings.MAX_LINKED_STORAGES) {
                    return RtsStorageBindings.UpdateResult.none();
                }
                session.linkedStorageInfo.add(ref, normalizedMode, 0, backpackUuid, backpackItemId);
                session.linkedStorageInfo.setName(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
            }
        }
        // Mark BD network caches as stale so the resolver re-resolves them
        // instead of using the old cached handler (which may reference blocks
        // that were unlinked or changed).
        session.bdCache.handlerStale = true;
        session.bdCache.fluidHandlerStale = true;
        return RtsStorageBindings.UpdateResult.refreshFirst(true);
    }

    /**
     * Updates settings for an existing linked storage row. This is intentionally
     * not a link/create operation: the detail panel can edit mode and AE-style
     * priority, but the server still requires the ref to already belong to the
     * player's session.
     */
    public static RtsStorageBindings.UpdateResult updateSettings(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode, int priority) {
        if (player == null || session == null || pos == null) {
            return RtsStorageBindings.UpdateResult.none();
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        if (!session.linkedStorageInfo.contains(ref)) {
            return RtsStorageBindings.UpdateResult.none();
        }
        byte normalizedMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);
        int normalizedPriority = RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority);
        byte oldMode = session.linkedStorageInfo.getMode(ref);
        int oldPriority = session.linkedStorageInfo.getPriority(ref);
        if (oldMode == normalizedMode && oldPriority == normalizedPriority) {
            return RtsStorageBindings.UpdateResult.none();
        }
        session.linkedStorageInfo.setMode(ref, normalizedMode);
        session.linkedStorageInfo.setPriority(ref, normalizedPriority);
        session.linkedStorageInfo.setName(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
        return RtsStorageBindings.UpdateResult.refreshCurrent(session, true);
    }

    // ======================================================================
    //  Internal helpers
    // ======================================================================

    private static void removeLinkedRef(RtsStorageSession session, LinkedStorageRef ref) {
        session.linkedStorageInfo.remove(ref);
    }

    private static void applyBackpackMetadata(RtsStorageSession session, LinkedStorageRef ref,
            UUID backpackUuid, String backpackItemId) {
        if (backpackUuid == null) {
            session.linkedStorageInfo.setBackpackUuid(ref, null);
            session.linkedStorageInfo.setBackpackItemId(ref, null);
            session.linkedStorageInfo.removeDetached(ref);
            return;
        }
        session.linkedStorageInfo.setBackpackUuid(ref, backpackUuid);
        session.linkedStorageInfo.setBackpackItemId(ref, backpackItemId);
        session.linkedStorageInfo.removeDetached(ref);
    }

    private static UUID readBackpackUuid(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !RtsBackpackCompat.isAvailable()) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
    }

    private static String readBackpackItemId(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !RtsBackpackCompat.isAvailable()) {
            return "";
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return RtsBackpackCompat.getBackpackItemId(blockEntity).orElse("");
    }

    /**
     * Checks whether the given block position belongs to a double chest whose
     * other half is already linked in the session.
     */
    private static boolean isDoubleChestHalfAlreadyLinked(ServerPlayer player, RtsStorageSession session, BlockPos pos) {
        if (player == null || session == null || pos == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return false;
        }
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.SINGLE) {
            return false;
        }
        Direction connectedDirection = ChestBlock.getConnectedDirection(state);
        BlockPos connectedPos = pos.relative(connectedDirection);
        LinkedStorageRef connectedRef = new LinkedStorageRef(level.dimension(), connectedPos);
        return session.linkedStorageInfo.contains(connectedRef);
    }

    /**
     * Finds the already-linked ref of the connected chest half, or null if
     * the target is not part of a double chest or the other half is not linked.
     */
    private static LinkedStorageRef findDoubleChestLinkedRef(ServerPlayer player, RtsStorageSession session, BlockPos pos) {
        if (player == null || session == null || pos == null) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.SINGLE) {
            return null;
        }
        Direction connectedDirection = ChestBlock.getConnectedDirection(state);
        BlockPos connectedPos = pos.relative(connectedDirection);
        LinkedStorageRef connectedRef = new LinkedStorageRef(level.dimension(), connectedPos);
        if (session.linkedStorageInfo.contains(connectedRef)) {
            return connectedRef;
        }
        return null;
    }
}
