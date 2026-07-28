package com.rtsbuilding.rtsbuilding.server.pipeline.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 预计算放置计划——将旋转、材质查找、流体成本、方块实体标签等
 * 不变操作一次性算好并缓存，避免搁置重试时重复计算。
 *
 * <p>调用方在初始化阶段调用 {@link #compute} 一次，
 * 之后 tick 阶段直接读取 precomputed 的 {@link PlacementPlan} 进行放置。</p>
 *
 * <p>计算结果不依赖运行时世界状态，可安全缓存并跨模块复用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * List<PlacementPlan> plans = BlockPlacementPlanner.compute(
 *         blueprint, anchor, centerOffset, ySteps, xSteps, zSteps);
 * // 之后 tick 中直接读 plan.target(), plan.state(), plan.items()...
 * }</pre>
 */
public final class BlockPlacementPlanner {

    private BlockPlacementPlanner() {
    }

    /**
     * 单个方块的预计算放置结果。
     * 包含放置所需的所有不会被世界状态改变的信息。
     *
     * @param target         旋转后的世界坐标
     * @param state          旋转后的方块状态
     * @param items          摆放所需的物品列表（空 = 不需要物品/仅流体）
     * @param fluidCost      流体成本（WATER / LAVA / EMPTY）
     * @param blockEntityTag 方块实体标签（可能为 null）
     */
    public static final class PlacementPlan {
        private final BlockPos target;
        private final IBlockState state;
        private final List<Item> items;
        @Nullable private final Fluid fluidCost;
        @Nullable private final NBTTagCompound blockEntityTag;

        public PlacementPlan(BlockPos target, IBlockState state, List<Item> items,
                @Nullable Fluid fluidCost, @Nullable NBTTagCompound blockEntityTag) {
            this.target = target.toImmutable();
            this.state = state;
            this.items = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.copyList(items);
            this.fluidCost = fluidCost;
            this.blockEntityTag = blockEntityTag == null ? null : blockEntityTag.copy();
        }

        public BlockPos target() { return target; }
        public IBlockState state() { return state; }
        public List<Item> items() { return items; }
        @Nullable public Fluid fluidCost() { return fluidCost; }
        @Nullable public NBTTagCompound blockEntityTag() {
            return blockEntityTag == null ? null : blockEntityTag.copy();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PlacementPlan)) return false;
            PlacementPlan that = (PlacementPlan) other;
            return java.util.Objects.equals(target, that.target)
                    && java.util.Objects.equals(state, that.state)
                    && java.util.Objects.equals(items, that.items)
                    && java.util.Objects.equals(fluidCost, that.fluidCost)
                    && java.util.Objects.equals(blockEntityTag, that.blockEntityTag);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(target, state, items, fluidCost, blockEntityTag);
        }

        @Override
        public String toString() {
            return "PlacementPlan[target=" + target + ", state=" + state + ", items=" + items
                    + ", fluidCost=" + fluidCost + ", blockEntityTag=" + blockEntityTag + "]";
        }
    }

    /**
     * 为整个蓝图计算所有方块的放置计划。
     *
     * @param blueprint    要放置的蓝图
     * @param anchor       锚点坐标
     * @param centerOffset 旋转中心偏移量
     * @param ySteps       Y 轴 90° 旋转步数
     * @param xSteps       X 轴 90° 旋转步数
     * @param zSteps       Z 轴 90° 旋转步数
     * @return 不可变的放置计划列表，与 {@code blueprint.blocks()} 索引一一对应
     * （缺失定义的块对应 null）
     */
    public static List<PlacementPlan> compute(
            RtsBlueprint blueprint,
            BlockPos anchor,
            BlockPos centerOffset,
            int ySteps, int xSteps, int zSteps) {

        List<RtsBlueprintBlock> blocks = blueprint.blocks();
        List<PlacementPlan> plans = new ArrayList<>(blocks.size());

        for (RtsBlueprintBlock block : blocks) {
            plans.add(computeOne(block, anchor, centerOffset, ySteps, xSteps, zSteps));
        }

        // 缺失方块使用 null 占位，List.copyOf 会拒绝 null。
        return Collections.unmodifiableList(plans);
    }

    /**
     * 只计算一个蓝图方块的放置计划。
     *
     * <p>统一任务引擎用它把大型蓝图的准备阶段切成受数量和纳秒预算约束的小步；
     * 本方法不读取世界，也不产生材料或方块副作用。</p>
     */
    @Nullable
    public static PlacementPlan computeOne(
            RtsBlueprintBlock block,
            BlockPos anchor,
            BlockPos centerOffset,
            int ySteps, int xSteps, int zSteps) {
        if (block == null || block.isMissingBlock()) return null;

        BlockPos target = anchor.offset(BlueprintTransform.rotateAroundCenter(
                block.relativePos(), ySteps, xSteps, zSteps, centerOffset));
        IBlockState state = BlueprintTransform.rotateState(block.state(), ySteps, xSteps, zSteps);
        List<Item> items = materialItems(block, state);
        Fluid fluid = items.isEmpty() ? fluidCostFor(state) : null;
        return new PlacementPlan(target, state, items, fluid, block.blockEntityTag());
    }

    // ──────────────────────────────────────────────────────────────────
    //  材质/流体辅助方法
    // ──────────────────────────────────────────────────────────────────

    /**
     * 返回方块的材料物品列表。
     * 优先使用蓝图记录的材质 ID；若为空则回退到方块的 asItem()。
     */
    public static List<Item> materialItems(RtsBlueprintBlock block, IBlockState state) {
        List<ResourceLocation> ids = RtsBlueprint.materialItemIds(block);
        if (ids.isEmpty() && state != null) {
            Item fallback = Item.getItemFromBlock(state.getBlock());
            return fallback == Items.AIR ? com.rtsbuilding.rtsbuilding.server.task.Java8Collections.listOf() : com.rtsbuilding.rtsbuilding.server.task.Java8Collections.listOf(fallback);
        }
        List<Item> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) out.add(item);
        }
        return out.isEmpty() ? com.rtsbuilding.rtsbuilding.server.task.Java8Collections.listOf() : com.rtsbuilding.rtsbuilding.server.task.Java8Collections.copyList(out);
    }

    /**
     * 返回方块的流体成本——如果方块状态中有水/岩浆则返回对应流体。
     */
    @Nullable
    public static Fluid fluidCostFor(IBlockState state) {
        if (state == null) return null;
        if (state.getMaterial() == Material.WATER) return FluidRegistry.WATER;
        if (state.getMaterial() == Material.LAVA) return FluidRegistry.LAVA;
        return null;
    }
}
