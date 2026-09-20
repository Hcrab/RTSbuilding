package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

/**
 * 蓝图材料检查所需的静态分析结果。
 *
 * <p>本类只读取蓝图身份对应的方块状态，并把流体统计和按原始方块顺序排列的
 * 材料成本序列保存下来。它不读取玩家背包、远程仓库或创造模式；这些动态数据
 * 必须由 {@link BlueprintMaterialInspector} 在每次检查时提供。</p>
 *
 * <p>保留每个方块的成本顺序是有意的：材料检查必须继续模拟旧实现的逐块扣料，
 * 不能为了聚合统计而改变有限库存下的建造数量。</p>
 */
final class BlueprintMaterialAnalysis {
    private static final BlueprintMaterialAnalysis EMPTY = new BlueprintMaterialAnalysis(
            FluidRequirement.EMPTY,
            List.of());

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

    static BlueprintMaterialAnalysis empty() {
        return EMPTY;
    }

    /**
     * 为一个蓝图生成一次静态材料分析；调用方负责按蓝图身份缓存结果。
     */
    static BlueprintMaterialAnalysis analyze(RtsBlueprint blueprint) {
        if (blueprint == null) {
            return EMPTY;
        }
        if (blueprint.blocks() == null || blueprint.blocks().isEmpty()) {
            return new BlueprintMaterialAnalysis(FluidRequirement.EMPTY, List.of());
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
            if (cost.kind() == CostKind.WATER) {
                waterBlocks++;
            } else if (cost.kind() == CostKind.LAVA) {
                lavaBlocks++;
            }
            costs.add(cost);
        }

        return new BlueprintMaterialAnalysis(
                new FluidRequirement(waterBlocks, lavaBlocks),
                costs);
    }

    private static BlockCost costOf(RtsBlueprintBlock block) {
        if (block.state().getFluidState().is(FluidTags.WATER)) return BlockCost.WATER;
        if (block.state().getFluidState().is(FluidTags.LAVA)) return BlockCost.LAVA;
        List<ResourceLocation> ids = RtsBlueprint.materialItemIds(block);
        return ids.isEmpty() ? BlockCost.SKIPPED : BlockCost.items(ids);
    }

    FluidRequirement fluidRequirement() {
        return fluidRequirement;
    }

    /**
     * 返回不可变的原始顺序成本序列，仅供离线验证和顺序扣料算法使用。
     */
    List<BlockCost> blockCosts() {
        return blockCosts;
    }

    /**
     * 用一次动态库存快照计算可建造数量；不会重新读取蓝图中的方块状态。
     */
    long buildableBlockCount(
            Map<ResourceLocation, Long> availableItems,
            boolean waterReady,
            long availableLavaBuckets) {
        // 库存仍逐次实时读取，只在输入完全相同时复用扣料模拟，静止帧不再走整条成本序列。
        if (lastAvailableItems != null && lastAvailableItems.equals(availableItems)
                && lastWaterReady == waterReady && lastLavaBuckets == availableLavaBuckets) {
            return lastBuildable;
        }
        Map<ResourceLocation, Long> remainingItems = new HashMap<>();
        if (availableItems != null) {
            for (Map.Entry<ResourceLocation, Long> entry : availableItems.entrySet()) {
                ResourceLocation id = entry.getKey();
                Long amount = entry.getValue();
                if (id != null && amount != null) {
                    remainingItems.put(id, Math.max(0L, amount));
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
                case SKIPPED -> {
                    // 缺失或没有可放置材料的方块保持不可建造。
                }
                case WATER -> {
                    // 与原材料检查保持一致：水只要求达到最低桶数，不逐块扣除。
                    if (waterReady) {
                        buildable++;
                    }
                }
                case LAVA -> {
                    if (remainingLava > 0L) {
                        remainingLava--;
                        buildable++;
                    }
                }
                case ITEMS -> {
                    if (!hasAllItems(remainingItems, cost.itemIds())) {
                        continue;
                    }
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

    private static boolean hasAllItems(Map<ResourceLocation, Long> remainingItems, List<ResourceLocation> itemIds) {
        for (ResourceLocation id : itemIds) {
            if (remainingItems.getOrDefault(id, 0L) <= 0L) {
                return false;
            }
        }
        return true;
    }

    enum CostKind {
        SKIPPED,
        WATER,
        LAVA,
        ITEMS
    }

    record BlockCost(CostKind kind, List<ResourceLocation> itemIds) {
        private static final BlockCost SKIPPED = new BlockCost(CostKind.SKIPPED, List.of());
        private static final BlockCost WATER = new BlockCost(CostKind.WATER, List.of());
        private static final BlockCost LAVA = new BlockCost(CostKind.LAVA, List.of());

        BlockCost {
            itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
        }

        private static BlockCost items(List<ResourceLocation> itemIds) {
            return new BlockCost(CostKind.ITEMS, itemIds);
        }
    }
}
