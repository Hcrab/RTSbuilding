package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsWorkflowControlHandlers;
import cpw.mods.fml.relauncher.Side;

/** 注册不会携带世界坐标的工作流所有权控制消息。 */
public final class RtsWorkflowControlPackets {
    private RtsWorkflowControlPackets() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(32, RtsWorkflowControlHandlers.DeleteHandler.class,
                C2SRtsDeleteWorkflowPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(33, RtsWorkflowControlHandlers.ProtectHandler.class,
                C2SRtsSetWorkflowProtectedPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(34, RtsWorkflowControlHandlers.UndoHandler.class,
                C2SRtsUndoPayload.class, Side.SERVER);
    }
}
