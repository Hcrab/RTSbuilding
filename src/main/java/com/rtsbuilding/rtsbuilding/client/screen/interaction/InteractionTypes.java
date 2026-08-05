package com.rtsbuilding.rtsbuilding.client.screen.interaction;

import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;

import java.util.Objects;

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
    public static final class InteractionTarget {
        private final int entityId;
        private final Vec3d hitLocation;
        private final RayTraceResult blockHit;
        private final Vec3d rayOrigin;
        private final Vec3d rayDir;

        public InteractionTarget(int entityId, Vec3d hitLocation, RayTraceResult blockHit,
                Vec3d rayOrigin, Vec3d rayDir) {
            this.entityId = entityId;
            this.hitLocation = hitLocation;
            this.blockHit = blockHit;
            this.rayOrigin = rayOrigin;
            this.rayDir = rayDir;
        }

        public int entityId() { return entityId; }
        public Vec3d hitLocation() { return hitLocation; }
        public RayTraceResult blockHit() { return blockHit; }
        public Vec3d rayOrigin() { return rayOrigin; }
        public Vec3d rayDir() { return rayDir; }

        public boolean isEntityTarget() {
            return this.entityId >= 0;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InteractionTarget)) return false;
            InteractionTarget that = (InteractionTarget) other;
            return entityId == that.entityId
                    && Objects.equals(hitLocation, that.hitLocation)
                    && Objects.equals(blockHit, that.blockHit)
                    && Objects.equals(rayOrigin, that.rayOrigin)
                    && Objects.equals(rayDir, that.rayDir);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityId, hitLocation, blockHit, rayOrigin, rayDir);
        }

        @Override
        public String toString() {
            return "InteractionTarget[entityId=" + entityId + ", hitLocation=" + hitLocation
                    + ", blockHit=" + blockHit + ", rayOrigin=" + rayOrigin + ", rayDir=" + rayDir + "]";
        }
    }

    /** Source kind for replaying or undoing shape placement batches. */
    public enum PlacementReplayKind {
        PIN_ITEM,
        TOOL_SLOT
    }
}
