package com.rtsbuilding.rtsbuilding.server.storage.model;

/**
 * UI recent entry snapshot.
 *
 * <p>Records a summary of items/fluids the player recently viewed or operated on, used for "recently used" list rendering.
 * This record reflects UI history, not authoritative item/fluid storage counts.
 *
 * @param id       The registry ID of the item/fluid (e.g. {@code "minecraft:diamond"})
 * @param amount   The visible amount
 * @param capacity Capacity (fluid only; 0 for items)
 * @param kind     Category marker: defined by {@code S2CRtsStoragePagePayload.RECENT_ITEM_*} constants
 */
public record RecentEntry(String id, long amount, long capacity, byte kind) {
}
