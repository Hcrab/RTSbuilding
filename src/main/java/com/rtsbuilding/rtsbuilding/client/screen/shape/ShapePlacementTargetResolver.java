package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.item.ItemStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.RayTraceResult;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 统一形状放置前的 Minecraft 目标位置解析。
 * <p>
 * 本类负责三件紧密相关的事：按原版方块替换规则判断点击方块能否直接替换、
 * 为平面形状应用统一的锚点偏移，以及在严格空位锁定时剔除已占用目标。它不拥有形状会话、
 * 玩家选择、工具来源、网络发送或世界修改；调用方必须显式提供输入、物品和只读世界探针。
 * 这样单方块幽灵、批量预览、成本和最终发送可以共享相同的放置目标语义，而不会在屏幕类中
 * 重新维护半砖合并等上下文敏感规则。
 */
public final class ShapePlacementTargetResolver {
    /**
     * 形状目标映射所需的最小只读世界边界。
     * <p>
     * 测试可提供纯内存实现；生产通过 {@link #minecraftWorld(Minecraft, ItemStack)} 接入
     * Minecraft 世界和 1.12 方块替换规则。
     */
    public interface PlacementWorld {
        boolean available();

        boolean hasChunkAt(BlockPos pos);

        boolean canReplace(BlockPos pos, EnumFacing face);
    }

    /**
     * 把形状几何坐标转换成真实放置坐标，并保持输入顺序去重。
     *
     * @param input           当前形状输入
     * @param clickedTargets  几何生成的点击坐标
     * @param strictEmptyLock READY_CONFIRM 阶段是否禁止覆盖已占用目标
     * @param world           只读世界与原版放置上下文适配
     */
    public static List<BlockPos> resolveTargets(
            ShapeBuildTypes.Input input,
            List<BlockPos> clickedTargets,
            boolean strictEmptyLock,
            PlacementWorld world) {
        if (clickedTargets == null || clickedTargets.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        if (input == null || input.placementFace() == null) {
            return immutableDistinct(clickedTargets);
        }
        if (world == null || !world.available()) {
            return java.util.Collections.emptyList();
        }

        EnumFacing face = input.placementFace();
        boolean uniformPlacement = usesUniformPlanePlacement(input.shape());
        LinkedHashSet<BlockPos> resolved = new LinkedHashSet<>(clickedTargets.size());
        for (BlockPos clickedPos : clickedTargets) {
            if (clickedPos == null) {
                continue;
            }
            BlockPos placePos = uniformPlacement
                    ? resolveUniformTarget(input, clickedPos, world)
                    : resolveClickedTarget(clickedPos, face, world);
            if (placePos == null) {
                continue;
            }
            if (strictEmptyLock
                    && world.hasChunkAt(placePos)
                    && !world.canReplace(placePos, face)) {
                continue;
            }
            resolved.add(placePos);
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(resolved));
    }

    /**
     * 创造覆盖模式直接把形状几何坐标当作最终放置坐标。
     * 这条路径只做空值清理和稳定去重，不再根据已有方块把整幅形状推向相邻面。
     */
    public static List<BlockPos> resolveOverwriteTargets(List<BlockPos> targets) {
        return immutableDistinct(targets == null ? java.util.Collections.emptyList() : targets);
    }

    /**
     * 解析单次点击实际会落到的坐标；世界或点击信息缺失时返回 {@code null}。
     */
    public static BlockPos resolveClickedTarget(
            BlockPos clickedPos,
            EnumFacing face,
            PlacementWorld world) {
        if (clickedPos == null || face == null || world == null || !world.available()) {
            return null;
        }
        if (!world.hasChunkAt(clickedPos)) {
            return clickedPos;
        }
        return world.canReplace(clickedPos, face)
                ? clickedPos
                : clickedPos.offset(face);
    }

    /**
     * 为单方块幽灵解析与原版放置完全相同的目标，并拒绝目标处不可替换的已有方块。
     */
    public static BlockPos resolveSingleGhostTarget(
            Minecraft minecraft,
            RayTraceResult hit,
            ItemStack placementStack) {
        if (minecraft == null
                || minecraft.world == null
                || minecraft.player == null
                || hit == null
                || placementStack == null
                || placementStack.isEmpty()) {
            return null;
        }
        BlockPos clickedPos = hit.getBlockPos();
        EnumFacing face = hit.sideHit;
        if (clickedPos == null || face == null) return null;
        BlockPos placePos = minecraft.world.isBlockLoaded(clickedPos)
                && minecraft.world.getBlockState(clickedPos).getBlock().isReplaceable(minecraft.world, clickedPos)
                ? clickedPos : clickedPos.offset(face);
        if (minecraft.world.isBlockLoaded(placePos)) {
            IBlockState state = minecraft.world.getBlockState(placePos);
            if (!state.getBlock().isReplaceable(minecraft.world, placePos)) {
                return null;
            }
        }
        return placePos;
    }

    /**
     * 创建只读生产世界探针。物品栈只用于构造放置上下文，不会在此处被修改。
     */
    public static PlacementWorld minecraftWorld(Minecraft minecraft, ItemStack placementStack) {
        return new MinecraftPlacementWorld(minecraft, placementStack);
    }

    static boolean usesUniformPlanePlacement(BuildShape shape) {
        if (shape == null) {
            return false;
        }
        return switch (shape) {
            case LINE, SQUARE, WALL, CYLINDER, BALL, BOX -> true;
            default -> false;
        };
    }

    private static BlockPos resolveUniformTarget(
            ShapeBuildTypes.Input input,
            BlockPos clickedPos,
            PlacementWorld world) {
        BlockPos anchor = input.pointA();
        EnumFacing face = input.placementFace();
        if (anchor == null || face == null) {
            return clickedPos;
        }
        BlockPos anchorPlaced = resolveClickedTarget(anchor, face, world);
        if (anchorPlaced == null) {
            return clickedPos;
        }
        return clickedPos.add(
                anchorPlaced.getX() - anchor.getX(),
                anchorPlaced.getY() - anchor.getY(),
                anchorPlaced.getZ() - anchor.getZ());
    }

    private static List<BlockPos> immutableDistinct(List<BlockPos> positions) {
        LinkedHashSet<BlockPos> distinct = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (pos != null) {
                // MutableBlockPos 不能穿过解析边界，否则鼠标下一帧会改写已锁定预览。
                distinct.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(distinct));
    }

    private static final class MinecraftPlacementWorld implements PlacementWorld {
        private final Minecraft minecraft;
        private final ItemStack placementStack;

        private MinecraftPlacementWorld(Minecraft minecraft, ItemStack placementStack) {
            this.minecraft = minecraft;
            this.placementStack = placementStack == null ? ItemStack.EMPTY : placementStack;
        }

        @Override
        public boolean available() {
            return this.minecraft != null && this.minecraft.world != null;
        }

        @Override
        public boolean hasChunkAt(BlockPos pos) {
            return available() && pos != null && this.minecraft.world.isBlockLoaded(pos);
        }

        @Override
        public boolean canReplace(BlockPos pos, EnumFacing face) {
            if (!hasChunkAt(pos) || face == null) {
                return false;
            }
            IBlockState state = this.minecraft.world.getBlockState(pos);
            return state.getBlock().isReplaceable(this.minecraft.world, pos);
        }
    }

    private ShapePlacementTargetResolver() {
    }
}
