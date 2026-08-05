package com.rtsbuilding.rtsbuilding.server.storage.session;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsDestructionState;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningState;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsTransferState;

/**
 * <strong>Mutable state container</strong> (aggregate root) for a player's RTS storage session.
 *
 * <p>This class only holds pure data fields, organized into 4 modules + 6 independent state objects by functional domain:
 * <ul>
 *   <li>{@link #linkedStorageInfo} — linked storage references and metadata (7 fields)</li>
 *   <li>{@link #bdCache} — BD network cache (5 fields)</li>
 *   <li>{@link #sessionFlags} — session toggles and virtual fluid (3 fields)</li>
 *   <li>{@link #uiMemory} — UI memory (4 fields)</li>
 *   <li>{@code mode}, {@code browser}, {@code mining},
 *       {@code transfer}, {@code placement} — independent state objects</li>
 * </ul>
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li><b>Pure data container</b> — does not query block entities / resolve capabilities / serialize NBT / modify inventory / send packets</li>
 *   <li><b>Module-first</b> — new code should prefer using module encapsulation methods over directly manipulating fields</li>
 * </ul>
 */
public class RtsStorageSession {

    // ======================================================================
    // §1  Linked Storage Info Module (LinkedStorageInfo)
    // ======================================================================

    /** Linked storage references and associated metadata (linkedStorages, linkedNames, linkedModes, linkedPriorities, linkedBackpackUuids, linkedBackpackItemIds, detachedBackpackRefs) */
    public final LinkedStorageInfo linkedStorageInfo = new LinkedStorageInfo();

    // ======================================================================
    // §2  BD Network Cache Module (BdCacheState)
    // ======================================================================

    /** BD network handler, name, and stale marker cache */
    public final BdCacheState bdCache = new BdCacheState();

    // ======================================================================
    // §3  Session Flags & Virtual Fluid Module (SessionFlags)
    // ======================================================================

    /** useBdNetwork, autoStoreMinedDrops, internalFluidMb */
    public final SessionFlags sessionFlags = new SessionFlags();

    // ======================================================================
    // §4  UI Memory Module (RtsUiMemory)
    // ======================================================================

    /** Recent entries, quick slots, GUI bindings */
    public final RtsUiMemory uiMemory = new RtsUiMemory();

    // ======================================================================
    // §5  Builder Mode
    // ======================================================================

    /** RTS interaction mode (INTERACT / MINE / PLACE, etc.) */
    public BuilderMode mode = BuilderMode.INTERACT;

    // ======================================================================
    // §6  Storage Browser & Crafting Browser State
    // ======================================================================

    /** Storage browser + crafting browser state (pagination, search, category, sort, pinyin, etc.) */
    public final RtsBrowserState browser = new RtsBrowserState();

    // ======================================================================
    // §7  Remote Mining & Chain-Mining State
    // ======================================================================

    /** Remote mining and chain-mining state */
    public final RtsMiningState mining = new RtsMiningState();

    // ======================================================================
    // §9  Remote GUI Menu State
    // ======================================================================

    /** Remote menu and data version state */
    public final RtsTransferState transfer = new RtsTransferState();

    // ======================================================================
    // §10  Placement Queue
    // ======================================================================

    /** Remote placement and recovery state */
    public final RtsPlacementState placement = new RtsPlacementState();

    // ======================================================================
    // §11  Area Destruction Queue
    // ======================================================================

    /**
     * Asynchronous queue state for area destruction (AREA_DESTROY).
     *
     * <p>Only stores pending destruction jobs and suspended destruction jobs,
     * no business logic. Tool leasing still uses fields in {@link #mining}.
     */
    public final RtsDestructionState destruction = new RtsDestructionState();
}
