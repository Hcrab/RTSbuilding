package com.rtsbuilding.rtsbuilding.server.service.fluids;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 世界流体适配器。
 *
 * <p>容器填充严格执行“先模拟、再按模拟量执行”；世界放置交给 Forge 的
 * {@link FluidUtil#tryPlaceFluid}，从而保留模组流体的 IFluidBlock、汽化与方块替换规则。
 * 权限检查仍由调用本类前的保护服务负责，本类不绕过该边界。</p>
 */
public final class RtsFluidWorldPlacer {
    private RtsFluidWorldPlacer() {
    }

    public static int fillFluidHandlerAtTarget(WorldServer level, BlockPos clickedPos,
            EnumFacing face, FluidStack fluidStack) {
        if (level == null || clickedPos == null || face == null
                || RtsFluidBufferService.isEmpty(fluidStack)
                || !level.isBlockLoaded(clickedPos)) return 0;

        List<IFluidHandler> candidates = new ArrayList<IFluidHandler>();
        addFluidHandlerCandidate(level, clickedPos, face, candidates);
        addFluidHandlerCandidate(level, clickedPos, null, candidates);
        for (EnumFacing direction : EnumFacing.values()) {
            addFluidHandlerCandidate(level, clickedPos, direction, candidates);
        }

        BlockPos adjacent = clickedPos.offset(face);
        if (level.isBlockLoaded(adjacent)) {
            addFluidHandlerCandidate(level, adjacent, face.getOpposite(), candidates);
            addFluidHandlerCandidate(level, adjacent, null, candidates);
            for (EnumFacing direction : EnumFacing.values()) {
                addFluidHandlerCandidate(level, adjacent, direction, candidates);
            }
        }

        for (IFluidHandler handler : candidates) {
            FluidStack candidate = fluidStack.copy();
            int simulated = handler.fill(candidate, false);
            if (simulated <= 0) continue;
            candidate.amount = Math.min(candidate.amount, simulated);
            return Math.max(0, handler.fill(candidate, true));
        }
        return 0;
    }

    private static void addFluidHandlerCandidate(WorldServer level, BlockPos pos,
            EnumFacing side, List<IFluidHandler> out) {
        IFluidHandler handler = FluidUtil.getFluidHandler(level, pos, side);
        if (handler != null && !out.contains(handler)) out.add(handler);
    }

    public static BlockPos resolveFluidPlacementPos(WorldServer level, EntityPlayerMP player,
            RayTraceResult hit, FluidStack fluidStack) {
        if (level == null || hit == null || hit.getBlockPos() == null || hit.sideHit == null) return null;
        BlockPos clicked = hit.getBlockPos();
        if (canPlaceFluidAt(level, clicked, fluidStack)) return clicked;
        BlockPos adjacent = clicked.offset(hit.sideHit);
        return canPlaceFluidAt(level, adjacent, fluidStack) ? adjacent : null;
    }

    public static boolean placeFluidBlock(WorldServer level, EntityPlayerMP player, BlockPos pos,
            FluidStack fluidStack, RayTraceResult placementHit) {
        if (!canPlaceFluidAt(level, pos, fluidStack) || player == null
                || !level.isBlockModifiable(player, pos)
                || !player.canPlayerEdit(pos, EnumFacing.UP, net.minecraft.item.ItemStack.EMPTY)) return false;
        FluidStack source = fluidStack.copy();
        FluidTank tank = new FluidTank(source, source.amount);
        tank.setCanFill(false);
        return FluidUtil.tryPlaceFluid(player, level, pos, tank, source);
    }

    private static boolean canPlaceFluidAt(WorldServer level, BlockPos pos, FluidStack fluidStack) {
        if (level == null || pos == null || RtsFluidBufferService.isEmpty(fluidStack)
                || !level.isBlockLoaded(pos)) return false;
        Fluid fluid = fluidStack.getFluid();
        if (fluid == null || !fluid.canBePlacedInWorld()) return false;
        IBlockState state = level.getBlockState(pos);
        return level.isAirBlock(pos)
                || state.getBlock().isReplaceable(level, pos)
                || !state.getMaterial().isSolid();
    }
}
