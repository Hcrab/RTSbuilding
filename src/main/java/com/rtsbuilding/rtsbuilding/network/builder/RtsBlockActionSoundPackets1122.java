package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import net.minecraftforge.fml.relauncher.Side;

/** 注册服务端向操作玩家回放方块音色的客户端消息。 */
public final class RtsBlockActionSoundPackets1122 {
    private RtsBlockActionSoundPackets1122() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(165, ClientPayloadDispatcher.BlockActionSoundHandler.class,
                S2CRtsBlockActionSoundPayload.class, Side.CLIENT);
    }
}
