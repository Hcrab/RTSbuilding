package com.rtsbuilding.rtsbuilding.server.service;

/**
 * Record for world scan results of suspended (pending) placement jobs.
 *
 * <p>When the player clicks the restart button, the server performs a world scan via {@link RtsPendingPlacementService#scanPendingJob}
 * on the remaining positions of the pending job, obtaining various statistics.
 * This result is cached and consumed by the client, used to display scan details and restart strategy decisions on the panel.
 *
 * @param itemId             The item ID being placed (e.g. {@code "minecraft:diamond_block"})
 * @param itemLabel          The localized display name of the item (optional, client uses itemId when empty)
 * @param totalRemaining     Total remaining positions for the job (including placed and conflicting slots)
 * @param alreadyPlacedCount Number of positions within range that already have the same block type (manually placed by the user)
 * @param conflictCount      Number of positions within range that have different blocks (conflicting slots, need skip or overwrite)
 * @param availableItems     Available quantity of this item in the current storage system (including player inventory)
 * @param neededItems        Number of items actually needed to extract from storage for restart (= totalRemaining - alreadyPlacedCount)
 * @param missingItems       Number of missing items (= neededItems - availableItems, ≤0 means sufficient)
 * @param workflowEntryId    Target workflow entry ID, used to locate the corresponding pending job
 *
 * <p><b>Derived methods:</b>
 * <ul>
 *   <li>{@link #hasEnoughItems()} — Returns {@code true} when {@code missingItems <= 0}</li>
 *   <li>{@link #hasConflicts()} — Returns {@code true} when conflicting blocks exist</li>
 *   <li>{@link #effectivePlaceCount()} — Actual number needing placement ({@code totalRemaining - alreadyPlacedCount})</li>
 * </ul>
 */
public record RtsResumeScanResult(
        String itemId,
        String itemLabel,
        int totalRemaining,
        int alreadyPlacedCount,
        int conflictCount,
        long availableItems,
        int neededItems,
        long missingItems,
        int workflowEntryId) {

    /**
     * Returns whether items are sufficient (no shortage).
     */
    public boolean hasEnoughItems() {
        return missingItems <= 0;
    }

    /**
     * Returns whether conflicting blocks exist.
     */
    public boolean hasConflicts() {
        return conflictCount > 0;
    }

    /**
     * Returns the actual number needing placement (after deducting already-existing same-type blocks, but not deducting inventory).
     */
    public int effectivePlaceCount() {
        return totalRemaining - alreadyPlacedCount;
    }
}
