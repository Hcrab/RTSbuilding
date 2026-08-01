package com.rtsbuilding.rtsbuilding.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/** 客户端 Fabric 收包上下文适配器。 */
final class FabricClientPayloadContext implements RtsPayloadContext {
    private final ClientPlayNetworking.Context context;

    FabricClientPayloadContext(ClientPlayNetworking.Context context) {
        this.context = context;
    }

    @Override
    public LocalPlayer player() {
        return this.context.player();
    }

    @Override
    public void enqueueWork(Runnable work) {
        this.context.client().execute(work);
    }
}
