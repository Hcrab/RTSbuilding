package com.rtsbuilding.rtsbuilding.server.plugin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lookup table for built-in RTS plugin definitions.
 *
 * <p>The registry is intentionally read-only at runtime in this production
 * pass. Recipes and item availability are the modpack extension point; a data
 * driven plugin-definition layer can be added later without changing service
 * callers.
 */
public final class RtsPluginRegistry {
    private static final Map<ResourceLocation, RtsPluginDefinition> BY_ID = new LinkedHashMap<>();
    private static final Map<ResourceLocation, RtsPluginDefinition> BY_ITEM = new LinkedHashMap<>();

    static {
        for (RtsPluginDefinition definition : BuiltInRtsPluginCatalog.definitions()) {
            BY_ID.put(definition.id(), definition);
            BY_ITEM.put(definition.itemId(), definition);
        }
    }

    private RtsPluginRegistry() {
    }

    public static Collection<RtsPluginDefinition> definitions() {
        return BY_ID.values();
    }

    public static RtsPluginDefinition byId(ResourceLocation id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static RtsPluginDefinition byItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? null : BY_ITEM.get(itemId);
    }

    public static boolean isPluginItem(ItemStack stack) {
        return byItem(stack) != null;
    }
}
