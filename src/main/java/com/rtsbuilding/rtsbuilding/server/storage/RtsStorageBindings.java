package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Owns the player binding edge of an RTS storage session.
 *
 * <p>This helper decides which storage refs, external GUI targets, quick-slot
 * item ids, and builder mode values are stored on the player's RTS session. It
 * deliberately does not read or build the full storage page, aggregate storage
 * contents, move items, transfer fluids, craft, mine, place blocks, or persist
 * wrappers so existing network handlers do not need to know about this split.
 *
 * <p>Linked storage capability probing and access checks still come from
 * {@link RtsLinkedStorageResolver}; this class only applies the resulting
 * binding state to the session. Remote GUI opening is delegated to
 * {@link RtsGuiBindingHelper}.
 */
public final class RtsStorageBindings {
    public static final int QUICK_SLOT_COUNT = 27;
    public static final int GUI_BINDING_SLOT_COUNT = 8;

    /** 绑定存储上限——防止玩家无限添加导致页面构建性能退???*/
    public static final int MAX_LINKED_STORAGES = 50;

    private RtsStorageBindings() {
    }

    // ======================================================================
    //  建造模??
    // ======================================================================

    /**
     * Stores the requested builder mode and reports whether leaving funnel mode
     * requires the manager to flush the funnel buffer and refresh the page.
     */
    public static boolean setMode(RtsStorageSession session, BuilderMode mode) {
        if (session == null) {
            return false;
        }
        session.mode = mode;
        return mode != BuilderMode.FUNNEL && session.funnel.funnelEnabled;
    }

    // ======================================================================
    //  存储链接
    // ======================================================================

