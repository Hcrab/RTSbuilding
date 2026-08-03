package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsOpenGuiBindingPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteGuiTracePayloadTest {
    private static final long TRACE_ID = 0x1234567890abcdefL;

    @Test
    void interactionCarriesTraceAsFirstWireField() {
        BlockPos target = new BlockPos(68, 61, -128);
        C2SRtsInteractPayload source = new C2SRtsInteractPayload(
                TRACE_ID, C2SRtsInteractPayload.NO_ENTITY, target,
                (byte) EnumFacing.UP.getIndex(), 68.5D, 61.5D, -127.5D,
                C2SRtsInteractPayload.SOURCE_EMPTY_HAND, (byte) 0, "",
                0.0D, 64.0D, 0.0D, 0.0D, -1.0D, 0.0D);
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.toBytes(buffer);
            assertEquals(TRACE_ID, buffer.getLong(0));
            C2SRtsInteractPayload decoded = new C2SRtsInteractPayload();
            decoded.fromBytes(buffer);
            assertEquals(TRACE_ID, decoded.traceId());
            assertEquals(target, decoded.clickedPos());
            assertEquals(C2SRtsInteractPayload.SOURCE_EMPTY_HAND, decoded.sourceType());
        } finally {
            buffer.release();
        }
    }

    @Test
    void hintAndTerminalResultRoundTripSameTrace() {
        BlockPos target = new BlockPos(-300, 70, 455);
        ByteBuf hintBuffer = Unpooled.buffer();
        ByteBuf resultBuffer = Unpooled.buffer();
        try {
            new S2CRtsRemoteMenuHintPayload(TRACE_ID, target).toBytes(hintBuffer);
            S2CRtsRemoteMenuHintPayload hint = new S2CRtsRemoteMenuHintPayload();
            hint.fromBytes(hintBuffer);
            assertEquals(TRACE_ID, hint.traceId());
            assertEquals(target, hint.pos());

            new S2CRtsRemoteMenuResultPayload(
                    TRACE_ID, S2CRtsRemoteMenuResultPayload.MENU_OPENED,
                    S2CRtsRemoteMenuResultPayload.REASON_NONE, 17).toBytes(resultBuffer);
            S2CRtsRemoteMenuResultPayload result = new S2CRtsRemoteMenuResultPayload();
            result.fromBytes(resultBuffer);
            assertEquals(TRACE_ID, result.traceId());
            assertEquals(S2CRtsRemoteMenuResultPayload.MENU_OPENED, result.outcome());
            assertEquals(17, result.windowId());
        } finally {
            hintBuffer.release();
            resultBuffer.release();
        }
    }

    @Test
    void guiBindingOpenCarriesTheSameTraceIdentity() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new C2SRtsOpenGuiBindingPayload(TRACE_ID, (byte) 3).toBytes(buffer);
            assertEquals(TRACE_ID, buffer.getLong(0));
            C2SRtsOpenGuiBindingPayload decoded = new C2SRtsOpenGuiBindingPayload();
            decoded.fromBytes(buffer);
            assertEquals(TRACE_ID, decoded.traceId());
            assertEquals(3, decoded.slot());
        } finally {
            buffer.release();
        }
    }
}
