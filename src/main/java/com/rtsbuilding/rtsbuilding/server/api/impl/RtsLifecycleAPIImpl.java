package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsLifecycleAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class RtsLifecycleAPIImpl implements RtsLifecycleAPI {
    @Override
    public void onRtsEnabled(ServerPlayer player) {
        RtsSessionService.onRtsEnabled(player);
    }

    @Override
    public void onRtsDisabled(ServerPlayer player) {
        RtsSessionService.onRtsDisabled(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        RtsSessionService.onPlayerLogout(player);
    }

    @Override
    public void onPlayerTickPre(ServerPlayer player) {
        RtsSessionService.onPlayerTickPre(player);
    }

    @Override
    public void onPlayerTickPost(ServerPlayer player) {
        RtsSessionService.onPlayerTickPost(player);
    }

    @Override
    public void tick(MinecraftServer server) {
        RtsSessionService.tickMining(server);
    }

    @Override
    public void warmCreativeTabCaches(MinecraftServer server) {
        RtsSessionService.warmCreativeTabCaches(server);
    }
}
