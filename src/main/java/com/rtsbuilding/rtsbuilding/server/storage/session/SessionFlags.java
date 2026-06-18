package com.rtsbuilding.rtsbuilding.server.storage.session;

import java.util.HashMap;
import java.util.Map;

/**
 * Session-level boolean flags and virtual fluid storage scoped to a single
 * RtsStorageSession.
 *
 * <p>Extracted from RtsStorageSession to group toggle flags and
 * internal (virtual) fluid capacity into a single value object.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #useBdNetwork} — whether BD network participates in resolution</li>
 *   <li>{@link #autoStoreMinedDrops} — whether mined drops auto-enter linked storage</li>
 *   <li>{@link #internalFluidMb} — virtual fluid capacity keyed by fluid registry name</li>
 * </ul>
 */
public final class SessionFlags {

    /** Whether to include the BD network as a unified storage backend. */
    public boolean useBdNetwork = true;

    /** Whether mined drops are automatically stored into linked storage. */
    public boolean autoStoreMinedDrops = true;

    /**
     * Virtual fluid capacity, {@code fluid registry name -> capacity(mB)}.
     * Used to display virtual fluid slots when no real fluid handlers exist.
     */
    public final Map<String, Long> internalFluidMb = new HashMap<>();
}
