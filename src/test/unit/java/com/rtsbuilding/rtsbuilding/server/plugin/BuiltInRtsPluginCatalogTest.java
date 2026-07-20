package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.service.mining.RangeMiningHarvestTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInRtsPluginCatalogTest {
    @Test
    void definitionsHaveStableUniqueIdsAndItems() {
        var definitions = BuiltInRtsPluginCatalog.definitions();
        var ids = new HashSet<ResourceLocation>();
        var itemIds = new HashSet<ResourceLocation>();

        for (RtsPluginDefinition definition : definitions) {
            assertTrue(ids.add(definition.id()), "插件 id 重复: " + definition.id());
            assertTrue(itemIds.add(definition.itemId()), "插件物品 id 重复: " + definition.itemId());
        }
    }

    @Test
    void criticalGameplayFeaturesStayOnExpectedPlugins() {
        Map<ResourceLocation, RtsPluginDefinition> byId = definitionsById();

        assertEnables(byId, BuiltInRtsPluginCatalog.RTS_CONTROL_CORE,
                RtsFeature.CAMERA, RtsFeature.INTERACT);
        assertEnables(byId, BuiltInRtsPluginCatalog.REMOTE_CONTROL_PLUGIN,
                RtsFeature.REMOTE_PLACE, RtsFeature.REMOTE_BREAK, RtsFeature.ROTATE_BLOCK);
        assertEnables(byId, BuiltInRtsPluginCatalog.STORAGE_INTEGRATION_PLUGIN,
                RtsFeature.LINK_STORAGE, RtsFeature.STORAGE_BROWSER,
                RtsFeature.AUTO_STORE_MINED_DROPS, RtsFeature.FUNNEL,
                RtsFeature.FLUID_HANDLING, RtsFeature.REMOTE_GUI_BINDING);
        assertEnables(byId, BuiltInRtsPluginCatalog.CHAIN_BREAK_PLUGIN, RtsFeature.ULTIMINE);
        assertEnables(byId, BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN,
                RtsFeature.AREA_MINE, RtsFeature.AREA_DESTROY);
        assertFalse(byId.get(BuiltInRtsPluginCatalog.CHAIN_BREAK_PLUGIN).enables(RtsFeature.AREA_MINE),
                "连锁挖掘插件不得隐式解锁范围挖掘");
        assertFalse(byId.get(BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN).enables(RtsFeature.ULTIMINE),
                "范围破坏插件不得隐式解锁连锁挖掘");
        assertEnables(byId, BuiltInRtsPluginCatalog.BLUEPRINT_PLUGIN, RtsFeature.BLUEPRINTS);
    }

    @Test
    void rangeExtensionPluginsKeepOrderedRadiusProgression() {
        Map<ResourceLocation, RtsPluginDefinition> byId = definitionsById();

        assertRange(byId, BuiltInRtsPluginCatalog.RANGE_EXTENSION_I, 16);
        assertRange(byId, BuiltInRtsPluginCatalog.RANGE_EXTENSION_II, 32);
        assertRange(byId, BuiltInRtsPluginCatalog.RANGE_EXTENSION_III, 48);
        assertRange(byId, BuiltInRtsPluginCatalog.RANGE_EXTENSION_MAX, Integer.MAX_VALUE);
    }

    @Test
    void harvestTierPluginsMapToTheFourPlayerFacingLimits() {
        Map<ResourceLocation, RtsPluginDefinition> byId = definitionsById();

        assertHarvestTier(byId, BuiltInRtsPluginCatalog.HARVEST_TIER_STONE,
                RangeMiningHarvestTier.STONE);
        assertHarvestTier(byId, BuiltInRtsPluginCatalog.HARVEST_TIER_IRON,
                RangeMiningHarvestTier.IRON);
        assertHarvestTier(byId, BuiltInRtsPluginCatalog.HARVEST_TIER_DIAMOND,
                RangeMiningHarvestTier.DIAMOND);
        assertHarvestTier(byId, BuiltInRtsPluginCatalog.HARVEST_TIER_UNLIMITED,
                RangeMiningHarvestTier.UNLIMITED);
    }

    private static Map<ResourceLocation, RtsPluginDefinition> definitionsById() {
        return BuiltInRtsPluginCatalog.definitions().stream()
                .collect(Collectors.toMap(RtsPluginDefinition::id, Function.identity()));
    }

    private static void assertEnables(Map<ResourceLocation, RtsPluginDefinition> byId,
            ResourceLocation pluginId, RtsFeature... features) {
        RtsPluginDefinition definition = byId.get(pluginId);
        assertTrue(definition != null, "缺少内置插件定义: " + pluginId);
        for (RtsFeature feature : features) {
            assertTrue(definition.enables(feature), pluginId + " 缺少功能门禁: " + feature);
        }
    }

    private static void assertRange(Map<ResourceLocation, RtsPluginDefinition> byId,
            ResourceLocation pluginId, int radius) {
        RtsPluginDefinition definition = byId.get(pluginId);
        assertTrue(definition != null, "缺少范围插件定义: " + pluginId);
        assertEquals(RtsPluginFamily.RANGE_EXTENSION, definition.family());
        assertEquals(radius, definition.radiusBlocks());
    }

    private static void assertHarvestTier(Map<ResourceLocation, RtsPluginDefinition> byId,
            ResourceLocation pluginId, RangeMiningHarvestTier tier) {
        RtsPluginDefinition definition = byId.get(pluginId);
        assertTrue(definition != null, "缺少采掘等级插件定义: " + pluginId);
        assertEquals(RtsPluginFamily.HARVEST_TIER, definition.family());
        assertEquals(tier, definition.harvestTier());
    }
}
