package com.rtsbuilding.rtsbuilding.server.plugin;

/**
 * Describes uniqueness groups for installed RTS plugins.
 *
 * <p>This is a server rule, not a UI slot category. Players never need to see
 * these family names; the service uses them to reject duplicate or mutually
 * exclusive plugins before any inventory mutation happens.
 */
public enum RtsPluginFamily {
    UNIQUE(false),
    RANGE_EXTENSION(true),
    HARVEST_TIER(true);

    private final boolean mutuallyExclusive;

    RtsPluginFamily(boolean mutuallyExclusive) {
        this.mutuallyExclusive = mutuallyExclusive;
    }

    public boolean mutuallyExclusive() {
        return this.mutuallyExclusive;
    }
}
