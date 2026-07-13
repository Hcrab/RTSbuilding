package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalPluginIndependenceTest {
    @Test
    void chainAndAreaPluginsExposeIndependentFeatures() {
        RtsPluginDefinition chain = definition(BuiltInRtsPluginCatalog.CHAIN_BREAK_PLUGIN);
        RtsPluginDefinition area = definition(BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN);

        assertTrue(chain.enables(RtsFeature.ULTIMINE));
        assertFalse(chain.enables(RtsFeature.AREA_MINE));
        assertFalse(chain.enables(RtsFeature.AREA_DESTROY));
        assertTrue(area.enables(RtsFeature.AREA_MINE));
        assertTrue(area.enables(RtsFeature.AREA_DESTROY));
        assertFalse(area.enables(RtsFeature.ULTIMINE));
    }

    private static RtsPluginDefinition definition(net.minecraft.resources.ResourceLocation id) {
        return BuiltInRtsPluginCatalog.definitions().stream()
                .filter(definition -> definition.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
