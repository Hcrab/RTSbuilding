package com.rtsbuilding.rtsbuilding.client.screen.ultimine;


/**
 * Player-facing batch mining mode selected from the Ultimine window.
 */
public enum UltimineMode {
    /** Flood-fill through connected blocks of the same block type. */
    CHAIN,
    /** Flood-fill through connected breakable blocks without the same-type filter. */
    AREA
}
