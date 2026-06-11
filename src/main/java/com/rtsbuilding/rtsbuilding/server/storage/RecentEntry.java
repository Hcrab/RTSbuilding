package com.rtsbuilding.rtsbuilding.server.storage;


/**
 * Snapshot used by the UI's recent list.
 *
 * <p>It records what the player recently saw or moved; it is not a source of
 * truth for item or fluid storage counts.
 */
public record RecentEntry(String id, long amount, long capacity, byte kind) {
}
