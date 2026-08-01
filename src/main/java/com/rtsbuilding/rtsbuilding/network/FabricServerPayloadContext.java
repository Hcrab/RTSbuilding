package com.rtsbuilding.rtsbuilding.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** 服务端 Fabric 收包上下文适配器。 */
final class FabricServerPayloadContext implements RtsPayloadContext {
    private final ServerPlayNetworking.Context context;

    FabricServerPayloadContext(ServerPlayNetworking.Context context) {
        this.context = context;
    }

    @Override
    public ServerPlayer player() {
        return this.context.player();
    }

    @Override
    public void enqueueWork(Runnable work) {
        this.context.server().execute(work);
    }
}
