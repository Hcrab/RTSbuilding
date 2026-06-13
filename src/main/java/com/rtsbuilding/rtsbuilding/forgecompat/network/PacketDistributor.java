package com.rtsbuilding.rtsbuilding.forgecompat.network;


import com.rtsbuilding.rtsbuilding.network.RtsForgePayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    private PacketDistributor() {
    }

    public static void sendToServer(final Object message) {
        RtsForgePayloadRegistrar.CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(final ServerPlayer player, final Object message) {
        RtsForgePayloadRegistrar.sendToPlayer(player, message);
    }
}
