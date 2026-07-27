package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * Types of workflows that can be tracked by the workflow system.
 *
 * <p>Each enum constant represents a different category of remote operation:
 * single or batch, mine or place. This type is used to identify active workflows in the UI
 * and to determine which progress/reporting format to use.</p>
 */
public enum RtsWorkflowType {

    /** Single block remote mining. */
    MINE_SINGLE,

    /** Chain (ultimine) batch mining. */
    ULTIMINE,

    /** Area mining operation within a defined 3D volume. */
    AREA_MINE,

    /** Shape destruction operation in quick-build preview. */
    AREA_DESTROY,

    /** Single block remote placement. */
    PLACE_SINGLE,

    /** Multi-block batch placement (interactive position-by-position placement). */
    PLACE_BATCH,

    /** Quick build (pre-resolved state) shape placement. */
    QUICK_BUILD,

    /** Blueprint file remote placement build. */
    BLUEPRINT_BUILD,

    /**
     * Standalone stop mining operation (no new mining will start afterward).
     *
     * <p>Used when the player explicitly cancels a mining operation or disables RTS mode.
     * Unlike the implicit stop inside {@code StopPreviousPipe},
     * this is a user-initiated stop.</p>
     */
    STOP_MINING
}
