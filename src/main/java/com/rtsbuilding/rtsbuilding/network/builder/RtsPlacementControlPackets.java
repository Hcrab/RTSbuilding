package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsPlacementControlHandlers;
import net.minecraftforge.fml.relauncher.Side;

/** 注册模式选择、方块旋转和挂起放置提交消息。 */
public final class RtsPlacementControlPackets {
    private RtsPlacementControlPackets() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(96, RtsPlacementControlHandlers.SetModeHandler.class,
                C2SRtsSetModePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(97, RtsPlacementControlHandlers.RotateHandler.class,
                C2SRtsRotateBlockPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(98, RtsPlacementControlHandlers.OrientHandler.class,
                C2SRtsOrientBlockPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(99, RtsPlacementControlHandlers.SubmitPendingHandler.class,
                C2SRtsSubmitPendingPayload.class, Side.SERVER);
    }
}
