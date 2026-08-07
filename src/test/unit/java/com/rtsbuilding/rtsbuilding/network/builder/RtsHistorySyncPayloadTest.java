package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsHistorySyncPayloadTest {
    @Test
    void roundTripsUndoAndRedoCounts() {
        S2CRtsHistorySyncPayload encoded = new S2CRtsHistorySyncPayload(17, 4);
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);

        S2CRtsHistorySyncPayload decoded = new S2CRtsHistorySyncPayload();
        decoded.fromBytes(buffer);
        assertEquals(17, decoded.undoSize());
        assertEquals(4, decoded.redoSize());
    }
}
