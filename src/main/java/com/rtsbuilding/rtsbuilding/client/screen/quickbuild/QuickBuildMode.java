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
    DESTROY,
    /** 先预览并锁定洞穴，再由第二次点击确认的智能填坑模式。 */
    SMART_FILL
}
