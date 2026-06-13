package com.rtsbuilding.rtsbuilding.common;

/**
 * Shared history limits used by server-authoritative undo tracking and
 * client-side UI affordances. These constants intentionally live outside
 * client packages so dedicated servers never load UI classes for gameplay
 * history rules.
 */
public final class RtsHistoryConstants {
    /** Maximum number of shape/build history entries kept per player stack. */
    public static final int SHAPE_HISTORY_LIMIT = 1000;

    private RtsHistoryConstants() {
    }
}
