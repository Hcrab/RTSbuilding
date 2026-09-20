package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.test.MinecraftTestBootstrapExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 覆盖 Forge 蓝图材料静态分析的身份缓存、流体/缺失方块和动态库存重算。 */
@ExtendWith(MinecraftTestBootstrapExtension.class)
class BlueprintMaterialAnalysisCacheTest {
    private static List<Fluid> testFluids;
    private static Map<Fluid, List<TagKey<Fluid>>> originalFluidTags;

    @BeforeAll
    static void installFluidTags() {
        // 延迟到 MinecraftTestBootstrapExtension 完成注册表引导之后，避免类初始化提前触碰 Fluid。
        testFluids = List.of(
                Fluids.WATER, Fluids.FLOWING_WATER, Fluids.LAVA, Fluids.FLOWING_LAVA);
        originalFluidTags = new IdentityHashMap<>();
        for (Fluid fluid : testFluids) {
            originalFluidTags.put(fluid, fluid.builtInRegistryHolder().tags().toList());
        }
        try {
            // Forge 1.20.1 的 FluidState.is 会读取 Fluid 缓存的真实 holder；
            // 测试引导中的 Forge wrapper/tag manager 与 BuiltInRegistries 不是同一层，
            // 因而直接给这些 holder 绑定测试 tag 才能覆盖生产读取路径。
            bindAdditionalTag(Fluids.WATER, FluidTags.WATER);
            bindAdditionalTag(Fluids.FLOWING_WATER, FluidTags.WATER);
            bindAdditionalTag(Fluids.LAVA, FluidTags.LAVA);
            bindAdditionalTag(Fluids.FLOWING_LAVA, FluidTags.LAVA);
        } catch (RuntimeException | Error failure) {
            restoreOriginalFluidTags();
            throw failure;
        }
    }

    @AfterAll
    static void restoreFluidTags() {
        try {
            restoreOriginalFluidTags();
        } finally {
            originalFluidTags = null;
        }
    }

    private static void bindAdditionalTag(Fluid fluid, TagKey<Fluid> tag) {
        List<TagKey<Fluid>> tags = new ArrayList<>(originalFluidTags.get(fluid));
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
        fluid.builtInRegistryHolder().bindTags(tags);
    }

    private static void restoreOriginalFluidTags() {
        if (originalFluidTags == null) {
            return;
        }
        originalFluidTags.forEach((fluid, tags) -> fluid.builtInRegistryHolder().bindTags(tags));
    }

    @Test
    void staticAnalysisCountsFluidsSkipsMissingBlocksAndPreservesCostOrder() {
        RtsBlueprint blueprint = blueprint(
                "fluid-and-missing",
                block(Blocks.WATER),
                block(Blocks.LAVA),
                RtsBlueprintBlock.missing(BlockPos.ZERO, "example:missing", new CompoundTag()),
                block(Blocks.STONE));
        assertSame(Fluids.WATER, Blocks.WATER.defaultBlockState().getFluidState().getType());
        assertSame(Fluids.LAVA, Blocks.LAVA.defaultBlockState().getFluidState().getType());
        assertTrue(Blocks.WATER.defaultBlockState().getFluidState().is(FluidTags.WATER));
        assertTrue(Blocks.LAVA.defaultBlockState().getFluidState().is(FluidTags.LAVA));
        BlueprintMaterialAnalysis analysis = new BlueprintMaterialAnalysisCache().get(blueprint);

        assertEquals(new FluidRequirement(1, 1), analysis.fluidRequirement());
        assertEquals(
                List.of(
                        BlueprintMaterialAnalysis.CostKind.WATER,
                        BlueprintMaterialAnalysis.CostKind.LAVA,
                        BlueprintMaterialAnalysis.CostKind.SKIPPED,
                        BlueprintMaterialAnalysis.CostKind.ITEMS),
                analysis.blockCosts().stream()
                        .map(BlueprintMaterialAnalysis.BlockCost::kind).toList());

        ResourceLocation stone = analysis.blockCosts().get(3).itemIds().get(0);
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
                "ordered", block(Blocks.STONE), block(Blocks.DIRT), block(Blocks.STONE)));
        ResourceLocation stone = analysis.blockCosts().get(0).itemIds().get(0);
        ResourceLocation dirt = analysis.blockCosts().get(1).itemIds().get(0);

        assertEquals(2L, analysis.buildableBlockCount(Map.of(stone, 1L, dirt, 1L), false, 0L));
        assertEquals(3L, analysis.buildableBlockCount(Map.of(stone, 2L, dirt, 1L), false, 0L));
        Map<ResourceLocation, Long> mutableInventory =
                new HashMap<>(Map.of(stone, 2L, dirt, 1L));
        assertEquals(3L, analysis.buildableBlockCount(mutableInventory, false, 0L));
        mutableInventory.put(stone, 0L);
        assertEquals(1L, analysis.buildableBlockCount(mutableInventory, false, 0L));
        assertEquals(List.of(stone, dirt, stone), analysis.blockCosts().stream()
                .flatMap(cost -> cost.itemIds().stream()).toList());
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
        return RtsBlueprint.create(name, name + ".nbt", BlueprintFormat.VANILLA_NBT,
                new Vec3i(Math.max(1, blocks.length), 1, 1), List.of(blocks));
    }

    private static RtsBlueprintBlock block(Block block) {
        // Forge 纯 JVM 注册表引导不经过完整方块缓存阶段，必须建立真实流体状态缓存。
        var state = block.defaultBlockState();
        state.initCache();
        return new RtsBlueprintBlock(BlockPos.ZERO, state, new CompoundTag());
    }
}
