package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.service.bindings.RtsLinkedStorageBindingService;
import com.rtsbuilding.rtsbuilding.server.service.bindings.RtsQuickSlotBindingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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

    /** 绑定存储上限——防止玩家无限添加导致页面构建性能退化。 */
    public static final int MAX_LINKED_STORAGES = 50;

    private RtsStorageBindings() {
    }

    // ======================================================================
    //  建造模式
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
        return RtsLinkedStorageBindingService.linkStorage(player, session, pos, linkMode);
    }

    /**
     * Updates settings for an existing linked storage row. This is intentionally
     * not a link/create operation: the detail panel can edit mode and AE-style
     * priority, but the server still requires the ref to already belong to the
     * player's session.
     */
    public static UpdateResult updateLinkedStorageSettings(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode, int priority) {
        return RtsLinkedStorageBindingService.updateSettings(player, session, pos, linkMode, priority);
    }

    // ======================================================================
    //  快速槽
    // ======================================================================

    /**
     * Updates one fixed quick-slot cell. Blank/null item ids clear the slot;
     * nonblank ids must parse to a registered item before the session changes.
     */
    public static UpdateResult setQuickSlot(RtsStorageSession session, byte slotId, String itemId, ItemStack previewStack) {
        return RtsQuickSlotBindingService.setQuickSlot(session, slotId, itemId, previewStack);
    }

    public static boolean isValidQuickSlotIndex(int slot) {
        return RtsQuickSlotBindingService.isValidSlotIndex(slot);
    }

    // ======================================================================
    //  GUI 绑定（委托给 RtsGuiBindingHelper）
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
    //  记录
    // ======================================================================

    public record UpdateResult(boolean saveSession, boolean refreshPage, int page) {
        private static final UpdateResult NONE = new UpdateResult(false, false, 0);

        public static UpdateResult none() {
            return NONE;
        }

        public static UpdateResult refreshFirst(boolean saveSession) {
            return new UpdateResult(saveSession, true, 0);
        }

        public static UpdateResult refreshCurrent(RtsStorageSession session, boolean saveSession) {
            return new UpdateResult(saveSession, true, session == null ? 0 : session.browser.page);
        }
    }
}
