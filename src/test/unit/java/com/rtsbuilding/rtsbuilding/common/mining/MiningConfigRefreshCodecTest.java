package com.rtsbuilding.rtsbuilding.common.mining;

import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningConfigSync;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MiningConfigRefreshCodecTest {
    @Test
    void configSnapshotRoundTripsWithoutSharingMutableBytes() {
        byte[] bytes = "[mining]\nmaxSelectionVolume=110592\nultimineMaxBlocks=1024\n".getBytes(StandardCharsets.UTF_8);
        var payload = new RtsMiningConfigSync.Payload(bytes);
        bytes[0] = 0;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RtsMiningConfigSync.Payload.CODEC.encode(buf, payload);
            var restored = RtsMiningConfigSync.Payload.CODEC.decode(buf);
            assertArrayEquals(payload.contents(), restored.contents());
            byte[] copy = restored.contents();
            copy[0] = 0;
            assertEquals('[', restored.contents()[0]);
        } finally {
            buf.release();
        }
    }

    @Test
    void oversizedAndEmptyConfigDocumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RtsMiningConfigSync.Payload(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new RtsMiningConfigSync.Payload(
                new byte[RtsMiningConfigSync.Payload.MAX_CONFIG_BYTES + 1]));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(RtsMiningConfigSync.Payload.MAX_CONFIG_BYTES + 1);
            assertThrows(RuntimeException.class, () -> RtsMiningConfigSync.Payload.CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }
}
