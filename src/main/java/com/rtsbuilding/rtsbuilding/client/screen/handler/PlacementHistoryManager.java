package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;

/**
 * Client-side proxy for server-side history management (refactored from Ultimine-Rewind style).
 * <p>
 * History is now fully managed on the server (see {@link com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager}).
 * This client proxy is responsible for:
 * <ul>
 *   <li>Sending undo requests to the server</li>
 *   <li>Receiving and caching server-synced history state (undoSize)</li>
 *   <li>Providing getUndoSize for UI button state</li>
 * </ul>
 * <p>
 * All legacy recording methods are retained as no-ops for API compatibility;
 * actual history recording is handled server-side during operation execution.
 */
public final class PlacementHistoryManager {

    /** Current active instance for network handler static callbacks. */
    private static PlacementHistoryManager INSTANCE = null;

    /** Cached undoSize from last server sync (preserved before INSTANCE is available). */
    private static int CACHED_UNDO_SIZE = 0;

    private BuilderScreen screen;
    private int undoSize = 0;

    /**
     * Initialises the manager, binding the owning Screen.
     */
    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        INSTANCE = this;
        // Apply cached sync value (server may send a sync packet before INSTANCE is set when entering RTS mode)
        this.undoSize = CACHED_UNDO_SIZE;
    }

    // ===== State queries =====

    /** Current number of undoable steps. */
    public int getUndoSize() {
        return this.undoSize;
    }

    // ===== Undo =====

    /**
     * Sends an undo request to the server.
     */
    public boolean undo() {
        RtsClientPacketGateway.sendUndo();
        return true;
    }

    // ===== Server state sync =====

    /**
     * Receives a history state sync from the server.
     * <p>
     * Called by {@link com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers#handleHistorySync}
     * when a {@link com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHistorySyncPayload} is received.
     *
     * @param newUndoSize the server's current undoable step count
     */
    public static void syncHistoryState(int newUndoSize) {
        // Always update cache so no sync is lost before INSTANCE is set
        CACHED_UNDO_SIZE = newUndoSize;
        PlacementHistoryManager instance = INSTANCE;
        if (instance != null) {
            instance.undoSize = newUndoSize;
        }
    }

    // ===== Lifecycle =====

    /** Clears all state. */
    public void clear() {
        this.undoSize = 0;
        CACHED_UNDO_SIZE = 0;
        INSTANCE = null;
    }

    public BuilderScreen getScreen() {
        return screen;
    }
}
