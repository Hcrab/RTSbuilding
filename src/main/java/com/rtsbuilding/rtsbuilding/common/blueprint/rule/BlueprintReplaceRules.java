package com.rtsbuilding.rtsbuilding.common.blueprint.rule;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 1.12 没有数据包方块标签，因此以稳定注册名集合表达同一软替换规则。 */
public final class BlueprintReplaceRules {
    public static final ResourceLocation SOFT_REPLACEABLE =
            new ResourceLocation(RtsbuildingMod.MODID, "blueprint_soft_replaceable");

    private static final Set<ResourceLocation> VANILLA_SOFT_REPLACEABLE = Collections.unmodifiableSet(
            new HashSet<ResourceLocation>(Arrays.asList(
                    vanilla("tallgrass"), vanilla("yellow_flower"), vanilla("red_flower"),
                    vanilla("double_plant"), vanilla("deadbush"), vanilla("brown_mushroom"),
                    vanilla("red_mushroom"), vanilla("vine"), vanilla("snow_layer"),
                    vanilla("waterlily"))));

    private BlueprintReplaceRules() {}

    public static boolean canBlueprintReplace(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR || state.getMaterial().isReplaceable()) return true;
        return VANILLA_SOFT_REPLACEABLE.contains(Block.REGISTRY.getNameForObject(state.getBlock()));
    }

    private static ResourceLocation vanilla(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
