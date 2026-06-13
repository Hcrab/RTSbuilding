package com.rtsbuilding.rtsbuilding.client.screen.interaction;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared data records for RTS screen interaction flows that are still active.
 *
 * <p>The old circular interaction wheel has been retired, but the screen still
 * needs a compact target record for normal block/entity interactions and a
 * placement replay kind for undo.
 */
public final class InteractionTypes {

    private InteractionTypes() {}

    /**
     * Target picked from the current RTS cursor ray.
     *
     * @param entityId    target entity id, or -1 when the target is a block
     * @param hitLocation precise hit location
     * @param blockHit    block hit result, null for entity targets
     * @param rayOrigin   ray-cast origin
     * @param rayDir      ray-cast direction
     */
    public record InteractionTarget(
            int entityId,
            Vec3 hitLocation,
            BlockHitResult blockHit,
            Vec3 rayOrigin,
            Vec3 rayDir) {

        public boolean isEntityTarget() {
            return this.entityId >= 0;
        }
    }

    /** Source kind for replaying or undoing shape placement batches. */
    public enum PlacementReplayKind {
        PIN_ITEM,
        TOOL_SLOT
    }
}
