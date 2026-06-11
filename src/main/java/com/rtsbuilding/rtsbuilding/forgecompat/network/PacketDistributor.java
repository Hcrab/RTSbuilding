package com.rtsbuilding.rtsbuilding.forgecompat.network;


import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    private PacketDistributor() {
    }

    public static void sendToServer(final Object message) {
        RtsPayloadRegistrar.CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(final ServerPlayer player, final Object message) {
        RtsPayloadRegistrar.sendToPlayer(player, message);
    }
}
