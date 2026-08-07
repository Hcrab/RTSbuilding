package com.rtsbuilding.rtsbuilding.network.craft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsClearCraftingGridPayloadTest {
    @Test
    void roundTripsBothFallbackPriorities() {
        assertTrue(roundTrip(true));
        assertFalse(roundTrip(false));
    }

    private static boolean roundTrip(boolean toPlayerInventory) {
        ByteBuf buffer = Unpooled.buffer();
        new C2SRtsClearCraftingGridPayload(toPlayerInventory).toBytes(buffer);
        C2SRtsClearCraftingGridPayload decoded = new C2SRtsClearCraftingGridPayload();
        decoded.fromBytes(buffer);
        return decoded.toPlayerInventory();
    }
}
