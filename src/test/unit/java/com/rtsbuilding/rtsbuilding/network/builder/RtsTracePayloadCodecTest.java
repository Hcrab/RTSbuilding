package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsTracePayloadCodecTest {
    private static final long TRACE = 0x1234_5678_9abc_def0L;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void c2sTracePayloadsKeepTraceAsFirstFieldAndRoundTrip() {
        assertRoundTrip(C2SRtsMineTracePayload.STREAM_CODEC,
                new C2SRtsMineTracePayload(TRACE, 3, 40L, 125,
                        RtsTraceInputKind.MOUSE.wireId(), RtsMiningStopOrigin.POINTER_RELEASE.wireId(),
                        new BlockPos(1, 2, 3), (byte) 1, true, (byte) 2,
                        "minecraft:iron_pickaxe", ItemStack.EMPTY, true, true,
                        true, 1.25D, 2.5D, 3.75D,
                        9.0D, 8.0D, 7.0D, 0.25D, -0.5D, 0.75D));
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
        assertRoundTrip(C2SRtsConvenienceDestroyTracePayload.STREAM_CODEC,
                new C2SRtsConvenienceDestroyTracePayload(TRACE, 7, 44L, 0,
                        RtsTraceInputKind.MOUSE.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                        99L, RtsConvenienceDestroyMode.TREE_FELL, new BlockPos(13, 14, 15),
                        (byte) 1, new RtsConvenienceDestroySettings(3, 4, 5, 1, 2, 256),
                        (byte) 6, "minecraft:diamond_axe", ItemStack.EMPTY, true));
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
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
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
