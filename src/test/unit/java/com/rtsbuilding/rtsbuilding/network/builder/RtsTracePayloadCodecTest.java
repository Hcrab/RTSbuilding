package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.test.MinecraftTestBootstrapExtension;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftTestBootstrapExtension.class)
class RtsTracePayloadCodecTest {
    private static final long TRACE = 0x1234_5678_9abc_def0L;

    @Test
    void c2sTracePayloadsKeepTraceAsFirstFieldAndRoundTrip() {
        assertRoundTrip(C2SRtsMineTracePayload.STREAM_CODEC,
                new C2SRtsMineTracePayload(TRACE, 3, 40L, 125,
                        RtsTraceInputKind.MOUSE.wireId(), RtsMiningStopOrigin.POINTER_RELEASE.wireId(),
                        new BlockPos(1, 2, 3), (byte) 1, true, (byte) 2,
                        "minecraft:iron_pickaxe", ItemStack.EMPTY, true, true));
        assertRoundTrip(C2SRtsUltimineTracePayload.STREAM_CODEC,
                new C2SRtsUltimineTracePayload(TRACE, 4, 41L, 126,
                        RtsTraceInputKind.KEYBOARD.wireId(), RtsMiningStopOrigin.KEY_RELEASE.wireId(),
                        new BlockPos(4, 5, 6), (byte) 2, (byte) 3,
                        "minecraft:diamond_pickaxe", ItemStack.EMPTY, (short) 64, (byte) 1, true));
        assertRoundTrip(C2SRtsAreaMineTracePayload.STREAM_CODEC,
                new C2SRtsAreaMineTracePayload(TRACE, 5, 42L, 127,
                        RtsTraceInputKind.MOUSE.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                        1, 4, 2, 5, 3, 6, (byte) 4,
                        "minecraft:netherite_pickaxe", ItemStack.EMPTY, (byte) 2, (byte) 1, true));
        assertRoundTrip(C2SRtsAreaDestroyTracePayload.STREAM_CODEC,
                new C2SRtsAreaDestroyTracePayload(TRACE, 6, 43L, 128,
                        RtsTraceInputKind.MOUSE.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                        List.of(new BlockPos(7, 8, 9), new BlockPos(10, 11, 12)), (byte) 5,
                        "minecraft:diamond_pickaxe", ItemStack.EMPTY, false));
    }

    @Test
    void terminalPayloadRoundTripsNegativeUnknownTicks() {
        S2CRtsOperationTerminalPayload payload = new S2CRtsOperationTerminalPayload(
                TRACE, 9, "CANCELLED", "KEY_RELEASE", 17, "task-17",
                12, 1, -1L, false, -1L);
        S2CRtsOperationTerminalPayload decoded = assertRoundTrip(
                S2CRtsOperationTerminalPayload.STREAM_CODEC, payload);

        assertEquals(-1L, decoded.serverTick());
        assertEquals(-1L, decoded.firstSliceWaitTicks());
        assertFalse(decoded.everExecuted());
    }

    private static <T extends com.rtsbuilding.rtsbuilding.network.RtsTracedPayload> T assertRoundTrip(
            StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, payload);
            assertTrue(buffer.readableBytes() >= Long.BYTES);
            assertEquals(TRACE, buffer.getLong(0), "trace 必须是网络包首字段");
            T decoded = codec.decode(buffer);
            assertEquals(payload, decoded);
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
