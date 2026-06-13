package com.rtsbuilding.rtsbuilding.server.util;

/**
 * Bundles the 6-tuple ray parameters (origin + direction) that are repeatedly
 * passed through placement, interaction, and fluid endpoints.
 */
public record RayTrace(double originX, double originY, double originZ,
                       double dirX, double dirY, double dirZ) {

    public static final RayTrace EMPTY = new RayTrace(0, 0, 0, 0, 0, 0);

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

    public TemporaryContextSwitcher.RayContext toContext() {
        if (this == EMPTY) {
            return null;
        }
        var dir = new net.minecraft.world.phys.Vec3(this.dirX, this.dirY, this.dirZ);
        return new TemporaryContextSwitcher.RayContext(
                new net.minecraft.world.phys.Vec3(this.originX, this.originY, this.originZ),
                dir.normalize());
    }
}
