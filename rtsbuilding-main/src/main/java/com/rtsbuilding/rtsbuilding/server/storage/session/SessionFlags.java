package com.rtsbuilding.rtsbuilding.server.storage.session;

import java.util.HashMap;
import java.util.Map;

/**
 * Session-level boolean flags and virtual fluid storage, scoped to a single RtsStorageSession.
 *
 * <p>Extracted from RtsStorageSession, grouping toggle flags and internal (virtual) fluid capacity
 * into a single value object.
 *
 * <p>Field descriptions:
 * <ul>
 *   <li>{@link #useBdNetwork} — whether the BD network participates in resolution</li>
 *   <li>{@link #autoStoreMinedDrops} — whether mined drops are automatically stored in linked storage</li>
 *   <li>{@link #internalFluidMb} — virtual fluid capacity keyed by fluid registry name</li>
 * </ul>
 */
public final class SessionFlags {

    /** Whether to include the BD network as a unified storage backend. */
    public boolean useBdNetwork = true;

    /** Whether mined drops are automatically stored in linked storage. */
    public boolean autoStoreMinedDrops = true;

    /**
     * Virtual fluid capacity, {@code fluid registry name -> capacity (mB)}.
     * Used to display virtual fluid slots when no real fluid handler is present.
     */
    public final Map<String, Long> internalFluidMb = new HashMap<>();
}
