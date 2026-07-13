package com.rtsbuilding.rtsbuilding.server.progression;

/**
 * Server-side RTS capability names used by the old progression gates.
 *
 * <p>The skill-tree implementation has been removed on this branch. These
 * feature names remain only as a narrow compatibility surface so existing RTS
 * services keep one obvious guard location until the production plugin service
 * replaces it.
 */
public enum RtsFeature {
    CAMERA,
    LINK_STORAGE,
    STORAGE_BROWSER,
    REMOTE_PLACE,
    REMOTE_BREAK,
    ROTATE_BLOCK,
    INTERACT,
    FUNNEL,
    AUTO_STORE_MINED_DROPS,
    REMOTE_GUI_BINDING,
    CRAFT_TERMINAL,
    JEI_TRANSFER,
    FLUID_HANDLING,
    ULTIMINE,
    AREA_MINE,
    AREA_DESTROY,
    BLUEPRINTS,
    RANGE_CULLING
}
