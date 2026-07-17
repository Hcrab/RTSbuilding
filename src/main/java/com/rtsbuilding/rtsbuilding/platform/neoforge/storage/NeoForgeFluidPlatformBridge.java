package com.rtsbuilding.rtsbuilding.platform.neoforge.storage;

import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidContainerDrain;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidPlatformBridge;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NeoForge 26.1 的流体容器与世界交互适配器。
 *
 * <p>该类拥有 capability 查询、FluidStack、流体类型扩展和汽化回调。
 * 它不拥有 RTS 会话、材料扣除或权限判断，因而可以在未来 Loader port 时整体替换，
 * 而不改动储存与蓝图业务。</p>
 */
public final class NeoForgeFluidPlatformBridge implements RtsFluidPlatformBridge {
    public static final NeoForgeFluidPlatformBridge INSTANCE = new NeoForgeFluidPlatformBridge();

    private NeoForgeFluidPlatformBridge() {
    }

    @Override
    public RtsFluidContainerDrain drainContainer(ItemStack container, int amount, boolean execute) {
        if (container.isEmpty() || amount <= 0) {
            return RtsFluidContainerDrain.EMPTY;
        }
        ItemStack single = container.copyWithCount(1);
        Optional<IFluidHandlerItem> optionalHandler = FluidUtil.getFluidHandler(single);
        if (optionalHandler.isEmpty()) {
            return RtsFluidContainerDrain.EMPTY;
        }

        IFluidHandlerItem handler = optionalHandler.get();
        IFluidHandler.FluidAction action =
                execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
        FluidStack drained = handler.drain(amount, action);
        if (drained.isEmpty()) {
            return RtsFluidContainerDrain.EMPTY;
        }
        return new RtsFluidContainerDrain(
                new RtsFluidVolume(drained.getFluid(), drained.getAmount()),
                handler.getContainer().copy());
    }

    @Override
    public int fillTarget(
            ServerLevel level, BlockPos clickedPos, Direction face, RtsFluidVolume volume) {
        if (volume.isEmpty() || !level.hasChunkAt(clickedPos)) {
            return 0;
        }
        List<IFluidHandler> candidates = new ArrayList<>();
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

        for (IFluidHandler handler : candidates) {
            FluidStack candidate = new FluidStack(volume.fluid(), volume.amount());
            int simulated = handler.fill(candidate, IFluidHandler.FluidAction.SIMULATE);
            if (simulated <= 0) {
                continue;
            }
            candidate.setAmount(simulated);
            return handler.fill(candidate, IFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    @Override
    public BlockPos resolvePlacementPos(
            ServerLevel level, ServerPlayer player, BlockHitResult hit, RtsFluidVolume volume) {
        BlockPos clicked = hit.getBlockPos();
        if (canPlaceFluidAt(level, player, clicked, volume, resolveFluidPlacementHit(hit, clicked))) {
            return clicked;
        }

        BlockPos adjacent = clicked.relative(hit.getDirection());
        if (level.hasChunkAt(adjacent)
                && canPlaceFluidAt(level, player, adjacent, volume, resolveFluidPlacementHit(hit, adjacent))) {
            return adjacent;
        }
        return null;
    }

    @Override
    public boolean placeFluid(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            RtsFluidVolume volume,
            BlockHitResult placementHit) {
        if (!canPlaceFluidAt(level, player, pos, volume, placementHit)) {
            return false;
        }

        Fluid fluid = volume.fluid();
        FluidStack fluidStack = new FluidStack(fluid, volume.amount());
        BlockState state = level.getBlockState(pos);
        if (fluid.getFluidType().isVaporizedOnPlacement(level, pos, fluidStack)) {
            fluid.getFluidType().onVaporize(player, level, pos, fluidStack);
            return true;
        }

        if (state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(player, level, pos, state, fluid)) {
            return liquidContainer.placeLiquid(level, pos, state, fluid.defaultFluidState());
        }

        BlockState placeState = fluid.getFluidType().getBlockForFluidState(
                level,
                pos,
                fluid.getFluidType().getStateForPlacement(level, pos, fluidStack));
        if (placeState.isAir()) {
            return false;
        }

        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                placementHit);
        boolean isDestNonSolid = !state.isSolid();
        boolean isDestReplaceable = state.canBeReplaced(context);
        if ((isDestNonSolid || isDestReplaceable) && !state.liquid()) {
            level.destroyBlock(pos, true);
        }
        return level.setBlock(pos, placeState, 11);
    }

    private static void addFluidHandlerCandidate(
            ServerLevel level, BlockPos pos, Direction side, List<IFluidHandler> out) {
        var transfer = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        IFluidHandler handler = transfer == null ? null : IFluidHandler.of(transfer);
        if (handler != null && !out.contains(handler)) {
            out.add(handler);
        }
    }

    private static boolean canPlaceFluidAt(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            RtsFluidVolume volume,
            BlockHitResult placementHit) {
        if (volume.isEmpty() || !level.hasChunkAt(pos)) {
            return false;
        }
        Fluid fluid = volume.fluid();
        FluidStack fluidStack = new FluidStack(fluid, volume.amount());
        if (!fluid.getFluidType().canBePlacedInLevel(level, pos, fluidStack)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                placementHit == null
                        ? new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
                        : placementHit);
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
