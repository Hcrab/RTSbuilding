package com.rtsbuilding.rtsbuilding.server.service.page;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPageSharedHelpersTest {
    @Test
    void matchesItemIdAndModNamespaceQueries() {
        ResourceLocation grid = new ResourceLocation("refinedstorage", "grid");

        assertTrue(RtsPageSharedHelpers.matchesSearchQuery(
                grid, "refinedstorage:grid", "Grid", "refined", false, Set.of()));
        assertTrue(RtsPageSharedHelpers.matchesSearchQuery(
                grid, "refinedstorage:grid", "Grid", "@refined", false, Set.of()));
        assertFalse(RtsPageSharedHelpers.matchesSearchQuery(
                grid, "refinedstorage:grid", "Grid", "@minecraft", false, Set.of()));
    }

    @Test
    void usesClientProvidedLocalizedMatchesBeforeServerLabelFallbacks() {
        ResourceLocation planks = new ResourceLocation("minecraft", "oak_planks");

        assertTrue(RtsPageSharedHelpers.matchesSearchQuery(
                planks, "minecraft:oak_planks", "Oak Planks", "xiangmu", false,
                Set.of("minecraft:oak_planks")));
    }

    @Test
    void pinyinSearchIsExplicitlyGated() {
        ResourceLocation planks = new ResourceLocation("minecraft", "oak_planks");

        assertFalse(RtsPageSharedHelpers.matchesSearchQuery(
                planks, "minecraft:oak_planks", "橡木木板", "xiangmu", false, Set.of()));
        assertTrue(RtsPageSharedHelpers.matchesSearchQuery(
                planks, "minecraft:oak_planks", "橡木木板", "xiangmu", true, Set.of()));
    }
}
