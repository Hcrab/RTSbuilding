package com.rtsbuilding.rtsbuilding.progression;

public record RtsUnlockEffect(Type type, RtsFeature feature, int value) {
    public enum Type {
        UNLOCK_FEATURE,
        SET_RADIUS_BLOCKS,
        SET_FLUID_CAPACITY_BUCKETS,
        SET_ULTIMINE_LIMIT,
        BYPASS_HOME_RADIUS
    }

    public static RtsUnlockEffect unlock(RtsFeature feature) {
        return new RtsUnlockEffect(Type.UNLOCK_FEATURE, feature, 0);
    }

    public static RtsUnlockEffect radius(int blocks) {
        return new RtsUnlockEffect(Type.SET_RADIUS_BLOCKS, null, blocks);
    }

    public static RtsUnlockEffect fluidCapacityBuckets(int buckets) {
        return new RtsUnlockEffect(Type.SET_FLUID_CAPACITY_BUCKETS, null, buckets);
    }

    public static RtsUnlockEffect ultimineLimit(int blocks) {
        return new RtsUnlockEffect(Type.SET_ULTIMINE_LIMIT, null, blocks);
    }

    public static RtsUnlockEffect bypassHomeRadius() {
        return new RtsUnlockEffect(Type.BYPASS_HOME_RADIUS, null, 0);
    }
}
