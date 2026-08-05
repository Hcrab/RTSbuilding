package com.rtsbuilding.rtsbuilding.network.blueprint;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlueprintNetworkHandlers {
    private BlueprintNetworkHandlers() {}

    public static void send(ServerPlayer player, byte status, String messageKey, String detail) {
        PacketDistributor.sendToPlayer(player, new S2CBlueprintStatusPayload(status, messageKey, detail));
    }
}
