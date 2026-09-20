package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图材料检查所需的静态分析结果。
 *
 * <p>只保存蓝图身份对应的流体统计和原始方块顺序成本；玩家背包、远程仓库和创造
 * 模式仍由 {@link BlueprintMaterialInspector} 每次读取，因此缓存不会冻结动态库存。</p>
 */
final class BlueprintMaterialAnalysis {
    private static final BlueprintMaterialAnalysis EMPTY = new BlueprintMaterialAnalysis(
            FluidRequirement.EMPTY, List.of());

    private final FluidRequirement fluidRequirement;
    private final List<BlockCost> blockCosts;
    private Map<ResourceLocation, Long> lastAvailableItems;
    private boolean lastWaterReady;
    private long lastLavaBuckets;
    private long lastBuildable;

    private BlueprintMaterialAnalysis(FluidRequirement fluidRequirement, List<BlockCost> blockCosts) {
        this.fluidRequirement = fluidRequirement;
        this.blockCosts = List.copyOf(blockCosts);
    }

    static BlueprintMaterialAnalysis empty() { return EMPTY; }

    static BlueprintMaterialAnalysis analyze(RtsBlueprint blueprint) {
        if (blueprint == null || blueprint.blocks() == null || blueprint.blocks().isEmpty()) {
            return blueprint == null ? EMPTY
                    : new BlueprintMaterialAnalysis(FluidRequirement.EMPTY, List.of());
        }
        int waterBlocks = 0;
        int lavaBlocks = 0;
        List<BlockCost> costs = new ArrayList<>(blueprint.blocks().size());
        Map<BlockState, BlockCost> palette = new IdentityHashMap<>();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block == null || block.isMissingBlock() || block.state() == null) {
                costs.add(BlockCost.SKIPPED);
                continue;
            }
            BlockCost cost = palette.computeIfAbsent(block.state(), ignored -> costOf(block));
            if (cost.kind() == CostKind.WATER) waterBlocks++;
            if (cost.kind() == CostKind.LAVA) lavaBlocks++;
            costs.add(cost);
        }
        return new BlueprintMaterialAnalysis(new FluidRequirement(waterBlocks, lavaBlocks), costs);
    }

    private static BlockCost costOf(RtsBlueprintBlock block) {
        if (block.state().getFluidState().is(FluidTags.WATER)) return BlockCost.WATER;
        if (block.state().getFluidState().is(FluidTags.LAVA)) return BlockCost.LAVA;
        List<ResourceLocation> ids = RtsBlueprint.materialItemIds(block);
        return ids.isEmpty() ? BlockCost.SKIPPED : BlockCost.items(ids);
    }

    FluidRequirement fluidRequirement() { return fluidRequirement; }
    List<BlockCost> blockCosts() { return blockCosts; }

    long buildableBlockCount(Map<ResourceLocation, Long> availableItems,
            boolean waterReady, long availableLavaBuckets) {
        if (lastAvailableItems != null && lastAvailableItems.equals(availableItems)
                && lastWaterReady == waterReady && lastLavaBuckets == availableLavaBuckets) {
            return lastBuildable;
        }
        Map<ResourceLocation, Long> remainingItems = new HashMap<>();
        if (availableItems != null) {
            for (Map.Entry<ResourceLocation, Long> entry : availableItems.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    remainingItems.put(entry.getKey(), Math.max(0L, entry.getValue()));
                }
            }
        }
        lastAvailableItems = Map.copyOf(remainingItems);
        lastWaterReady = waterReady;
        lastLavaBuckets = availableLavaBuckets;
        long remainingLava = Math.max(0L, availableLavaBuckets);
        long buildable = 0L;
        for (BlockCost cost : blockCosts) {
            switch (cost.kind()) {
                case SKIPPED -> { }
                case WATER -> { if (waterReady) buildable++; }
                case LAVA -> { if (remainingLava > 0L) { remainingLava--; buildable++; } }
                case ITEMS -> {
                    boolean ready = true;
                    for (ResourceLocation id : cost.itemIds()) {
                        if (remainingItems.getOrDefault(id, 0L) <= 0L) { ready = false; break; }
                    }
                    if (!ready) continue;
                    for (ResourceLocation id : cost.itemIds()) {
                        remainingItems.put(id, remainingItems.getOrDefault(id, 0L) - 1L);
                    }
                    buildable++;
                }
            }
        }
        lastBuildable = buildable;
        return buildable;
    }

    enum CostKind { SKIPPED, WATER, LAVA, ITEMS }

    record BlockCost(CostKind kind, List<ResourceLocation> itemIds) {
        private static final BlockCost SKIPPED = new BlockCost(CostKind.SKIPPED, List.of());
        private static final BlockCost WATER = new BlockCost(CostKind.WATER, List.of());
        private static final BlockCost LAVA = new BlockCost(CostKind.LAVA, List.of());

        BlockCost { itemIds = itemIds == null ? List.of() : List.copyOf(itemIds); }
        private static BlockCost items(List<ResourceLocation> ids) {
            return new BlockCost(CostKind.ITEMS, ids);
        }
    }
}
