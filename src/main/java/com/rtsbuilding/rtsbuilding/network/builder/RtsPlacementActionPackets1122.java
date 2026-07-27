package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsPlacementActionHandlers1122;
import net.minecraftforge.fml.relauncher.Side;

/** 剩余放置/交互 C2S 的固定 discriminator 表。 */
public final class RtsPlacementActionPackets1122 {
    private RtsPlacementActionPackets1122() {
    }

    public static void register() {
        RtsPayloadRegistrar.registerMessage(100, RtsPlacementActionHandlers1122.Place.class,
                C2SRtsPlacePayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(101, RtsPlacementActionHandlers1122.PlaceBatch.class,
                C2SRtsPlaceBatchPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(102, RtsPlacementActionHandlers1122.PlaceFluid.class,
                C2SRtsPlaceFluidPayload.class, Side.SERVER);
        RtsPayloadRegistrar.registerMessage(164, RtsPlacementActionHandlers1122.Interact.class,
                C2SRtsInteractPayload.class, Side.SERVER);
    }
}
