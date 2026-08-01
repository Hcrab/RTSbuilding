package com.rtsbuilding.rtsbuilding.server.service.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.rtsbuilding.rtsbuilding.platform.fluid.RtsFluidStack;
import com.rtsbuilding.rtsbuilding.platform.fluid.RtsFluidHandler;
import com.rtsbuilding.rtsbuilding.platform.fluid.FabricFluidHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界级流体放置器，负责在世界中放置流体方块和填充现有流体处理器。
 *
 * <p>核心功能：
 * <ul>
 *   <li>{@link #fillFluidHandlerAtTarget} — 在点击位置及其周围搜索 {@link RtsFluidHandler} 并尝试填充，
 *   按点击面→无面→六方向→相邻块对面等顺序搜索候选处理器</li>
 *   <li>{@link #resolveFluidPlacementPos} — 解析流体方块的放置位置（点击位置或相邻位置）</li>
 *   <li>{@link #placeFluidBlock} — 在世界中放置流体方块，处理 {@link LiquidBlockContainer}、
 *   汽化、非固体替换等场景</li>
 * </ul>
 *
 * <p><b>职责边界：</b>
 * <ul>
 *   <li>不处理会话内部缓冲区（由 {@link RtsFluidBufferService} 负责）</li>
 *   <li>不处理跨链接网络操作（由 {@link RtsFluidNetworkOperator} 负责）</li>
 * </ul>
 */
public final class RtsFluidWorldPlacer {

    private RtsFluidWorldPlacer() {
    }

    /**
     * 尝试填充点击位置或其周围的现有流体处理器。
     * 返回填充的流体量（以 mb 为单位），如果未找到兼容的处理器则返回 0。
     */
    public static int fillFluidHandlerAtTarget(ServerLevel level, BlockPos clickedPos, Direction face, RtsFluidStack fluidStack) {
        if (fluidStack.isEmpty() || !level.hasChunkAt(clickedPos)) {
            return 0;
        }
        List<RtsFluidHandler> candidates = new ArrayList<>();
        addFluidHandlerCandidate(level, clickedPos, face, candidates);
        addFluidHandlerCandidate(level, clickedPos, null, candidates);
        for (Direction direction : Direction.values()) {
            addFluidHandlerCandidate(level, clickedPos, direction, candidates);
        }

        BlockPos adjacent = clickedPos.relative(face);
        if (level.hasChunkAt(adjacent)) {
            addFluidHandlerCandidate(level, adjacent, face.getOpposite(), candidates);
            addFluidHandlerCandidate(level, adjacent, null, candidates);
            for (Direction direction : Direction.values()) {
                addFluidHandlerCandidate(level, adjacent, direction, candidates);
            }
        }

        for (RtsFluidHandler handler : candidates) {
            RtsFluidStack candidate = fluidStack.copy();
            int simulated = handler.fill(candidate, RtsFluidHandler.FluidAction.SIMULATE);
            if (simulated <= 0) {
                continue;
            }
            candidate.setAmount(simulated);
            return handler.fill(candidate, RtsFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    private static void addFluidHandlerCandidate(ServerLevel level, BlockPos pos, Direction side, List<RtsFluidHandler> out) {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
        if (storage != null && out.stream().noneMatch(candidate ->
                candidate instanceof FabricFluidHandler fabric && fabric.wraps(storage))) {
            out.add(new FabricFluidHandler(storage));
        }
    }

    /**
     * 解析流体方块应该被放置的位置。
     * 如果在点击位置或相邻位置都无法放置，则返回 null。
     */
    public static BlockPos resolveFluidPlacementPos(ServerLevel level, ServerPlayer player, BlockHitResult hit,
            RtsFluidStack fluidStack) {
        BlockPos clicked = hit.getBlockPos();
        if (canPlaceFluidAt(level, player, clicked, fluidStack, resolveFluidPlacementHit(hit, clicked))) {
            return clicked;
        }

        BlockPos adjacent = clicked.relative(hit.getDirection());
        if (level.hasChunkAt(adjacent)
                && canPlaceFluidAt(level, player, adjacent, fluidStack, resolveFluidPlacementHit(hit, adjacent))) {
            return adjacent;
        }
        return null;
    }

    /**
     * 在世界的指定位置放置一个流体方块。
     */
    public static boolean placeFluidBlock(ServerLevel level, ServerPlayer player, BlockPos pos, RtsFluidStack fluidStack,
            BlockHitResult placementHit) {
        if (!canPlaceFluidAt(level, player, pos, fluidStack, placementHit)) {
            return false;
        }

        Fluid fluid = fluidStack.getFluid();
        if (fluid.getBucket() instanceof BucketItem bucket) {
            // 复用原版桶的放置路径，包含 waterlog、下界汽化、声音和第三方 BucketItem 覆盖。
            return bucket.emptyContents(player, level, pos, placementHit);
        }
        BlockState state = level.getBlockState(pos);
        BlockState placeState = fluid.defaultFluidState().createLegacyBlock();
        if (placeState.isAir()) {
            return false;
        }
        if (!state.isAir() && !state.liquid()) {
            level.destroyBlock(pos, true);
        }
        return level.setBlock(pos, placeState, 11);
    }

    private static boolean canPlaceFluidAt(ServerLevel level, ServerPlayer player, BlockPos pos, RtsFluidStack fluidStack,
            BlockHitResult placementHit) {
        if (fluidStack.isEmpty() || !level.hasChunkAt(pos)) {
            return false;
        }
        Fluid fluid = fluidStack.getFluid();
        if (fluid == Fluids.EMPTY || fluid.defaultFluidState().createLegacyBlock().isAir()) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                placementHit == null ? new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false) : placementHit);
        boolean canContain = state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(player, level, pos, state, fluid);
        boolean isDestNonSolid = !state.isSolid();
        boolean isDestReplaceable = state.canBeReplaced(context);
        return level.isEmptyBlock(pos) || isDestNonSolid || isDestReplaceable || canContain;
    }

    private static BlockHitResult resolveFluidPlacementHit(BlockHitResult sourceHit, BlockPos targetPos) {
        if (targetPos == null) {
            return new BlockHitResult(Vec3.atCenterOf(BlockPos.ZERO), Direction.UP, BlockPos.ZERO, false);
        }
        if (sourceHit == null) {
            return new BlockHitResult(Vec3.atCenterOf(targetPos), Direction.UP, targetPos, false);
        }

        BlockPos clicked = sourceHit.getBlockPos();
        Direction face = sourceHit.getDirection();
        if (targetPos.equals(clicked)) {
            return new BlockHitResult(sourceHit.getLocation(), face, targetPos, false);
        }

        if (targetPos.equals(clicked.relative(face))) {
            Direction targetFace = face.getOpposite();
            Vec3 targetLocation = Vec3.atCenterOf(targetPos).add(
                    targetFace.getStepX() * 0.498D,
                    targetFace.getStepY() * 0.498D,
                    targetFace.getStepZ() * 0.498D);
            return new BlockHitResult(targetLocation, targetFace, targetPos, false);
        }

        return new BlockHitResult(Vec3.atCenterOf(targetPos), face, targetPos, false);
    }
}
