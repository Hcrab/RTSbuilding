package com.rtsbuilding.rtsbuilding.api.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;
import javax.annotation.Nullable;

public interface RtsStorageNetworkProvider {

    String getModId();

    boolean isAvailable();

    @Nullable
    IItemHandler createItemHandler(ServerPlayer player, BlockPos pos);

    void releaseItemHandler(IItemHandler handler);

    boolean isNetworkNode(ServerPlayer player, BlockPos pos);

    @Nullable
    String getNetworkDisplayName(ServerPlayer player);
}
