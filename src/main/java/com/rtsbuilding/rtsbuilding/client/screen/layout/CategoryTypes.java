package com.rtsbuilding.rtsbuilding.client.screen.layout;

/**
 * Container for category-browsing data types.
 * <p>
 * Groups the row model and click-result record that drive the category
 * tree rendered inside the bottom panel's category sidebar. Both records
 * are produced and consumed by
 */
public final class CategoryTypes {

    /**
     * A single category row in the bottom-panel category tree.
     * <p>
     * Each row represents one mod namespace or a tab within a mod.
     * Indentation is controlled by {@code depth} (0 = mod, 1 = tab),
     * and expandable/expanded flags determine whether the chevron icon
     * is shown and whether child rows are visible.
     *
     * @param token       unique category identifier for filtering
     * @param label       display label (translated)
     * @param depth       indentation level (0 = root mod, 1 = tab)
     * @param expandable  whether this row can be expanded (has children)
     * @param expanded    whether this row is currently expanded
     * @param modNamespace the mod's namespace (empty for the "All" row)
     */
    public record CategoryRow(
            String token,
            String label,
            int depth,
            boolean expandable,
            boolean expanded,
            String modNamespace) {}

    /**
     * Result of a category-click action.
     * <p>
     * Carries the selected category's token, the owning mod namespace,
     * and whether the click should <em>only</em> toggle expand/collapse
     * without changing the filter.
     *
     * @param categoryToken   selected category token
     * @param modNamespace    mod namespace (empty for "All")
     * @param toggleExpandOnly true if the click hit a expand/collapse chevron
     */
    public record CategoryClick(
            String categoryToken,
            String modNamespace,
            boolean toggleExpandOnly) {}

    private CategoryTypes() {}
}
