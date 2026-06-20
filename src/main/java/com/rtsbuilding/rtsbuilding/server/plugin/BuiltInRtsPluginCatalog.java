package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Built-in production catalog for survival-balance plugins.
 *
 * <p>Modpacks are expected to rebalance these entries primarily by changing the
 * real item recipes. The Java catalog stays deliberately small and auditable:
 * it maps items to RTS feature gates and numeric limits.
 */
public final class BuiltInRtsPluginCatalog {
    public static final ResourceLocation RTS_CONTROL_CORE = id("rts_control_core");
    public static final ResourceLocation REMOTE_CONTROL_PLUGIN = id("remote_control_plugin");
    public static final ResourceLocation STORAGE_INTEGRATION_PLUGIN = id("storage_integration_plugin");
    public static final ResourceLocation CRAFT_TERMINAL_PLUGIN = id("craft_terminal_plugin");
    public static final ResourceLocation CHAIN_BREAK_PLUGIN = id("chain_break_plugin");
    public static final ResourceLocation AREA_DESTROY_PLUGIN = id("area_destroy_plugin");
    public static final ResourceLocation BLUEPRINT_PLUGIN = id("blueprint_plugin");
    public static final ResourceLocation FIELD_DEPLOYMENT_PLUGIN = id("field_deployment_plugin");
    public static final ResourceLocation RANGE_EXTENSION_I = id("range_extension_i");
    public static final ResourceLocation RANGE_EXTENSION_II = id("range_extension_ii");
    public static final ResourceLocation RANGE_EXTENSION_III = id("range_extension_iii");
    public static final ResourceLocation RANGE_EXTENSION_MAX = id("range_extension_max");

    private BuiltInRtsPluginCatalog() {
    }

    public static List<RtsPluginDefinition> definitions() {
        return List.of(
                definition(RTS_CONTROL_CORE, RtsPluginFamily.UNIQUE,
                        EnumSet.of(RtsFeature.CAMERA, RtsFeature.INTERACT), 16, false),
                definition(REMOTE_CONTROL_PLUGIN, RtsPluginFamily.UNIQUE,
                        EnumSet.of(RtsFeature.REMOTE_PLACE, RtsFeature.REMOTE_BREAK, RtsFeature.ROTATE_BLOCK), 0, false),
                definition(STORAGE_INTEGRATION_PLUGIN, RtsPluginFamily.UNIQUE,
                        EnumSet.of(RtsFeature.LINK_STORAGE, RtsFeature.STORAGE_BROWSER,
                                RtsFeature.AUTO_STORE_MINED_DROPS, RtsFeature.FUNNEL,
                                RtsFeature.FLUID_HANDLING, RtsFeature.REMOTE_GUI_BINDING), 0, false),
                definition(CRAFT_TERMINAL_PLUGIN, RtsPluginFamily.UNIQUE,
                        EnumSet.of(RtsFeature.CRAFT_TERMINAL, RtsFeature.JEI_TRANSFER), 0, false),
                definition(CHAIN_BREAK_PLUGIN, RtsPluginFamily.UNIQUE,
                        EnumSet.of(RtsFeature.ULTIMINE), 0, false),
                definition(AREA_DESTROY_PLUGIN, RtsPluginFamily.UNIQUE,
                        EnumSet.of(RtsFeature.AREA_DESTROY), 0, false),
                definition(BLUEPRINT_PLUGIN, RtsPluginFamily.UNIQUE,
                        EnumSet.of(RtsFeature.BLUEPRINTS), 0, false),
                definition(FIELD_DEPLOYMENT_PLUGIN, RtsPluginFamily.UNIQUE,
                        Set.of(), 0, true),
                definition(RANGE_EXTENSION_I, RtsPluginFamily.RANGE_EXTENSION, Set.of(), 16, false),
                definition(RANGE_EXTENSION_II, RtsPluginFamily.RANGE_EXTENSION, Set.of(), 32, false),
                definition(RANGE_EXTENSION_III, RtsPluginFamily.RANGE_EXTENSION, Set.of(), 48, false),
                definition(RANGE_EXTENSION_MAX, RtsPluginFamily.RANGE_EXTENSION, Set.of(), Integer.MAX_VALUE, false)
        );
    }

    private static RtsPluginDefinition definition(ResourceLocation pluginId, RtsPluginFamily family,
            Set<RtsFeature> features, int radiusBlocks, boolean fieldDeployment) {
        return new RtsPluginDefinition(pluginId, pluginId, family, features, radiusBlocks, fieldDeployment);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(RtsbuildingMod.MODID, path);
    }
}
