package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.craft.handler.RtsCraftNetworkHandlers;
import net.minecraftforge.fml.relauncher.Side;

/** 合成域稳定 discriminator：72-78。 */
public final class RtsCraftPackets {
    private RtsCraftPackets() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(72, RtsCraftNetworkHandlers.RequestCraftables.class,
                C2SRtsRequestCraftablesPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(73, RtsCraftNetworkHandlers.OpenTerminal.class,
                C2SRtsOpenCraftTerminalPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(74, RtsCraftNetworkHandlers.CraftRefill.class,
                C2SRtsCraftRefillPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(75, RtsCraftNetworkHandlers.CraftRecipe.class,
                C2SRtsCraftRecipePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(76, RtsCraftNetworkHandlers.JeiTransfer.class,
                C2SRtsJeiTransferPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(77, RtsCraftNetworkHandlers.ClientCraftables.class,
                S2CRtsCraftablesPayload.class, Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(78, RtsCraftNetworkHandlers.ClientFeedback.class,
                S2CRtsCraftFeedbackPayload.class, Side.CLIENT);
    }
}
