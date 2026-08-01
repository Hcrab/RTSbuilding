package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

/**
 * Player-facing mode for the quick-build window.
 *
 * <p>BUILD keeps the existing shape placement workflow. DESTROY reuses the same
 * shape and fill controls, but routes world clicks into the area-mine selection
 * and server-side batch-breaking path.</p>
 */
public enum QuickBuildMode {
    BUILD,
    DESTROY
}
