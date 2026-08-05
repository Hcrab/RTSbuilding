package com.rtsbuilding.rtsbuilding.api.energy;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Describes who is interacting with an energy container, allowing implementers
 * to restrict insertions/extractions to certain callers.
 * <p>
 * Modeled after Mekanism's {@code mekanism.api.AutomationType}.
 */
public enum AutomationType {
    /**
     * External interaction (third party interacting with a machine/block).
     */
    EXTERNAL,
    /**
     * Internal interaction (a machine interacting with its own contents).
     */
    INTERNAL,
    /**
     * Manual interaction (player interacting manually, such as in a GUI).
     */
    MANUAL;

    /**
     * Helper method to convert a null side into an internal automation type, and
     * anything else into an external automation type.
     */
    public static AutomationType handler(@Nullable Direction side) {
        return side == null ? INTERNAL : EXTERNAL;
    }
}
