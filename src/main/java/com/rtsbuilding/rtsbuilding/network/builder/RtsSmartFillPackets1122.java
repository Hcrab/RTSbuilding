package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsSmartFillHandlers1122;
import net.minecraftforge.fml.relauncher.Side;

/** 智能填坑专用的稳定 1.12.2 协议登记。 */
public final class RtsSmartFillPackets1122 {
    private RtsSmartFillPackets1122() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(196, RtsSmartFillHandlers1122.Confirm.class,
                C2SRtsConfirmSmartFillPayload.class, Side.SERVER);
    }
}
