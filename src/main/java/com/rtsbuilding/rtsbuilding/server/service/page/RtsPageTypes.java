package com.rtsbuilding.rtsbuilding.server.service.page;

import net.minecraft.world.item.ItemStack;

/**
 * Package-private data types for the page-building sub-package.
 */
// ---- Entry types -----------------------------------------------------------

record Entry(ItemStack stack, String itemId, String namespace, String path, String label, long count) {
}

record FluidEntry(String fluidId, String namespace, String path, long amount, long capacity) {
}

record LinkedRefPayload(
        java.util.List<Long> positions,
        java.util.List<String> names,
        java.util.List<Byte> modes,
        java.util.List<Integer> priorities,
        java.util.List<String> iconItemIds,
        java.util.List<Boolean> worldAvailable) {
}

// ---- Category selection -----------------------------------------------------

record CategorySelection(CategorySelectionType type, String namespace, String tabKey) {
    static CategorySelection all() {
        return new CategorySelection(CategorySelectionType.ALL, "", "");
    }

    static CategorySelection mod(String namespace) {
        return new CategorySelection(CategorySelectionType.MOD, namespace, "");
    }

    static CategorySelection tab(String namespace, String tabKey) {
        return new CategorySelection(CategorySelectionType.TAB, namespace, tabKey);
    }

    boolean isCreativeTab() {
        return this.type == CategorySelectionType.TAB;
    }

    boolean matches(String namespace, java.util.Set<String> tabs) {
        return switch (this.type) {
            case ALL -> true;
            case MOD -> this.namespace.equals(namespace);
            case TAB -> this.namespace.equals(namespace) && tabs.contains(this.tabKey);
        };
    }
}

enum CategorySelectionType {
    ALL,
    MOD,
    TAB
}
