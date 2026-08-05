package com.rtsbuilding.rtsbuilding.server.service.fluids;

import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.storage.IFluidHandler;
import com.rtsbuilding.rtsbuilding.platform.storage.NativeFluidHandlerAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 世界流体适配器。
 *
 * <p>容器填充严格执行“先模拟、再按模拟量执行”；世界放置交给 Forge 的
 * 旧版原生流体方块，从而保留 Forge 流体注册关系和 GTNH 机器的方向访问规则。
 * 权限检查仍由调用本类前的保护服务负责，本类不绕过该边界。</p>
 */
public final class RtsFluidWorldPlacer {
    private RtsFluidWorldPlacer() {
    }

    public static int fillFluidHandlerAtTarget(WorldServer level, BlockPos clickedPos,
            EnumFacing face, FluidStack fluidStack) {
        if (level == null || clickedPos == null || face == null
                || RtsFluidBufferService.isEmpty(fluidStack)
                || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, clickedPos)) return 0;

        List<IFluidHandler> candidates = new ArrayList<IFluidHandler>();
        addFluidHandlerCandidate(level, clickedPos, face, candidates);
        addFluidHandlerCandidate(level, clickedPos, null, candidates);
        for (EnumFacing direction : EnumFacing.values()) {
            addFluidHandlerCandidate(level, clickedPos, direction, candidates);
        }

        BlockPos adjacent = clickedPos.offset(face);
        if (com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, adjacent)) {
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
        TileEntity tile = level.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (!(tile instanceof net.minecraftforge.fluids.IFluidHandler)) return;
        ForgeDirection nativeSide = side == null ? ForgeDirection.UNKNOWN : side.toForgeDirection();
        IFluidHandler handler = new NativeFluidHandlerAdapter(
                (net.minecraftforge.fluids.IFluidHandler) tile, nativeSide);
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
                || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockModifiable(level, player, pos)
                || !player.canPlayerEdit(pos.getX(), pos.getY(), pos.getZ(),
                        EnumFacing.UP.getIndex(), null)) return false;
        Fluid fluid = fluidStack.getFluid();
        return fluid != null && fluid.getBlock() != null
                && level.setBlock(pos.getX(), pos.getY(), pos.getZ(), fluid.getBlock(), 0, 3);
    }

    private static boolean canPlaceFluidAt(WorldServer level, BlockPos pos, FluidStack fluidStack) {
        if (level == null || pos == null || RtsFluidBufferService.isEmpty(fluidStack)
                || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) return false;
        Fluid fluid = fluidStack.getFluid();
        if (fluid == null || !fluid.canBePlacedInWorld()) return false;
        BlockState state = BlockState.fromWorld(level, pos);
        return level.isAirBlock(pos.getX(), pos.getY(), pos.getZ())
                || state.getBlock().isReplaceable(level, pos.getX(), pos.getY(), pos.getZ())
                || !state.getMaterial().isSolid();
    }
}
