package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.core.BlockPos;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable state container for remote GUI menu state and session data versioning.
 *
 * <p>Extracted from RtsStorageSession, aggregated by the responsibility of "remote menu interaction and data version tracking".
 * Contains the remote GUI menu's container ID and block position,
 * storage view dirty flag, and page cache data version number.
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li><b>Pure data container</b> — contains no business logic, only holds public mutable fields</li>
 *   <li><b>Independently instantiable</b> — allows testing transfer state transitions without a full session</li>
 * </ul>
 */
public class RtsTransferState {

    // ======================================================================
    // Remote GUI menu state
    // ======================================================================

    /** Container ID of the remote GUI menu; -1 = no active remote menu */
    public int remoteMenuContainerId = -1;

    /** Block position of the remote GUI menu */
    public BlockPos remoteMenuPos;

    /** True when the client's storage browser page no longer matches the storage content. */
    public boolean storageViewDirty;

    /**
     * Storage data version number — incremented when cached data changes.
     * <p>Used for page cache expiry detection in {@code RtsPageCore}.
     * When only page navigation occurs (search/sort/category unchanged),
     * if the version number has not changed, the O(n log n) sort/filter rebuild is skipped.
     */
    public final AtomicLong pageDataVersion = new AtomicLong();
}
