package com.rtsbuilding.rtsbuilding.network.create.handler;

import com.rtsbuilding.rtsbuilding.compat.create.RtsCreateValueSettingsServerCompat;
import com.rtsbuilding.rtsbuilding.network.create.C2SRtsCreateValueSettingsPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 将网络线程收到的兼容包交给服务端 Create 行为复核器。 */
public final class RtsCreateValueSettingsNetworkHandler {
    private RtsCreateValueSettingsNetworkHandler() {
    }

    public static void handle(C2SRtsCreateValueSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RtsCreateValueSettingsServerCompat.handle(player, payload);
            }
        });
    }
}
