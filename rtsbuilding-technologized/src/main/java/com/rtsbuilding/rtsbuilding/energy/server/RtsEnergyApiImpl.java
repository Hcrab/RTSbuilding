package com.rtsbuilding.rtsbuilding.energy.server;

import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.IContentsListener;
import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import com.rtsbuilding.rtsbuilding.api.energy.RtsEnergyAPI;
import com.rtsbuilding.rtsbuilding.common.energy.BasicEnergyContainer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of {@link RtsEnergyAPI} — delegates to
 * {@link RtsEnergyNetworkManager}, the server-side per-player energy grid.
 */
@ApiStatus.Internal
public final class RtsEnergyApiImpl implements RtsEnergyAPI {

    private static final RtsEnergyNetworkManager MANAGER = RtsEnergyNetworkManager.INSTANCE;

    @Override
    public IEnergyContainer createContainer(long maxEnergy) {
        return BasicEnergyContainer.create(maxEnergy, null);
    }

    @Override
    public IEnergyContainer createContainer(long maxEnergy, @Nullable IContentsListener listener) {
        return BasicEnergyContainer.create(maxEnergy, listener);
    }

    @Override
    public boolean hasGrid(ServerPlayer player) {
        return MANAGER.hasGrid(player);
    }

    @Override
    public long getStoredEnergy(ServerPlayer player) {
        return MANAGER.getStored(player);
    }

    @Override
    public long getMaxEnergy(ServerPlayer player) {
        return MANAGER.getMaxEnergy(player);
    }

    @Override
    public long getNeededEnergy(ServerPlayer player) {
        return MANAGER.getNeeded(player);
    }

    @Override
    public long insertEnergy(ServerPlayer player, long amount, Action action) {
        return MANAGER.insert(player, amount, action);
    }

    @Override
    public long extractEnergy(ServerPlayer player, long amount, Action action) {
        return MANAGER.extract(player, amount, action);
    }
}
