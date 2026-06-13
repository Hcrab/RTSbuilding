package com.rtsbuilding.rtsbuilding.server.util;

/**
 * Bundles the 6-tuple ray parameters (origin + direction) that are
 * repeatedly passed through placement, interaction, and fluid endpoints.
 *
 * <p>Created from a client payload via {@link #of(double, double, double, double, double, double)}.
 * Invalid or zero-length rays produce {@link #EMPTY}.
 */
public record RayTrace(double originX, double originY, double originZ,
                       double dirX, double dirY, double dirZ) {

    /** Sentinel for an unset / invalid ray — all zeros. */
    public static final RayTrace EMPTY = new RayTrace(0, 0, 0, 0, 0, 0);

    /**
     * Creates a {@link RayTrace} from raw origin and direction values.
     *
     * @return the ray trace, or {@link #EMPTY} if any value is NaN/infinite
     *         or the direction is a zero vector
     */
    public static RayTrace of(double originX, double originY, double originZ,
                              double dirX, double dirY, double dirZ) {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)
                || !Double.isFinite(dirX) || !Double.isFinite(dirY) || !Double.isFinite(dirZ)) {
            return EMPTY;
        }
        double lenSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (lenSq < 1.0e-6D) {
            return EMPTY;
        }
        return new RayTrace(originX, originY, originZ, dirX, dirY, dirZ);
    }

    /**
     * Converts this ray trace into a {@link TemporaryContextSwitcher.RayContext}
     * for use with the temporary context-switching helpers.
     */
    public TemporaryContextSwitcher.RayContext toContext() {
        if (this == EMPTY) return null;
        var dir = new net.minecraft.world.phys.Vec3(this.dirX, this.dirY, this.dirZ);
        return new TemporaryContextSwitcher.RayContext(
                new net.minecraft.world.phys.Vec3(this.originX, this.originY, this.originZ),
                dir.normalize());
    }
}
