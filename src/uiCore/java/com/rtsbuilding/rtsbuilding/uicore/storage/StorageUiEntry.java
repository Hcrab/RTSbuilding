package com.rtsbuilding.rtsbuilding.uicore.storage;

/** 当前可见的一条绑定储存行，不持有 ItemStack 或 BlockPos。 */
public final class StorageUiEntry {
    public final String stableKey;
    public final String label;
    public final String position;
    public final int priority;
    public final boolean extractOnly;
    public final boolean worldAvailable;
    public final String itemId;

    public StorageUiEntry(String stableKey, String label, String position, int priority,
            boolean extractOnly, boolean worldAvailable, String itemId) {
        this.stableKey = safe(stableKey);
        this.label = safe(label);
        this.position = safe(position);
        this.priority = clamp(priority);
        this.extractOnly = extractOnly;
        this.worldAvailable = worldAvailable;
        this.itemId = safe(itemId);
    }

    StorageUiEntry withPriority(int value) {
        return new StorageUiEntry(stableKey, label, position, value,
                extractOnly, worldAvailable, itemId);
    }
    StorageUiEntry toggledExtract() {
        return new StorageUiEntry(stableKey, label, position, priority,
                !extractOnly, worldAvailable, itemId);
    }
    private static int clamp(int value) { return Math.max(-9999, Math.min(9999, value)); }
    private static String safe(String value) { return value == null ? "" : value; }
}
