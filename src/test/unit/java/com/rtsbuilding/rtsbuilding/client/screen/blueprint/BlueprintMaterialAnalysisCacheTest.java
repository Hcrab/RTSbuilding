package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 覆盖蓝图材料静态分析的身份缓存、流体/缺失方块处理和动态库存重算。
 */
class BlueprintMaterialAnalysisCacheTest {
    private static Map<TagKey<Fluid>, List<Holder<Fluid>>> originalFluidTags;
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapRegistries() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        originalFluidTags = BuiltInRegistries.FLUID.getTags().collect(java.util.stream.Collectors.toMap(
                pair -> pair.getFirst(), pair -> pair.getSecond().stream().toList()));
        Map<TagKey<Fluid>, List<Holder<Fluid>>> testTags = new java.util.HashMap<>(originalFluidTags);
        testTags.put(FluidTags.WATER, List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER),
                BuiltInRegistries.FLUID.wrapAsHolder(Fluids.FLOWING_WATER)));
        testTags.put(FluidTags.LAVA, List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA),
                BuiltInRegistries.FLUID.wrapAsHolder(Fluids.FLOWING_LAVA)));
        BuiltInRegistries.FLUID.bindTags(testTags);
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreFluidTags() {
        if (originalFluidTags != null) {
            BuiltInRegistries.FLUID.resetTags();
            BuiltInRegistries.FLUID.bindTags(originalFluidTags);
        }
    }

    @Test
    void staticAnalysisCountsFluidsSkipsMissingBlocksAndPreservesCostOrder() {
        RtsBlueprint blueprint = blueprint(
                "fluid-and-missing",
                block(Blocks.WATER),
                block(Blocks.LAVA),
                RtsBlueprintBlock.missing(BlockPos.ZERO, "example:missing", new CompoundTag()),
                block(Blocks.STONE));
        BlueprintMaterialAnalysis analysis = new BlueprintMaterialAnalysisCache().get(blueprint);

        assertEquals(new FluidRequirement(1, 1), analysis.fluidRequirement());
        assertEquals(
                List.of(
                        BlueprintMaterialAnalysis.CostKind.WATER,
                        BlueprintMaterialAnalysis.CostKind.LAVA,
                        BlueprintMaterialAnalysis.CostKind.SKIPPED,
                        BlueprintMaterialAnalysis.CostKind.ITEMS),
                analysis.blockCosts().stream().map(BlueprintMaterialAnalysis.BlockCost::kind).toList());

        ResourceLocation stone = analysis.blockCosts().get(3).itemIds().getFirst();
        assertEquals(3L, analysis.buildableBlockCount(Map.of(stone, 1L), true, 1L));
        assertEquals(2L, analysis.buildableBlockCount(Map.of(stone, 1L), false, 1L));
        assertEquals(1L, analysis.buildableBlockCount(Map.of(stone, 1L), false, 0L));
    }

    @Test
    void repeatedIdentityReusesAnalysisButEqualBlueprintObjectsRemainSeparate() {
        RtsBlueprintBlock sharedBlock = block(Blocks.STONE);
        RtsBlueprint first = blueprint("same", sharedBlock);
        RtsBlueprint equalButDistinct = blueprint("same", sharedBlock);
        BlueprintMaterialAnalysisCache cache = new BlueprintMaterialAnalysisCache();

        BlueprintMaterialAnalysis firstAnalysis = cache.get(first);
        assertSame(firstAnalysis, cache.get(first));
        assertEquals(first, equalButDistinct);
        assertNotSame(firstAnalysis, cache.get(equalButDistinct));
        assertEquals(2, cache.size());
    }

    @Test
    void dynamicInventoryChangesReuseStaticSequenceAndKeepSequentialConsumption() {
        BlueprintMaterialAnalysis analysis = new BlueprintMaterialAnalysisCache().get(blueprint(
                "ordered",
                block(Blocks.STONE),
                block(Blocks.DIRT),
                block(Blocks.STONE)));
        ResourceLocation stone = analysis.blockCosts().get(0).itemIds().getFirst();
        ResourceLocation dirt = analysis.blockCosts().get(1).itemIds().getFirst();

        assertEquals(2L, analysis.buildableBlockCount(Map.of(stone, 1L, dirt, 1L), false, 0L));
        assertEquals(3L, analysis.buildableBlockCount(Map.of(stone, 2L, dirt, 1L), false, 0L));
        Map<ResourceLocation, Long> mutableInventory = new java.util.HashMap<>(Map.of(stone, 2L, dirt, 1L));
        assertEquals(3L, analysis.buildableBlockCount(mutableInventory, false, 0L));
        mutableInventory.put(stone, 0L);
        assertEquals(1L, analysis.buildableBlockCount(mutableInventory, false, 0L));
        assertEquals(
                List.of(stone, dirt, stone),
                analysis.blockCosts().stream().flatMap(cost -> cost.itemIds().stream()).toList());
    }

    @Test
    void cacheIsBoundedAndEvictsLeastRecentlyUsedIdentity() {
        BlueprintMaterialAnalysisCache cache = new BlueprintMaterialAnalysisCache();
        RtsBlueprint first = blueprint("blueprint-0", block(Blocks.STONE));
        BlueprintMaterialAnalysis firstAnalysis = cache.get(first);
        for (int index = 1; index <= BlueprintMaterialAnalysisCache.DEFAULT_MAX_ENTRIES; index++) {
            cache.get(blueprint("blueprint-" + index, block(Blocks.STONE)));
        }

        assertEquals(BlueprintMaterialAnalysisCache.DEFAULT_MAX_ENTRIES, cache.size());
        assertNotSame(firstAnalysis, cache.get(first));
        assertEquals(BlueprintMaterialAnalysisCache.DEFAULT_MAX_ENTRIES, cache.size());
    }

    private static RtsBlueprint blueprint(String name, RtsBlueprintBlock... blocks) {
        return RtsBlueprint.create(
                name,
                name + ".nbt",
                BlueprintFormat.VANILLA_NBT,
                new Vec3i(Math.max(1, blocks.length), 1, 1),
                List.of(blocks));
    }

    private static RtsBlueprintBlock block(net.minecraft.world.level.block.Block block) {
        return new RtsBlueprintBlock(BlockPos.ZERO, block.defaultBlockState(), new CompoundTag());
    }
}
