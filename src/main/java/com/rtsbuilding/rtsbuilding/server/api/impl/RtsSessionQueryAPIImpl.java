package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsSessionQueryAPI;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * {@link RtsSessionQueryAPI} 的实现——委托给会话查询服务层。
 */
public final class RtsSessionQueryAPIImpl implements RtsSessionQueryAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public BuilderMode getMode(EntityPlayerMP player) {
        return REGISTRY.session().getMode(player);
    }
}