    /**
     * Toggles or retargets a linked storage ref while preserving the existing
     * extract-only mode behavior. A target with no item or fluid endpoint still
     * asks the UI to return to page zero without saving session data.
     */
    public static UpdateResult linkStorage(ServerPlayer player, RtsStorageSession session, BlockPos pos, byte linkMode) {
        if (player == null || session == null || pos == null) {
            return UpdateResult.none();
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        Object itemHandler = RtsLinkedCapabilities.findLinkedItemHandler(player, pos);
        Object fluidHandler = RtsLinkedCapabilities.findFluidHandler(player, pos);
        if (itemHandler == null && fluidHandler == null) {
            return UpdateResult.refreshFirst(false);
        }

        BackpackLinkData backpackLinkData = BackpackLinkData.read(player.serverLevel(), pos);
        byte normalizedMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);
        if (session.linkedStorages.contains(ref)) {
            byte existingMode = session.linkedModes.getOrDefault(ref, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
            if (existingMode == normalizedMode) {
                removeLinkedRef(session, ref);
            } else {
                session.linkedModes.put(ref, normalizedMode);
                session.linkedNames.put(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
                applyBackpackMetadata(session, ref, backpackLinkData);
            }
        } else {
            // 大箱子检查：如果点击的是双箱子中未链接的一半，且另一半已链接，则执行解绑
            LinkedStorageRef existingRef = findDoubleChestLinkedRef(player, session, pos);
            if (existingRef != null) {
                removeLinkedRef(session, existingRef);
            } else {
                if (session.linkedStorages.size() >= MAX_LINKED_STORAGES) {
                    return UpdateResult.none();
                }
                session.linkedStorages.add(ref);
                session.linkedNames.put(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
                session.linkedModes.put(ref, normalizedMode);
                session.linkedPriorities.put(ref, 0);
                applyBackpackMetadata(session, ref, backpackLinkData);
            }
        }
        // Mark BD network caches as stale so the resolver re-resolves them
        // instead of using the old cached handler (which may reference blocks
        // that were unlinked or changed).
        session.bdHandlerStale = true;
        session.bdFluidHandlerStale = true;
        return UpdateResult.refreshFirst(true);
    }

    /**
     * Updates settings for an existing linked storage row. This is intentionally
     * not a link/create operation: the detail panel can edit mode and AE-style
     * priority, but the server still requires the ref to already belong to the
     * player's session.
     */
    public static UpdateResult updateLinkedStorageSettings(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode, int priority) {
        if (player == null || session == null || pos == null) {
            return UpdateResult.none();
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        if (!session.linkedStorages.contains(ref)) {
            return UpdateResult.none();
        }
        byte normalizedMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);
        int normalizedPriority = RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority);
        byte oldMode = session.linkedModes.getOrDefault(ref, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        int oldPriority = session.linkedPriorities.getOrDefault(ref, 0);
        if (oldMode == normalizedMode && oldPriority == normalizedPriority) {
            return UpdateResult.none();
        }
        session.linkedModes.put(ref, normalizedMode);
        session.linkedPriorities.put(ref, normalizedPriority);
        session.linkedNames.put(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
        return UpdateResult.refreshCurrent(session, true);
    }

    // ======================================================================
    //  快速槽
    // ======================================================================

    /**
     * Updates one fixed quick-slot cell. Blank/null item ids clear the slot;
     * nonblank ids must parse to a registered item before the session changes.
     */
    public static UpdateResult setQuickSlot(RtsStorageSession session, byte slotId, String itemId, ItemStack previewStack) {
        if (session == null) {
            return UpdateResult.none();
        }
        int slot = slotId;
        if (!isValidQuickSlotIndex(slot)) {
            return UpdateResult.none();
        }

        String normalized = "";
        ItemStack normalizedPreview = ItemStack.EMPTY;
        if (itemId != null && !itemId.isBlank()) {
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                return UpdateResult.none();
            }
            normalized = itemId;
            Item item = BuiltInRegistries.ITEM.get(key);
            if (previewStack != null && !previewStack.isEmpty() && previewStack.is(item)) {
                normalizedPreview = previewStack.copyWithCount(1);
            } else {
                normalizedPreview = new ItemStack(item);
            }
        }

        ItemStack previousPreview = session.quickSlotPreviews[slot] == null
                ? ItemStack.EMPTY
                : session.quickSlotPreviews[slot];
        if (normalized.equals(session.quickSlotItemIds[slot])
                && ItemStack.isSameItemSameTags(previousPreview, normalizedPreview)) {
            return UpdateResult.none();
        }

        session.quickSlotItemIds[slot] = normalized;
        session.quickSlotPreviews[slot] = normalizedPreview;
        return UpdateResult.refreshCurrent(session, true);
    }

    public static boolean isValidQuickSlotIndex(int slot) {
        return slot >= 0 && slot < QUICK_SLOT_COUNT;
    }

    // ======================================================================
    //  GUI 绑定（委托给 RtsGuiBindingHelper??
    // ======================================================================

    /**
     * Binds or clears one external GUI slot.
     */
    public static UpdateResult setGuiBinding(ServerPlayer player, RtsStorageSession session, byte slotId, boolean clear,
            BlockPos pos, Direction face, String itemIdHint) {
        return RtsGuiBindingHelper.setGuiBinding(player, session, slotId, clear, pos, face, itemIdHint);
    }

    /**
     * Reopens a saved GUI binding from RTS camera mode.
     */
    public static UpdateResult openGuiBinding(ServerPlayer player, RtsStorageSession session, byte slotId, double remotePovBlockReach) {
        return RtsGuiBindingHelper.openGuiBinding(player, session, slotId, remotePovBlockReach);
    }

    /**
     * Backfills older GUI bindings that predate item-id icons.
     */
    public static boolean refreshMissingGuiBindingIcons(ServerPlayer player, RtsStorageSession session) {
        return RtsGuiBindingHelper.refreshMissingGuiBindingIcons(player, session);
    }

    // ======================================================================
    //  内部辅助：存储引用操??
    // ======================================================================

    private static void removeLinkedRef(RtsStorageSession session, LinkedStorageRef ref) {
        session.linkedStorages.remove(ref);
        session.linkedNames.remove(ref);
        session.linkedModes.remove(ref);
        session.linkedPriorities.remove(ref);
        session.linkedBackpackUuids.remove(ref);
        session.linkedBackpackItemIds.remove(ref);
        session.detachedBackpackRefs.remove(ref);
    }

    private static void applyBackpackMetadata(RtsStorageSession session, LinkedStorageRef ref,
            BackpackLinkData backpackLinkData) {
        if (backpackLinkData == null || backpackLinkData.uuid() == null) {
            session.linkedBackpackUuids.remove(ref);
            session.linkedBackpackItemIds.remove(ref);
            session.detachedBackpackRefs.remove(ref);
            return;
        }
        session.linkedBackpackUuids.put(ref, backpackLinkData.uuid());
        if (backpackLinkData.itemId() == null || backpackLinkData.itemId().isBlank()) {
            session.linkedBackpackItemIds.remove(ref);
        } else {
            session.linkedBackpackItemIds.put(ref, backpackLinkData.itemId());
        }
        session.detachedBackpackRefs.remove(ref);
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
        return session.linkedStorages.contains(connectedRef);
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
        if (session.linkedStorages.contains(connectedRef)) {
            return connectedRef;
        }
        return null;
    }

    // ======================================================================
    //  记录
    // ======================================================================

    public record UpdateResult(boolean saveSession, boolean refreshPage, int page) {
        private static final UpdateResult NONE = new UpdateResult(false, false, 0);

        static UpdateResult none() {
            return NONE;
        }

        static UpdateResult refreshFirst(boolean saveSession) {
            return new UpdateResult(saveSession, true, 0);
        }

        static UpdateResult refreshCurrent(RtsStorageSession session, boolean saveSession) {
            return new UpdateResult(saveSession, true, session == null ? 0 : session.browser.page);
        }
    }

    private record BackpackLinkData(java.util.UUID uuid, String itemId) {
        private static BackpackLinkData read(ServerLevel level, BlockPos pos) {
            if (level == null || pos == null || !RtsBackpackCompat.isAvailable()) {
                return null;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            java.util.UUID uuid = RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
            if (uuid == null) {
                return null;
            }
            String itemId = RtsBackpackCompat.getBackpackItemId(blockEntity).orElse("");
            return new BackpackLinkData(uuid, itemId);
        }
    }
}
