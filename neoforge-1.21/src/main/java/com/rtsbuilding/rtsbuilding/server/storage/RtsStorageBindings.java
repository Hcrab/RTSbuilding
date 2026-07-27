package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.service.bindings.RtsLinkedStorageBindingService;
import com.rtsbuilding.rtsbuilding.server.service.bindings.RtsQuickSlotBindingService;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Owns the binding edge of a player's RTS storage session.
 *
 * <p>This helper class determines which storage references, external GUI targets, quick slot item IDs,
 * and builder mode values are stored on the player's RTS session. It deliberately does not read or build
 * full storage pages, aggregate storage contents, move items, transfer fluids, craft,
 * mine, place blocks, or persist wrappers, so existing network handlers do not need to know about this split.
 *
 * <p>Linked storage capability probing and access checks still come from {@link RtsLinkedStorageResolver};
 * this class only applies the resulting binding state to the session. Remote GUI opening is delegated to {@link RtsGuiBindingHelper}.
 */
public final class RtsStorageBindings {
    public static final int QUICK_SLOT_COUNT = 27;
    public static final int GUI_BINDING_SLOT_COUNT = 8;

    /** Linked storage cap — prevents infinite player additions from degrading page build performance. */
    public static final int MAX_LINKED_STORAGES = 50;

    private RtsStorageBindings() {
    }

    // ======================================================================
    //  Builder mode
    // ======================================================================

    /**
     * Stores the requested builder mode and reports whether leaving funnel mode requires the manager
     * to flush the funnel buffer and refresh the page.
     */
    public static boolean setMode(RtsStorageSession session, BuilderMode mode) {
        if (session == null) {
            return false;
        }
        session.mode = mode;
        return mode != BuilderMode.FUNNEL && session.funnel.funnelEnabled;
    }

    // ======================================================================
    //  Storage linking
    // ======================================================================

    /**
     * Toggles or relocates a linked storage reference, preserving existing extract-only mode behavior.
     * Targets with no item or fluid endpoints still cause the UI to return to page zero without saving session data.
     */
    public static UpdateResult linkStorage(ServerPlayer player, RtsStorageSession session, BlockPos pos, byte linkMode) {
        return RtsLinkedStorageBindingService.linkStorage(player, session, pos, linkMode);
    }

    /**
     * Updates settings for an existing linked storage row. This is deliberately not a link/create operation:
     * the detail panel can edit the mode and AE-style priority,
     * but the server still requires the reference to already belong to the player's session.
     */
    public static UpdateResult updateLinkedStorageSettings(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode, int priority) {
        return RtsLinkedStorageBindingService.updateSettings(player, session, pos, linkMode, priority);
    }

    // ======================================================================
    //  Quick slots
    // ======================================================================

    /**
     * Updates a fixed quick slot cell. A blank/null item ID clears the slot;
     * a non-blank ID must resolve to a registered item before the session is changed.
     */
    public static UpdateResult setQuickSlot(RtsStorageSession session, byte slotId, String itemId, ItemStack previewStack) {
        return RtsQuickSlotBindingService.setQuickSlot(session, slotId, itemId, previewStack);
    }

    public static boolean isValidQuickSlotIndex(int slot) {
        return RtsQuickSlotBindingService.isValidSlotIndex(slot);
    }

    // ======================================================================
    //  GUI bindings (delegated to RtsGuiBindingHelper)
    // ======================================================================

    /**
     * Binds or clears an external GUI slot.
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
     * Backfills old GUI bindings that predate item ID icons.
     */
    public static boolean refreshMissingGuiBindingIcons(ServerPlayer player, RtsStorageSession session) {
        return RtsGuiBindingHelper.refreshMissingGuiBindingIcons(player, session);
    }

    // ======================================================================
    //  Record types
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
