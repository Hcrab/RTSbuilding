package com.rtsbuilding.rtsbuilding.network.create.handler;

import com.rtsbuilding.rtsbuilding.compat.create.RtsCreateValueSettingsServerCompat;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;
import com.rtsbuilding.rtsbuilding.network.create.C2SRtsCreateValueSettingsPayload;
import net.minecraft.server.level.ServerPlayer;

/** 网络线程只解包和排队；全部目标复核由服务端兼容边界拥有。 */
public final class RtsCreateValueSettingsNetworkHandler {
    private RtsCreateValueSettingsNetworkHandler() {
    }

    public static void handle(C2SRtsCreateValueSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCreateValueSettingsServerCompat.handle(serverPlayer, payload);
            }
        });
    }
}
