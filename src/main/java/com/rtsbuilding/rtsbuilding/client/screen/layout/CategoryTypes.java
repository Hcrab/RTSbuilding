package com.rtsbuilding.rtsbuilding.client.screen.layout;

/**
 * Container for category-browsing data types.
 * <p>
 * Groups the row model and click-result record that drive the category
 * tree rendered inside the bottom panel's category sidebar.
 */
public final class CategoryTypes {

    /**
     * A single category row in the bottom-panel category tree.
     * <p>
     * Each row represents one mod namespace or a tab within a mod.
     * Indentation is controlled by {@code depth} (0 = mod, 1 = tab),
     * and expandable/expanded flags determine whether the chevron icon
     * is shown and whether child rows are visible.
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
     */
    public record CategoryClick(
            String categoryToken,
            String modNamespace,
            boolean toggleExpandOnly) {}

    private CategoryTypes() {}
}
