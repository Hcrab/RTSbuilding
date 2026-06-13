package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.api.RtsSessionQueryAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import net.minecraft.server.level.ServerPlayer;

public final class RtsSessionQueryAPIImpl implements RtsSessionQueryAPI {
    @Override
    public BuilderMode getMode(ServerPlayer player) {
        return RtsSessionService.getMode(player);
    }
}
