package com.rtsbuilding.rtsbuilding.server.storage.session;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Mutable state container for the storage browser and crafting browser.
 *
 * <p>Extracted from RtsStorageSession, aggregated by the responsibility of "how the user views and filters content in the browser interface".
 * Contains pagination, search, category sorting, and pinyin fuzzy search.
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li><b>Pure data container</b> — contains no business logic, only holds public mutable fields</li>
 *   <li><b>Independently instantiable</b> — allows testing browser state transitions without a full session</li>
 * </ul>
 */
public class RtsBrowserState {

    /** Default batch size for block placement. */
    public static final int CRAFTABLE_BATCH_SIZE = 12;

    // ======================================================================
    // Storage browser state
    // ======================================================================

    /** Current page number (0-based) */
    public int page;
    /** Number of entries per page, default read from page builder constant */
    public int pageSize = RtsStoragePageBuilder.DEFAULT_PAGE_SIZE;
    /** Search keyword (empty = no filter) */
    public String search = "";
    /** Category filter: "all" / "mod|namespace" / "tab|name" */
    public String category = "all";
    /** Current sort mode: by quantity/name/mod/type */
    public RtsStorageSort sort = RtsStorageSort.QUANTITY;
    /** true = ascending, false = descending */
    public boolean ascending = false;
    /** Pinyin fuzzy search toggle */
    public boolean pinyinSearchEnabled;
    /** Set of localized search hit IDs (used for client-side highlighting/quick filtering) */
    public final Set<String> localizedSearchMatches = new HashSet<>();

    // ======================================================================
    //  Crafting browser state
    // ======================================================================

    /** Crafting search keyword */
    public String craftSearch = "";
    /** Whether to show uncraftable recipes */
    public boolean craftShowUnavailable;
    /** Total number of requested crafting recipes (includes offset and limit, at least CRAFTABLE_BATCH_SIZE) */
    public int craftRequestedCount = CRAFTABLE_BATCH_SIZE;
    /** Pinyin fuzzy search toggle for crafting search */
    public boolean craftPinyinSearchEnabled;
    /** Localized hit ID set for crafting search */
    public final Set<String> craftLocalizedSearchMatches = new HashSet<>();
}
