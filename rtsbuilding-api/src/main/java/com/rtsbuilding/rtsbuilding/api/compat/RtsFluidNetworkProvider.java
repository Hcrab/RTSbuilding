package com.rtsbuilding.rtsbuilding.api.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import javax.annotation.Nullable;
import java.util.List;

public interface RtsFluidNetworkProvider {

    String getModId();

    boolean isAvailable();

    @Nullable
    IFluidHandler createFluidHandler(ServerPlayer player);

    List<FluidStack> collectFluids(ServerPlayer player, @Nullable BlockPos pos, @Nullable BlockEntity blockEntity);
}
