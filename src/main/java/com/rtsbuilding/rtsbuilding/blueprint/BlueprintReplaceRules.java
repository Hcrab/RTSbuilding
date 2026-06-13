package com.rtsbuilding.rtsbuilding.blueprint;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class BlueprintReplaceRules {
    public static final TagKey<Block> SOFT_REPLACEABLE = TagKey.create(
            Registries.BLOCK,
            new ResourceLocation(RtsbuildingMod.MODID, "blueprint_soft_replaceable"));

    private static final Set<ResourceLocation> VANILLA_SOFT_REPLACEABLE = Set.of(
            vanilla("short_grass"),
            vanilla("tall_grass"),
            vanilla("fern"),
            vanilla("large_fern"),
            vanilla("dead_bush"),
            vanilla("dandelion"),
            vanilla("poppy"),
            vanilla("blue_orchid"),
            vanilla("allium"),
            vanilla("azure_bluet"),
            vanilla("red_tulip"),
            vanilla("orange_tulip"),
            vanilla("white_tulip"),
            vanilla("pink_tulip"),
            vanilla("oxeye_daisy"),
            vanilla("cornflower"),
            vanilla("lily_of_the_valley"),
            vanilla("torchflower"),
            vanilla("wither_rose"),
            vanilla("sunflower"),
            vanilla("lilac"),
            vanilla("rose_bush"),
            vanilla("peony"),
            vanilla("pitcher_plant"),
            vanilla("brown_mushroom"),
            vanilla("red_mushroom"),
            vanilla("crimson_roots"),
            vanilla("warped_roots"),
            vanilla("nether_sprouts"),
            vanilla("vine"),
            vanilla("cave_vines"),
            vanilla("cave_vines_plant"),
            vanilla("twisting_vines"),
            vanilla("twisting_vines_plant"),
            vanilla("weeping_vines"),
            vanilla("weeping_vines_plant"),
            vanilla("glow_lichen"),
            vanilla("hanging_roots"),
            vanilla("pink_petals"),
            vanilla("moss_carpet"),
            vanilla("snow"),
            vanilla("seagrass"),
            vanilla("tall_seagrass"));

    private BlueprintReplaceRules() {
    }

    public static boolean canBlueprintReplace(BlockState state) {
        if (state == null || state.isAir() || state.canBeReplaced() || state.is(SOFT_REPLACEABLE)) {
            return true;
        }
        return VANILLA_SOFT_REPLACEABLE.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static ResourceLocation vanilla(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
