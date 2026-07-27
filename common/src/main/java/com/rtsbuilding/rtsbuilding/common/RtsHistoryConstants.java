package com.rtsbuilding.rtsbuilding.common;

/**
 * History constants — defines history limits used by server-side undo tracking and client UI.
 * <p>
 * These constants are intentionally placed in the common package rather than the client package,
 * so that dedicated servers do not need to load UI classes when loading game rule history.
 */
public final class RtsHistoryConstants {

    /** Maximum number of entries per player for the shape/build history stack */
    public static final int SHAPE_HISTORY_LIMIT = 1000;

    private RtsHistoryConstants() {
    }
}
