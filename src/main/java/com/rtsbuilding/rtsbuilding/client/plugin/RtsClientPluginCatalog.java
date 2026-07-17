package com.rtsbuilding.rtsbuilding.client.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Client display helper for recognizing built-in RTS plugin items.
 *
 * <p>This is not an authority surface. It only lets inventory screens highlight
 * plugin-looking items and send install requests. The server plugin service is
 * still the only place that decides whether the request is valid.
 */
public final class RtsClientPluginCatalog {
    private static final Set<Identifier> PLUGIN_ITEMS = Set.of(
            id("rts_control_core"),
            id("remote_control_plugin"),
            id("storage_integration_plugin"),
            id("craft_terminal_plugin"),
            id("chain_break_plugin"),
            id("area_destroy_plugin"),
            id("blueprint_plugin"),
            id("range_culling_plugin"),
            id("field_deployment_plugin"),
            id("range_extension_i"),
            id("range_extension_ii"),
            id("range_extension_iii"),
            id("range_extension_max"));

    private RtsClientPluginCatalog() {
    }

    public static boolean isPluginItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && PLUGIN_ITEMS.contains(itemId);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, path);
    }
}
