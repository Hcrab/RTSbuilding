package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.api.RtsSessionQueryAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@link RtsSessionQueryAPI} 的实现——委托给会话查询服务层。
 */
public final class RtsSessionQueryAPIImpl implements RtsSessionQueryAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public BuilderMode getMode(ServerPlayer player) {
        return REGISTRY.session().getMode(player);
    }
}
