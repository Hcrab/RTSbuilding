package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsSessionQueryAPI;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Implementation of {@link RtsSessionQueryAPI} — delegates to the session query service layer.
 */
@ApiStatus.Internal
public final class RtsSessionQueryAPIImpl implements RtsSessionQueryAPI {

    private static final RtsServer REGISTRY = RtsServer.get();

    @Override
    public BuilderMode getMode(ServerPlayer player) {
        return REGISTRY.session().getMode(player);
    }
}
